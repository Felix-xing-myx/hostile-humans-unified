package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Combat-only coordination seam: shared facts and admitted policies, no path or projectile execution. */
public final class HumanCombatDirector {
    private static final class State {
        int nextProjectiles, lastProjectiles = Integer.MIN_VALUE;
        List<Projectile> projectiles = List.of();
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private HumanCombatDirector() { }
    public static void tick(Human human) {
        if (human.level().isClientSide) return;
        var shields = human.humanGunner$shieldDefense();
        shields.meleeReentryCooldownTicks(NpcDualWield.ordinaryDefenseCooldown(human));
        shields.validateDefenseLease();
        if (HumanTargetCoordinator.suspended(human) || !human.isAlive() || human.isRemoved()) {
            clear(human); return;
        }
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        LivingEntity target = human.getTarget();
        if (!HumanPerception.canTarget(human, target) || human.isFleeing
                || GunSupport.isGunItem(human.getMainHandItem())
                || !(SpartanEquipmentCompat.isShield(human.getMainHandItem())
                    || SpartanEquipmentCompat.isShield(human.getOffhandItem())))
            shields.clearAttackObligation();
        // Existing recognized-memory facts are shared; defense cannot grant new offensive knowledge.
        List<LivingEntity> known = HumanPerception.knownDynamicTargets(human).stream()
                .filter(entity -> HumanPerception.canTarget(human, entity)).limit(64).toList();
        List<LivingEntity> near = known.stream().filter(entity -> {
            var offset = entity.position().subtract(human.position());
            return offset.horizontalDistanceSqr() <= 16 && Math.abs(offset.y) <= 3;
        }).toList();
        boolean shield = SpartanEquipmentCompat.isShield(human.getMainHandItem())
                || SpartanEquipmentCompat.isShield(human.getOffhandItem());
        if (shield && !GunSupport.isGunItem(human.getMainHandItem()) && human.shieldCoolDown <= 0
                && human.tickCount >= state.nextProjectiles
                && PerceptionBudget.claim(human, "queries", 1, ":defense_projectiles")) {
            state.projectiles = human.level().getEntitiesOfClass(Projectile.class,
                    human.getBoundingBox().inflate(32,16,32), projectile -> projectile.isAlive()
                            && projectile.getOwner() != human).stream()
                    .sorted(java.util.Comparator.comparingDouble(human::distanceToSqr)).limit(32).toList();
            state.lastProjectiles = human.tickCount;
            state.nextProjectiles = human.tickCount + 3;
        }
        List<Projectile> projectiles = state.lastProjectiles != Integer.MIN_VALUE
                && human.tickCount - state.lastProjectiles <= 6 ? state.projectiles : List.of();
        shields.observeDefenseSnapshot(near, known, projectiles);
        var heldShield = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? human.getOffhandItem() : human.getMainHandItem();
        var capability = EquipmentArchetypes.describe(human, heldShield);
        boolean advance = shield && capability.core() && capability.enabled()
                && HumanFactions.shortId(human).equals("azure") && human.onGround()
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                && HumanPerception.canTarget(human,target)
                && human.distanceToSqr(target) > MeleeCombatRange.reachSqr(human,target)
                && !SoldierOrder.isHoldingPosition(human) && !human.isFleeing;
        shields.tickAdvance(advance, 1);
    }
    public static void afterTick(Human human) {
        if (human.level().isClientSide) return;
        var shield = human.humanGunner$shieldDefense();
        MovementSpeedController.limit(human, MovementSpeedController.Limit.DEFENSE,
                shield.defenseSpeedFactor(), shield.defenseOwnsFacing());
        if (shield.defenseOwnsFacing()) RangedStrafeSpeed.clear(human);
    }
    public static void clear(Human human) {
        STATES.remove(human);
        human.humanGunner$shieldDefense().cancelDefenseSession();
        MovementSpeedController.clearLimits(human);
    }
}
