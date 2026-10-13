package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.HitResult;
import java.util.*;

/** Opportunistic inventory collection. Does not touch navigation, targets, hands or item use. */
final class CombatResupply {
    private static final int MAX_CANDIDATES = 16;
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        boolean arrows, magazines;
        final Map<UUID, Long> failed = new LinkedHashMap<>();
    }
    record Need(int arrows, int magazines, int recovery) {
        boolean empty() { return arrows <= 0 && magazines <= 0 && recovery <= 0; }
        int amount(Human human, ItemStack stack) {
            if (HumanAmmo.isArrow(stack)) return arrows;
            if (HumanAmmo.isMagazine(stack)) return magazines;
            return recovery > 0 && RecoverySupplies.isHealingSupplyForResupply(human, stack) ? recovery : 0;
        }
    }
    private CombatResupply() {}

    static void tick(Human human) {
        var config = UnifiedConfig.get().combatResupply();
        if (!SoldierPickupPolicy.canCombatResupplyNow(human) || !config.enabled()) {
            STATES.remove(human);
            return;
        }
        if (Math.floorMod(human.tickCount + human.getId(), config.intervalTicks()) != 0) return;
        var state = STATES.computeIfAbsent(human, ignored -> new State());
        long now = human.level().getGameTime();
        state.failed.values().removeIf(until -> until <= now);
        Need need = needs(human, state, config);
        if (need.empty()) return;
        List<ItemEntity> items = new ArrayList<>();
        human.level().getEntities(EntityTypeTest.forClass(ItemEntity.class),
                human.getBoundingBox().inflate(config.radius(), 1, config.radius()),
                item -> item.isAlive() && !item.hasPickUpDelay() && !item.getItem().isEmpty()
                        && !state.failed.containsKey(item.getUUID()) && need.amount(human, item.getItem()) > 0,
                items, MAX_CANDIDATES);
        final boolean urgentHealing = human.getHealth() < human.getMaxHealth() * .3F && need.recovery() > 0;
        items.sort(Comparator.comparingInt((ItemEntity item) -> urgentHealing
                && RecoverySupplies.isHealingSupplyForResupply(human, item.getItem()) ? 0 : 1)
                .thenComparingDouble(item -> human.distanceToSqr(item)));
        // At most one successful transaction per interval. Recompute after unrelated inventory changes.
        for (ItemEntity item : items) {
            int amount = need.amount(human, item.getItem());
            if (amount > 0 && reachable(human, item, config.radius())
                    && HumanLootManager.collectCombatSupply(human, item, amount)) {
                // End the cycle immediately at its target, before the next second of shooting consumes it.
                needs(human, state, config);
                return;
            }
            if (state.failed.size() >= 32) state.failed.remove(state.failed.keySet().iterator().next());
            state.failed.put(item.getUUID(), now + 40);
        }
    }

    private static Need needs(Human human, State state, CombatResupplyConfig config) {
        var data = human.getData();
        if (data == null) return new Need(0, 0, 0);
        boolean bow = false, recovery = false;
        int gunCost = 0;
        List<ItemStack> owned = new ArrayList<>(data.getInventoryItemsSize() + 2);
        owned.add(human.getMainHandItem());
        owned.add(human.getOffhandItem());
        for (int i = 0; i < data.getInventoryItemsSize(); i++) owned.add(data.getInventoryItem(i));
        for (ItemStack stack : owned) {
            if (stack.isEmpty()) continue;
            bow |= RangedWeaponCustody.isBowOrCrossbow(stack);
            if (GunSupport.get().isGun(stack) && HumanGunAcceptance.accepts(stack))
                gunCost = Math.max(gunCost, Math.max(1, HumanAmmo.magazineCost(stack)));
            recovery |= RecoverySupplies.isHealingSupplyForResupply(human, stack);
        }
        int arrows = HumanAmmo.arrowCount(human), magazines = HumanAmmo.magazineCount(human);
        boolean consumes = UnifiedConfig.get().ammoConsumptionEnabled();
        state.arrows = CombatResupplyConfig.ammunitionNeeded(state.arrows, bow, consumes,
                arrows, config.arrowLow(), config.arrowTarget());
        boolean gun = gunCost > 0;
        int target = gun ? gunCost * config.reloads() : 0;
        state.magazines = CombatResupplyConfig.ammunitionNeeded(state.magazines, gun, consumes,
                magazines, gun ? gunCost - 1 : 0, target);
        boolean healing = RecoverySupplies.hasActiveUse(human) || RecoverySupplies.hasPendingGradualHealing(human)
                || human.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
        return new Need(state.arrows ? Math.max(0, config.arrowTarget() - arrows) : 0,
                state.magazines ? Math.max(0, target - magazines) : 0,
                !recovery && !healing ? config.recoveryItems() : 0);
    }

    static boolean reachable(Human human, ItemEntity item, double radius) {
        if (human.level() != item.level() || !item.isAlive() || item.hasPickUpDelay()
                || human.distanceToSqr(item) > radius * radius || Math.abs(human.getY() - item.getY()) > 1
                || !SoldierOrder.allowsLootPosition(human, item.getX(), item.getZ())
                || !HumanLootManager.isReachableWithoutDiving(item)) return false;
        return human.level().clip(new ClipContext(human.getEyePosition(), item.getBoundingBox().getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType() == HitResult.Type.MISS;
    }
}
