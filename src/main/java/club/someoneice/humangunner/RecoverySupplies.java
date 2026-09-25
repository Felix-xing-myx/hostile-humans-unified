package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finite, inventory-backed recovery supplies. Food healing is derived from
 * nutrition and saturation and then delivered over time; no health is granted
 * unless a real consumable completes its use animation.
 */
public final class RecoverySupplies {
    static final int MEALS_PER_BATCH = 5;
    private static final String KIT_GRANTED = "humangunner:recovery_kit_granted_v2";
    private static final String PENDING_HEAL = "humangunner:pending_food_heal";
    private static final String NEXT_HEAL_TICK = "humangunner:next_food_heal_tick";
    private static final String SHIELD_REBLOCK_UNTIL = "humangunner:shield_reblock_until";
    private static final String TIER_THREE_MARKER = "humangunner:tier_three";
    // Removed fixed-slot lease metadata. Retain the key only to clean old
    // entities when they are first reconciled by the inventory custody logic.
    private static final String RECOVERY_LEASE = "humangunner:recovery_offhand_lease_v3";

    /**
     * One authoritative owner for a temporary hand/inventory exchange. Goal
     * instances may be stopped or replaced on adjacent ticks; keeping the
     * active session here makes completion idempotent and prevents two goals
     * from settling the same off-hand stack twice.
     */
    private static final Map<Human, UseSession> ACTIVE_USES = new WeakHashMap<>();
    /**
     * Item completion can publish its final hand remainder after an AI goal has
     * already observed {@code isUsingItem() == false}. Restoring the displaced
     * shield in that callback lets the late remainder overwrite the shield.
     * Keep custody until the recovery hand has stayed unchanged for several
     * complete server ticks, then perform the one and only swap-back.
     */
    private static final Map<Human, PendingFinish> PENDING_FINISHES = new WeakHashMap<>();
    private static final Map<Human, ShieldSnapshot> SHIELD_AUDIT = new WeakHashMap<>();

    private static final int FINISH_QUIET_TICKS = 3;
    private static final int INTERRUPTED_FINISH_QUIET_TICKS = 1;
    private static final double FOOD_USE_TIME_MULTIPLIER = 0.70D;

    private static final List<String> FOOD_POOL = List.of(
            "minecraft:cooked_beef", "minecraft:cooked_porkchop", "minecraft:cooked_chicken",
            "minecraft:cooked_salmon", "minecraft:bread", "minecraft:baked_potato",
            "farmersdelight:hamburger", "farmersdelight:bacon_and_eggs",
            "farmersdelight:chicken_sandwich", "farmersdelight:bacon_sandwich",
            "farmersdelight:egg_sandwich", "farmersdelight:dumplings",
            "farmersdelight:stuffed_potato", "farmersdelight:barbecue_stick",
            "farmersdelight:roast_chicken", "farmersdelight:honey_glazed_ham",
            "farmersdelight:steak_and_potatoes", "farmersdelight:pasta_with_meatballs",
            "farmersdelight:grilled_salmon"
    );

    private record IntRange(int minimum, int maximum) {
        int roll(RandomSource random) {
            return random.nextInt(minimum, maximum + 1);
        }
    }

    private record SupplyPreset(int weight, IntRange potions, IntRange food, IntRange goldenApples) {
    }

    private record ShieldSnapshot(int count, String state) {
    }

    private record PendingFinish(
            UseSession session,
            boolean completed,
            int requestedTick,
            ItemStack observedHand,
            int stableTicks
    ) {
    }

    public record UseSession(
            InteractionHand hand,
            ItemStack itemAtStart,
            int expectedCompletionTick,
            double startingHealthRatio,
            boolean food,
            boolean healingPotion,
            int exchangeSlot,
            ItemStack displacedHandItem,
            int shieldCountAtStart
    ) {
    }

    private RecoverySupplies() {
    }

    public static void provisionInitialKit(Human human, CombatAiConfig config) {
        if (!config.itemRecoveryEnabled() || human.getPersistentData().getBoolean(KIT_GRANTED)) {
            return;
        }
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        human.getPersistentData().putBoolean(KIT_GRANTED, true);
        SupplyPreset preset = rollPreset(human);
        RandomSource random = human.getRandom();

        int potionCount = preset.potions().roll(random);
        int insertedPotions = 0;
        for (int i = 0; i < potionCount; i++) {
            ItemStack potion = PotionUtils.setPotion(
                    new ItemStack(Items.POTION),
                    i % 3 == 2 ? Potions.LONG_REGENERATION : Potions.STRONG_HEALING
            );
            if (!insertInventory(data, potion)) {
                break;
            }
            insertedPotions++;
        }

        int tacticalPotions = provisionTacticalPotions(human, data);

        int appleCount = preset.goldenApples().roll(random);
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, appleCount);
        insertInventory(data, apples);
        int insertedApples = appleCount - apples.getCount();
        int enchantedApples = provisionEnchantedGoldenApple(human);
        int foodCount = preset.food().roll(random);
        int insertedFood = provisionFoodVariety(human, data, foodCount);
        human.getPersistentData().putInt("humangunner:recovery_kit_potions", insertedPotions);
        human.getPersistentData().putInt("humangunner:tactical_kit_potions", tacticalPotions);
        human.getPersistentData().putInt("humangunner:recovery_kit_food", insertedFood);
        human.getPersistentData().putInt("humangunner:recovery_kit_golden_apples", insertedApples);
        human.getPersistentData().putInt("humangunner:recovery_kit_enchanted_apples", enchantedApples);
    }

    public static boolean hasUsableSupply(Human human) {
        return findBestSlot(human, false, true) >= 0;
    }

    public static boolean hasActiveUse(Human human) {
        return ACTIVE_USES.containsKey(human);
    }

    /** Combat retreat recovery excludes ordinary food, which has its own bounded goal. */
    public static boolean hasCombatRecoverySupply(Human human) {
        return findBestSlot(human, false, false) >= 0;
    }

    public static boolean hasOrdinaryFood(Human human) {
        return findBestOrdinaryFoodSlot(human) >= 0;
    }

    public static boolean isRecoverySupply(Human human, ItemStack stack) {
        return isSupportedSupplyPotion(stack)
                || stack.is(Items.GOLDEN_APPLE)
                || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                || isRecoveryFood(human, stack);
    }

    public static boolean hasPendingGradualHealing(Human human) {
        return human.getPersistentData().getFloat(PENDING_HEAL) > 0.0F;
    }

    public static float pendingGradualHealing(Human human) {
        return Math.max(0.0F, human.getPersistentData().getFloat(PENDING_HEAL));
    }

    public static float projectedHealth(Human human) {
        return Math.min(human.getMaxHealth(), human.getHealth() + pendingGradualHealing(human));
    }

    public static UseSession beginUse(Human human) {
        return beginUse(human, false);
    }

    public static UseSession beginCombatRecoveryUse(Human human) {
        if (human.isUsingItem()) {
            return null;
        }
        HumanData data = human.getData();
        return beginInventoryUse(human, data, findBestSlot(human, false, false));
    }

    public static UseSession beginIdleUse(Human human) {
        return beginUse(human, true);
    }

    /** Starts food-only recovery without selecting potions or either golden apple. */
    public static UseSession beginCombatFoodUse(Human human) {
        if (human.isUsingItem()) {
            return null;
        }
        HumanData data = human.getData();
        int slot = findBestOrdinaryFoodSlot(human);
        return beginInventoryUse(human, data, slot);
    }

    public static UseSession beginCombatBuffUse(Human human) {
        if (human.isUsingItem()) {
            return null;
        }
        HumanData data = human.getData();
        return beginInventoryUse(human, data, findUsefulDrinkableBuffSlot(human));
    }

    public static UseSession beginEmergencyEnchantedAppleUse(Human human) {
        if (human.isUsingItem()) {
            return null;
        }
        HumanData data = human.getData();
        return beginInventoryUse(human, data, findItemSlot(data, Items.ENCHANTED_GOLDEN_APPLE));
    }

    public static boolean hasEnchantedGoldenApple(Human human) {
        return findItemSlot(human.getData(), Items.ENCHANTED_GOLDEN_APPLE) >= 0;
    }

    public static boolean hasUsefulDrinkableBuff(Human human) {
        return findUsefulDrinkableBuffSlot(human) >= 0;
    }

    public static int findSplashDebuffSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!stack.is(Items.SPLASH_POTION)) {
                continue;
            }
            boolean useful = PotionUtils.getMobEffects(stack).stream().anyMatch(effect ->
                    effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                            || effect.getEffect() == MobEffects.WEAKNESS);
            if (useful) {
                return i;
            }
        }
        return -1;
    }

    public static ItemStack takeOneFromInventory(Human human, int slot) {
        HumanData data = human.getData();
        if (data == null || slot < 0 || slot >= data.getInventoryItemsSize()) {
            return ItemStack.EMPTY;
        }
        ItemStack stored = data.getInventoryItem(slot).copy();
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = stored.copy();
        result.setCount(1);
        stored.shrink(1);
        data.setInventoryItem(slot, stored);
        return result;
    }

    private static UseSession beginUse(Human human, boolean preferFood) {
        if (human.isUsingItem()) {
            return null;
        }
        HumanData data = human.getData();
        int slot = findBestSlot(human, preferFood, true);
        return beginInventoryUse(human, data, slot);
    }

    private static UseSession beginInventoryUse(Human human, HumanData data, int slot) {
        // Item-use animation can end before the owning goal receives its stop
        // callback. During that small window isUsingItem() is already false,
        // but the old two-location exchange is not settled yet. Starting a
        // second exchange there would overwrite ACTIVE_USES and orphan the
        // first reservation, so the session map is the authoritative lock.
        if (data == null
                || slot < 0
                || slot >= data.getInventoryItemsSize()
                || human.isUsingItem()
                || ACTIVE_USES.containsKey(human)) {
            return null;
        }
        ItemStack supply = data.getInventoryItem(slot).copy();
        if (supply.isEmpty()) {
            return null;
        }

        // Exact two-location exchange. The displaced off-hand goes into the
        // supply's own slot and the entire supply stack goes into the hand.
        // No third slot, capacity search, eviction or drop is involved.
        ItemStack displaced = human.getOffhandItem().copy();
        int shieldCount = countOwnedShields(human);
        // Acquire ownership before either location changes. This also covers
        // synchronous equipment-change hooks fired from setItemSlot().
        UseSession session = createSession(
                human, InteractionHand.OFF_HAND, supply, slot, displaced, shieldCount
        );
        ACTIVE_USES.put(human, session);
        data.setInventoryItem(slot, displaced.copy());
        human.setItemSlot(EquipmentSlot.OFFHAND, supply);
        human.startUsingItem(InteractionHand.OFF_HAND);
        accelerateFoodUse(human, supply);
        if (!human.isUsingItem() || human.getUsedItemHand() != InteractionHand.OFF_HAND) {
            ACTIVE_USES.remove(human, session);
            rollbackExchange(human, data, slot);
            return null;
        }
        HumanGunner.LOGGER.debug(
                "Recovery custody begin npc={} slot={} displaced={} supply={} shields={}",
                human.getUUID(), slot, itemId(displaced), itemId(supply), shieldCount
        );
        return session;
    }

    /**
     * Commits one completed meal and starts the next one without ever
     * restoring the displaced shield between bites. The original exchange slot
     * remains the sole owner of that shield until the whole recovery chain ends.
     */
    public static UseSession continueFoodUse(
            Human human,
            UseSession completedSession,
            double targetHealthRatio
    ) {
        if (completedSession == null
                || ACTIVE_USES.get(human) != completedSession
                || !completed(human, completedSession)
                || !completedSession.food()) {
            return null;
        }
        double projectedAfterMeal = Math.min(
                human.getMaxHealth(),
                projectedHealth(human) + foodHealingValue(human, completedSession.itemAtStart())
        );
        if (!shouldContinueCombatEating(
                projectedAfterMeal / Math.max(1.0F, human.getMaxHealth()), targetHealthRatio, true)) {
            return null;
        }

        HumanData data = human.getData();
        if (data == null) {
            return null;
        }
        ItemStack currentHand = human.getItemInHand(completedSession.hand()).copy();
        int nextSlot = -1;
        ItemStack nextFood = currentHand;
        if (!isOrdinaryFood(human, nextFood)) {
            nextSlot = findBestOrdinaryFoodSlot(human);
            if (nextSlot < 0 || nextSlot == completedSession.exchangeSlot()) {
                return null;
            }
            nextFood = data.getInventoryItem(nextSlot).copy();
        }

        UseSession nextSession = createSession(
                human, completedSession.hand(), nextFood,
                completedSession.exchangeSlot(), completedSession.displacedHandItem(),
                completedSession.shieldCountAtStart()
        );
        if (nextSlot >= 0) {
            data.setInventoryItem(nextSlot, currentHand);
            human.setItemSlot(EquipmentSlot.OFFHAND, nextFood);
        }
        ACTIVE_USES.put(human, nextSession);
        // LivingTick can observe the completed item before this goal ticks and
        // queue the old session for shield restoration. Chaining supersedes
        // that request and must remove it before the next custody audit.
        PendingFinish pending = PENDING_FINISHES.get(human);
        if (pending != null && pending.session() == completedSession) {
            PENDING_FINISHES.remove(human, pending);
        }
        human.startUsingItem(completedSession.hand());
        accelerateFoodUse(human, nextFood);
        if (!isUsingSessionHand(human, nextSession)) {
            ACTIVE_USES.put(human, completedSession);
            if (nextSlot >= 0) {
                human.setItemSlot(EquipmentSlot.OFFHAND, currentHand);
                data.setInventoryItem(nextSlot, nextFood);
            }
            return null;
        }

        queueFoodHealing(human, completedSession.itemAtStart());
        rememberRecoveryItem(human, completedSession.itemAtStart());
        HumanGunner.LOGGER.debug(
                "Recovery custody continued npc={} reservedSlot={} nextFood={} displaced={} shields={}",
                human.getUUID(), completedSession.exchangeSlot(), itemId(nextFood),
                itemId(completedSession.displacedHandItem()), completedSession.shieldCountAtStart()
        );
        return nextSession;
    }

    public static boolean completed(Human human, UseSession session) {
        return !human.isUsingItem() && human.tickCount >= session.expectedCompletionTick() - 1;
    }

    public static boolean shouldCommitToUse(Human human, UseSession session, CombatAiConfig config) {
        if (!human.isUsingItem()) {
            return completed(human, session);
        }
        int remaining = human.getUseItemRemainingTicks();
        double damagePaidRatio = Math.max(0.0D, session.startingHealthRatio() - healthRatio(human));
        boolean nearCompletion = remaining <= config.recoveryCommitTicks();
        boolean healthCanPay = healthRatio(human) > config.criticalHealthRatio() * 0.75D;
        return healthCanPay && (nearCompletion || damagePaidRatio < config.recoveryDamageToleranceRatio());
    }

    /**
     * Living-tick safety net for goals that are interrupted without a final
     * callback. It also owns the ordinary post-recovery shield reconciliation
     * when no exchange is active.
     */
    public static void tickHandCustody(Human human) {
        UseSession session = ACTIVE_USES.get(human);
        if (session == null) {
            PENDING_FINISHES.remove(human);
            restoreShieldAfterRecovery(human);
            return;
        }

        PendingFinish pending = PENDING_FINISHES.get(human);
        if (pending != null && pending.session() == session) {
            tickPendingFinish(human, pending);
            return;
        }

        if (isUsingSessionHand(human, session)
                && human.tickCount <= session.expectedCompletionTick() + 40) {
            return;
        }
        boolean completed = completed(human, session);
        if (isUsingSessionHand(human, session)) {
            HumanGunner.LOGGER.warn(
                    "Forcing overdue recovery custody settlement npc={} slot={} item={}",
                    human.getUUID(), session.exchangeSlot(), itemId(session.itemAtStart())
            );
        }
        finishUse(human, session, completed);
    }

    /** Settles any temporary recovery hand swap before an owner edits slots. */
    public static void prepareForManualInventory(Human human) {
        UseSession session = ACTIVE_USES.remove(human);
        PENDING_FINISHES.remove(human);
        if (session == null) {
            human.getPersistentData().remove(RECOVERY_LEASE);
            return;
        }
        if (human.isUsingItem()) human.stopUsingItem();
        settleExchange(human, session);
        human.getPersistentData().remove(RECOVERY_LEASE);
        HumanGunner.LOGGER.debug(
                "Recovery custody settled for owner inventory edit npc={} slot={}",
                human.getUUID(), session.exchangeSlot()
        );
    }

    /**
     * Records the observable equipment boundary independently from the goal
     * callbacks. This makes a future controlled test distinguish a protected
     * recovery exchange from an unrelated equipment writer or real breakage.
     */
    public static void auditEquipmentChange(
            Human human,
            EquipmentSlot slot,
            ItemStack previous,
            ItemStack current
    ) {
        if (slot != EquipmentSlot.OFFHAND
                || (!SpartanEquipmentCompat.isShield(previous)
                && !SpartanEquipmentCompat.isShield(current))) {
            return;
        }
        UseSession active = ACTIVE_USES.get(human);
        int shieldCount = countOwnedShields(human);
        HumanGunner.LOGGER.debug(
                "Shield offhand transition npc={} from={} to={} activeRecovery={} slot={} ownedShields={}",
                human.getUUID(), itemId(previous), itemId(current), active != null,
                active == null ? -1 : active.exchangeSlot(), shieldCount
        );
        if (SpartanEquipmentCompat.isShield(previous)
                && !SpartanEquipmentCompat.isShield(current)
                && active == null) {
            HumanGunner.LOGGER.warn(
                    "Shield left offhand outside recovery custody npc={} from={} to={} ownedShields={}",
                    human.getUUID(), itemId(previous), itemId(current), shieldCount
            );
        }
    }

    /** Periodic whole-owner audit, including all 30 HumanData slots. */
    public static void auditOwnedShields(Human human) {
        ShieldSnapshot current = shieldSnapshot(human);
        ShieldSnapshot previous = SHIELD_AUDIT.put(human, current);
        if (previous == null || previous.state().equals(current.state())) {
            return;
        }
        HumanGunner.LOGGER.debug(
                "Shield ownership changed npc={} beforeCount={} afterCount={} activeRecovery={} before=[{}] after=[{}]",
                human.getUUID(), previous.count(), current.count(), ACTIVE_USES.containsKey(human),
                previous.state(), current.state()
        );
        if (current.count() < previous.count() && human.isAlive()) {
            HumanGunner.LOGGER.warn(
                    "Shield ownership decreased npc={} beforeCount={} afterCount={} activeRecovery={} before=[{}] after=[{}]",
                    human.getUUID(), previous.count(), current.count(), ACTIVE_USES.containsKey(human),
                    previous.state(), current.state()
            );
        }
    }

    public static void finishUse(Human human, UseSession session, boolean completed) {
        // A goal can stop after the living-tick watchdog has already requested
        // settlement. Never schedule or process the same exchange twice.
        if (session == null || ACTIVE_USES.get(human) != session) {
            return;
        }
        PendingFinish existing = PENDING_FINISHES.get(human);
        if (existing != null && existing.session() == session) {
            if (completed && !existing.completed()) {
                PENDING_FINISHES.put(human, new PendingFinish(
                        session, true, existing.requestedTick(),
                        existing.observedHand(), existing.stableTicks()
                ));
            }
            return;
        }
        if (isUsingSessionHand(human, session)) {
            human.stopUsingItem();
        }
        ItemStack observed = human.getItemInHand(session.hand()).copy();
        PENDING_FINISHES.put(human, new PendingFinish(
                session, completed, human.tickCount, observed, 0
        ));
        HumanGunner.LOGGER.debug(
                "Recovery custody finish queued npc={} slot={} completed={} hand={} shields={}",
                human.getUUID(), session.exchangeSlot(), completed,
                itemId(observed), countOwnedShields(human)
        );
    }

    private static void tickPendingFinish(Human human, PendingFinish pending) {
        UseSession session = pending.session();
        if (ACTIVE_USES.get(human) != session) {
            PENDING_FINISHES.remove(human, pending);
            return;
        }
        if (isUsingSessionHand(human, session)) {
            PendingFinish reset = new PendingFinish(
                    session, pending.completed(), pending.requestedTick(),
                    human.getItemInHand(session.hand()).copy(), 0
            );
            PENDING_FINISHES.put(human, reset);
            return;
        }

        ItemStack current = human.getItemInHand(session.hand()).copy();
        boolean unchanged = sameStack(current, pending.observedHand());
        int stableTicks = unchanged ? pending.stableTicks() + 1 : 0;
        if (!unchanged) {
            HumanGunner.LOGGER.debug(
                    "Recovery custody quiet wait reset npc={} slot={} from={} to={} shields={}",
                    human.getUUID(), session.exchangeSlot(), itemId(pending.observedHand()),
                    itemId(current), countOwnedShields(human)
            );
        }
        PendingFinish updated = new PendingFinish(
                session, pending.completed(), pending.requestedTick(), current, stableTicks
        );
        PENDING_FINISHES.put(human, updated);
        int requiredQuietTicks = pending.completed()
                ? FINISH_QUIET_TICKS : INTERRUPTED_FINISH_QUIET_TICKS;
        if (human.tickCount - pending.requestedTick() < requiredQuietTicks
                || stableTicks < requiredQuietTicks) {
            return;
        }
        finishUseNow(human, updated);
    }

    private static void finishUseNow(Human human, PendingFinish pending) {
        UseSession session = pending.session();
        if (ACTIVE_USES.get(human) != session
                || PENDING_FINISHES.get(human) != pending) {
            return;
        }
        PENDING_FINISHES.remove(human, pending);
        ACTIVE_USES.remove(human, session);
        settleExchange(human, session);
        int shieldCountAfter = countOwnedShields(human);
        if (shieldCountAfter != session.shieldCountAtStart()) {
            HumanGunner.LOGGER.error(
                    "Shield custody invariant violated npc={} slot={} before={} after={} displaced={} hand={}",
                    human.getUUID(), session.exchangeSlot(), session.shieldCountAtStart(),
                    shieldCountAfter, itemId(session.displacedHandItem()),
                    itemId(human.getItemInHand(session.hand()))
            );
        } else {
            HumanGunner.LOGGER.debug(
                    "Recovery custody finish npc={} slot={} completed={} quietTicks={} restored={} shields={}",
                    human.getUUID(), session.exchangeSlot(), pending.completed(), pending.stableTicks(),
                    itemId(human.getItemInHand(session.hand())), shieldCountAfter
            );
        }
        // An interrupted use returns its shield promptly. Completed uses
        // retain the longer quiet window for the final consumed-item remainder.
        requestShieldReblock(human);
        if (!pending.completed()) return;
        if (session.food()) {
            queueFoodHealing(human, session.itemAtStart());
        }
        rememberRecoveryItem(human, session.itemAtStart());
    }

    private static boolean isUsingSessionHand(Human human, UseSession session) {
        return human.isUsingItem() && human.getUsedItemHand() == session.hand();
    }

    /**
     * Reconciles off-hand custody after food, potion, gun and weapon-switch
     * actions. The best shield is selected from the whole inventory by value.
     * The selected slot and off-hand are exchanged directly, so reconciliation
     * never depends on a free backpack slot and cannot evict either stack.
     */
    public static boolean restoreShieldAfterRecovery(Human human) {
        // Old lease metadata is intentionally ignored. Its fixed slot may have
        // been reused by weapon switching; writing it back was the source of
        // shield downgrades and deletions in previous builds.
        human.getPersistentData().remove(RECOVERY_LEASE);
        UseSession active = ACTIVE_USES.get(human);
        if (active != null) {
            return SpartanEquipmentCompat.isShield(active.displacedHandItem())
                    || SpartanEquipmentCompat.isShield(human.getMainHandItem());
        }
        if (human.isUsingItem() && human.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return SpartanEquipmentCompat.isShield(human.getOffhandItem())
                    || SpartanEquipmentCompat.isShield(human.getMainHandItem());
        }
        ItemStack offhand = human.getOffhandItem();
        HumanData data = human.getData();
        if (data == null) {
            return SpartanEquipmentCompat.isShield(human.getMainHandItem());
        }
        // A valid equipped shield owns the off-hand until it actually breaks
        // or an explicit pickup upgrade replaces it. Do not rotate to a
        // slightly less-damaged backpack shield every reconciliation tick;
        // that made a safe swap look like shield consumption and spread wear
        // across every stored shield during one fight.
        if (SpartanEquipmentCompat.isShield(offhand)) {
            return true;
        }
        int bestShieldSlot = findBestStoredShieldSlot(data);
        if (bestShieldSlot < 0) {
            return SpartanEquipmentCompat.isShield(human.getMainHandItem());
        }

        // Atomic two-location exchange. In particular, do not clear the hand
        // and then call a generic insert routine: that briefly loses custody
        // of the displaced item and lets other hand writers observe an empty
        // slot or make the result depend on backpack capacity.
        ItemStack bestShield = data.getInventoryItem(bestShieldSlot).copy();
        data.setInventoryItem(bestShieldSlot, offhand.copy());
        human.setItemSlot(EquipmentSlot.OFFHAND, bestShield);
        requestShieldReblock(human);
        return true;
    }

    private static void settleExchange(Human human, UseSession session) {
        HumanData data = human.getData();
        int slot = session.exchangeSlot();
        EquipmentSlot handSlot = session.hand() == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        if (data != null && slot >= 0 && slot < data.getInventoryItemsSize()) {
            ItemStack reserved = data.getInventoryItem(slot).copy();
            if (sameStack(reserved, session.displacedHandItem())) {
                ItemStack usedRemainder = human.getItemInHand(session.hand()).copy();
                // Paired swap back into the exact same two locations. The
                // shield never competes for capacity and the same shield,
                // including its durability/NBT, is returned.
                data.setInventoryItem(slot, usedRemainder);
                human.setItemSlot(handSlot, reserved);
                if (SpartanEquipmentCompat.isShield(reserved)) {
                    requestShieldReblock(human);
                } else {
                    restoreShieldAfterRecovery(human);
                }
                return;
            }
        }

        // A third-party or legacy writer touched the reserved slot. Preserve
        // the current use remainder first, then recover the exact displaced
        // stack from anywhere in the backpack. If it is truly absent, restore
        // the session snapshot instead of silently losing equipment.
        HumanGunner.LOGGER.error(
                "Recovery custody reservation changed npc={} slot={} expected={}",
                human.getUUID(), slot, itemId(session.displacedHandItem())
        );
        stowCurrentHand(human, session.hand());
        if (session.displacedHandItem().isEmpty()) {
            restoreShieldAfterRecovery(human);
            return;
        }
        ItemStack recovered = takeMatchingOwnedStack(
                human, session.displacedHandItem(), session.hand()
        );
        if (recovered.isEmpty()) {
            recovered = session.displacedHandItem().copy();
            HumanGunner.LOGGER.error(
                    "Reconstructed missing displaced hand item npc={} item={}",
                    human.getUUID(), itemId(recovered)
            );
        }
        human.setItemSlot(handSlot, recovered);
        if (SpartanEquipmentCompat.isShield(recovered)) {
            requestShieldReblock(human);
        }
    }

    private static void rollbackExchange(Human human, HumanData data, int slot) {
        ItemStack returnedSupply = human.getOffhandItem().copy();
        ItemStack displaced = data.getInventoryItem(slot).copy();
        data.setInventoryItem(slot, returnedSupply);
        human.setItemSlot(EquipmentSlot.OFFHAND, displaced);
    }

    private static void stowCurrentHand(Human human, InteractionHand hand) {
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack current = human.getItemInHand(hand).copy();
        if (current.isEmpty()) {
            return;
        }
        human.setItemSlot(slot, ItemStack.EMPTY);
        if (!stowWithEviction(human, current)) {
            human.setItemSlot(slot, current);
        }
    }

    private static ItemStack takeMatchingOwnedStack(
            Human human,
            ItemStack expected,
            InteractionHand destinationHand
    ) {
        InteractionHand otherHand = destinationHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherHeld = human.getItemInHand(otherHand);
        if (sameStack(otherHeld, expected)) {
            ItemStack result = otherHeld.copy();
            human.setItemSlot(
                    otherHand == InteractionHand.MAIN_HAND
                            ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND,
                    ItemStack.EMPTY
            );
            return result;
        }
        HumanData data = human.getData();
        if (data == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (!sameStack(stored, expected)) {
                continue;
            }
            ItemStack result = stored.copy();
            data.setInventoryItem(i, ItemStack.EMPTY);
            return result;
        }
        return ItemStack.EMPTY;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.getCount() == second.getCount()
                && ItemStack.isSameItemSameTags(first, second);
    }

    private static int countOwnedShields(Human human) {
        int count = 0;
        if (SpartanEquipmentCompat.isShield(human.getMainHandItem())) {
            count += human.getMainHandItem().getCount();
        }
        if (SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
            count += human.getOffhandItem().getCount();
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (SpartanEquipmentCompat.isShield(stored)) {
                    count += stored.getCount();
                }
            }
        }
        return count;
    }

    private static ShieldSnapshot shieldSnapshot(Human human) {
        int count = 0;
        StringBuilder state = new StringBuilder();
        if (SpartanEquipmentCompat.isShield(human.getMainHandItem())) {
            count += human.getMainHandItem().getCount();
            appendShieldState(state, "main", human.getMainHandItem());
        }
        if (SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
            count += human.getOffhandItem().getCount();
            appendShieldState(state, "off", human.getOffhandItem());
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (!SpartanEquipmentCompat.isShield(stored)) {
                    continue;
                }
                count += stored.getCount();
                appendShieldState(state, "inv" + i, stored);
            }
        }
        return new ShieldSnapshot(count, state.toString());
    }

    private static void appendShieldState(StringBuilder state, String location, ItemStack stack) {
        if (!state.isEmpty()) {
            state.append(',');
        }
        // Exclude durability/NBT here so ordinary blocking damage does not
        // emit an ownership-change line every audit interval. Detailed hand
        // transitions still use itemId(), which includes both.
        state.append(location).append('=')
                .append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .append('x').append(stack.getCount());
    }

    private static String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        StringBuilder fingerprint = new StringBuilder()
                .append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .append('x').append(stack.getCount());
        if (stack.isDamageableItem()) {
            fingerprint.append('@').append(stack.getDamageValue())
                    .append('/').append(stack.getMaxDamage());
        }
        if (stack.hasTag()) {
            fingerprint.append('#').append(Integer.toHexString(stack.getTag().hashCode()));
        }
        return fingerprint.toString();
    }

    private static int findBestStoredShieldSlot(HumanData data) {
        int bestSlot = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (!SpartanEquipmentCompat.isShield(stored)) {
                continue;
            }
            long score = shieldScore(stored);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static long shieldScore(ItemStack shield) {
        if (!SpartanEquipmentCompat.isShield(shield)) {
            return Long.MIN_VALUE;
        }
        return Math.round(SpartanEquipmentCompat.shieldValue(shield) * 1_000.0D);
    }

    /**
     * Stores the complete stack without overwriting an occupied slot. If the
     * backpack is full, the lowest-valued unprotected stack is dropped first.
     * If every stored item is protected, the incoming remainder itself is
     * dropped rather than silently deleted.
     */
    private static boolean stowWithEviction(Human human, ItemStack source) {
        if (source.isEmpty()) {
            return true;
        }
        HumanData data = human.getData();
        if (data == null) {
            return dropRemainder(human, source);
        }
        insertInventory(data, source);
        while (!source.isEmpty()) {
            int evictionSlot = HumanLootManager.lowestEvictableSlot(human);
            if (evictionSlot < 0) {
                break;
            }
            ItemStack evicted = data.getInventoryItem(evictionSlot).copy();
            net.minecraft.world.entity.item.ItemEntity drop = human.spawnAtLocation(evicted);
            if (drop == null) {
                break;
            }
            drop.setPickUpDelay(80);
            data.setInventoryItem(evictionSlot, ItemStack.EMPTY);
            insertInventory(data, source);
        }
        return source.isEmpty() || dropRemainder(human, source);
    }

    private static boolean dropRemainder(Human human, ItemStack source) {
        if (source.isEmpty()) {
            return true;
        }
        net.minecraft.world.entity.item.ItemEntity drop = human.spawnAtLocation(source.copy());
        if (drop == null) {
            return false;
        }
        drop.setPickUpDelay(80);
        source.setCount(0);
        return true;
    }

    private static void requestShieldReblock(Human human) {
        if (human.getTarget() != null
                && (SpartanEquipmentCompat.isShield(human.getOffhandItem())
                || SpartanEquipmentCompat.isShield(human.getMainHandItem()))) {
            human.getPersistentData().putInt(SHIELD_REBLOCK_UNTIL, human.tickCount + 80);
        }
    }

    public static void tickGradualHealing(Human human) {
        float pending = human.getPersistentData().getFloat(PENDING_HEAL);
        if (pending <= 0.0F || !human.isAlive()) {
            return;
        }
        if (human.getHealth() >= human.getMaxHealth()) {
            human.getPersistentData().remove(PENDING_HEAL);
            human.getPersistentData().remove(NEXT_HEAL_TICK);
            return;
        }
        int nextTick = human.getPersistentData().getInt(NEXT_HEAL_TICK);
        if (human.tickCount < nextTick) {
            return;
        }
        float step = Math.min(1.0F, pending);
        human.heal(step);
        pending -= step;
        if (pending <= 0.0F) {
            human.getPersistentData().remove(PENDING_HEAL);
            human.getPersistentData().remove(NEXT_HEAL_TICK);
        } else {
            human.getPersistentData().putFloat(PENDING_HEAL, pending);
            human.getPersistentData().putInt(
                    NEXT_HEAL_TICK,
                    human.tickCount + CombatAiConfig.get().gradualHealIntervalTicks()
            );
        }
    }

    private static void queueFoodHealing(Human human, ItemStack food) {
        float amount = foodHealingValue(human, food);
        if (amount <= 0.0F) {
            return;
        }
        CombatAiConfig config = CombatAiConfig.get();
        float pending = human.getPersistentData().getFloat(PENDING_HEAL);
        human.getPersistentData().putFloat(PENDING_HEAL, pending + amount);
        human.getPersistentData().putInt(
                NEXT_HEAL_TICK,
                human.tickCount + config.gradualHealIntervalTicks()
        );
        human.getPersistentData().putFloat("humangunner:last_food_heal_value", amount);
    }

    private static float foodHealingValue(Human human, ItemStack food) {
        FoodProperties properties = food.getFoodProperties(human);
        if (properties == null) {
            return 0.0F;
        }
        CombatAiConfig config = CombatAiConfig.get();
        float saturationPoints = properties.getNutrition() * properties.getSaturationModifier() * 2.0F;
        float calculated = properties.getNutrition() * 0.55F + saturationPoints * 0.35F;
        return Mth.clamp(
                (float) Math.round(calculated),
                config.foodRecoveryMinHealth(),
                config.foodRecoveryMaxHealth()
        );
    }

    private static void rememberRecoveryItem(Human human, ItemStack item) {
        human.getPersistentData().putString(
                "humangunner:last_recovery_item",
                BuiltInRegistries.ITEM.getKey(item.getItem()).toString()
        );
    }

    private static int provisionFoodVariety(Human human, HumanData data, int totalCount) {
        List<Item> available = new ArrayList<>();
        for (String idString : FOOD_POOL) {
            ResourceLocation id = ResourceLocation.tryParse(idString);
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                ItemStack sample = item.getDefaultInstance();
                if (sample.isEdible() && sample.getMaxStackSize() > 1) {
                    available.add(item);
                }
            });
        }
        if (available.isEmpty()) {
            available.add(Items.COOKED_BEEF);
        }
        Collections.shuffle(available, new java.util.Random(human.getRandom().nextLong()));
        int variety = Math.min(available.size(), human.getRandom().nextInt(4, 8));
        int remaining = totalCount;
        for (int i = 0; i < variety && remaining > 0; i++) {
            Item item = available.get(i);
            int reserveForOthers = Math.max(0, variety - i - 1);
            int upper = Math.max(1, remaining - reserveForOthers);
            int count = i == variety - 1
                    ? remaining
                    : human.getRandom().nextInt(1, Math.min(item.getMaxStackSize(), upper) + 1);
            ItemStack stack = new ItemStack(item, count);
            int before = stack.getCount();
            insertInventory(data, stack);
            remaining -= before - stack.getCount();
        }
        int cursor = 0;
        while (remaining > 0 && cursor < available.size() * 4) {
            Item item = available.get(cursor % variety);
            ItemStack stack = new ItemStack(item, Math.min(remaining, item.getMaxStackSize()));
            int before = stack.getCount();
            if (!insertInventory(data, stack) && stack.getCount() == before) {
                break;
            }
            remaining -= before - stack.getCount();
            cursor++;
        }
        return totalCount - remaining;
    }

    private static SupplyPreset rollPreset(Human human) {
        List<SupplyPreset> pool;
        if (human.getPersistentData().getBoolean(TIER_THREE_MARKER)) {
            pool = List.of(
                    preset(25, 6, 7, 96, 112, 3, 4),
                    preset(50, 7, 8, 108, 124, 4, 5),
                    preset(25, 8, 8, 120, 128, 5, 5)
            );
        } else if (human.getTier() == HumanTier.LEVEL2) {
            pool = List.of(
                    preset(30, 4, 5, 64, 80, 2, 3),
                    preset(50, 5, 6, 76, 96, 3, 4),
                    preset(20, 6, 7, 92, 112, 4, 5)
            );
        } else if (human.getTier() == HumanTier.LEVEL1) {
            pool = List.of(
                    preset(35, 2, 3, 32, 48, 1, 2),
                    preset(45, 3, 4, 44, 64, 2, 3),
                    preset(20, 4, 5, 56, 72, 2, 3)
            );
        } else {
            pool = List.of(
                    preset(45, 2, 2, 20, 32, 1, 1),
                    preset(40, 2, 3, 28, 42, 1, 2),
                    preset(15, 3, 4, 36, 48, 2, 2)
            );
        }
        int totalWeight = pool.stream().mapToInt(SupplyPreset::weight).sum();
        int roll = human.getRandom().nextInt(totalWeight);
        for (SupplyPreset preset : pool) {
            roll -= preset.weight();
            if (roll < 0) {
                return preset;
            }
        }
        return pool.get(pool.size() - 1);
    }

    public static boolean consumeShieldReblockRequest(Human human) {
        int until = human.getPersistentData().getInt(SHIELD_REBLOCK_UNTIL);
        if (until < human.tickCount) {
            human.getPersistentData().remove(SHIELD_REBLOCK_UNTIL);
            return false;
        }
        if (until == 0) {
            return false;
        }
        human.getPersistentData().remove(SHIELD_REBLOCK_UNTIL);
        return true;
    }

    private static int provisionTacticalPotions(Human human, HumanData data) {
        int minimum;
        int maximum;
        if (human.getPersistentData().getBoolean(TIER_THREE_MARKER)) {
            minimum = 5;
            maximum = 7;
        } else if (human.getTier() == HumanTier.LEVEL2) {
            minimum = 3;
            maximum = 5;
        } else if (human.getTier() == HumanTier.LEVEL1) {
            minimum = 2;
            maximum = 3;
        } else {
            minimum = 1;
            maximum = 2;
        }
        int requested = human.getRandom().nextInt(minimum, maximum + 1);
        int inserted = 0;
        // Roll Swiftness once per spawned human, independently of the tactical
        // potion count. In particular, a roamer with only one slot still has
        // the advertised 10% chance instead of half that chance.
        float swiftnessChance = human.getPersistentData().getBoolean(TIER_THREE_MARKER)
                ? 0.40F : human.getTier() == HumanTier.LEVEL2
                ? 0.30F : human.getTier() == HumanTier.LEVEL1 ? 0.20F : 0.10F;
        if (!hasSwiftnessPotion(human, data) && human.getRandom().nextFloat() < swiftnessChance) {
            ItemStack swiftness = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.LONG_SWIFTNESS);
            if (insertInventory(data, swiftness)) {
                inserted++;
            }
        }
        int remaining = requested - inserted;
        for (int i = 0; i < remaining; i++) {
            boolean splash = i % 2 == 0;
            ItemStack potion;
            if (splash) {
                potion = PotionUtils.setPotion(
                        new ItemStack(Items.SPLASH_POTION),
                        human.getRandom().nextBoolean() ? Potions.LONG_SLOWNESS : Potions.LONG_WEAKNESS
                );
            } else {
                potion = PotionUtils.setPotion(
                        new ItemStack(Items.POTION),
                        Potions.LONG_STRENGTH
                );
            }
            if (!insertInventory(data, potion)) {
                break;
            }
            inserted++;
        }
        return inserted;
    }

    private static boolean hasSwiftnessPotion(Human human, HumanData data) {
        if (isSwiftnessPotion(human.getMainHandItem()) || isSwiftnessPotion(human.getOffhandItem())) {
            return true;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (isSwiftnessPotion(data.getInventoryItem(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSwiftnessPotion(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) {
            return false;
        }
        var potion = PotionUtils.getPotion(stack);
        return potion == Potions.SWIFTNESS || potion == Potions.LONG_SWIFTNESS
                || potion == Potions.STRONG_SWIFTNESS;
    }

    private static int provisionEnchantedGoldenApple(Human human) {
        float chance;
        if (TierThreeHuman.isTierThree(human)) {
            chance = 0.80F;
        } else if (human.getTier() == HumanTier.LEVEL2) {
            chance = 0.40F;
        } else if (human.getTier() == HumanTier.LEVEL1) {
            chance = 0.30F;
        } else {
            chance = 0.20F;
        }
        if (human.getRandom().nextFloat() >= chance) {
            return 0;
        }
        ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        // An enchanted apple is the rarest supply in the kit. Use the shared
        // lossless/high-value storage path so a successful roll is not silently
        // discarded merely because ordinary potions filled the backpack first.
        return HumanLootManager.storeWithEviction(human, apple) ? 1 : 0;
    }

    private static int findUsefulDrinkableBuffSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!stack.is(Items.POTION)) {
                continue;
            }
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                if (effect.getEffect() == MobEffects.DAMAGE_BOOST
                        && !human.hasEffect(MobEffects.DAMAGE_BOOST)) {
                    return i;
                }
                if (effect.getEffect() == MobEffects.MOVEMENT_SPEED
                        && !human.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findItemSlot(HumanData data, Item item) {
        if (data == null) {
            return -1;
        }
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (data.getInventoryItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private static SupplyPreset preset(
            int weight, int potionMin, int potionMax, int foodMin, int foodMax, int appleMin, int appleMax
    ) {
        return new SupplyPreset(
                weight,
                new IntRange(potionMin, potionMax),
                new IntRange(foodMin, foodMax),
                new IntRange(appleMin, appleMax)
        );
    }

    private static UseSession createSession(
            Human human,
            InteractionHand hand,
            ItemStack stack,
            int exchangeSlot,
            ItemStack displacedHandItem,
            int shieldCountAtStart
    ) {
        int remaining = acceleratedUseDuration(
                Math.max(1, stack.getUseDuration()),
                stack.getUseAnimation() == net.minecraft.world.item.UseAnim.EAT
        );
        return new UseSession(
                hand, stack.copy(),
                human.tickCount + remaining, healthRatio(human),
                isRecoveryFood(human, stack), isHealingPotion(stack),
                exchangeSlot, displacedHandItem.copy(), shieldCountAtStart
        );
    }

    private static void accelerateFoodUse(Human human, ItemStack stack) {
        if (stack.getUseAnimation() != net.minecraft.world.item.UseAnim.EAT) {
            return;
        }
        int original = Math.max(1, stack.getUseDuration());
        int accelerated = acceleratedUseDuration(original, true);
        human.advanceUseTime(original - accelerated);
    }

    static int acceleratedUseDuration(int originalTicks, boolean food) {
        int safeTicks = Math.max(1, originalTicks);
        return food
                ? Math.max(1, (int) Math.ceil(safeTicks * FOOD_USE_TIME_MULTIPLIER))
                : safeTicks;
    }

    static boolean shouldContinueCombatEating(
            double projectedHealthRatio,
            double targetHealthRatio,
            boolean hasFood
    ) {
        return hasFood && projectedHealthRatio < targetHealthRatio;
    }

    private static int findBestSlot(Human human, boolean preferFood, boolean allowFood) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        int bestPotion = -1;
        int bestPotionScore = Integer.MIN_VALUE;
        int goldenApple = -1;
        int enchantedGoldenApple = -1;
        int bestFood = -1;
        int bestFoodScore = Integer.MIN_VALUE;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (isHealingPotion(stack)) {
                int score = healingPotionScore(stack);
                if (score > bestPotionScore) {
                    bestPotion = i;
                    bestPotionScore = score;
                }
            } else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                enchantedGoldenApple = i;
            } else if (stack.is(Items.GOLDEN_APPLE)) {
                goldenApple = i;
            } else if (isRecoveryFood(human, stack)) {
                FoodProperties food = stack.getFoodProperties(human);
                int score = Math.round(food.getNutrition() * (1.0F + food.getSaturationModifier()));
                if (score > bestFoodScore) {
                    bestFood = i;
                    bestFoodScore = score;
                }
            }
        }
        if (enchantedGoldenApple >= 0 && healthRatio(human) <= 0.35D) {
            return enchantedGoldenApple;
        }
        if (allowFood && preferFood && bestFood >= 0) {
            return bestFood;
        }
        if (allowFood && preferFood && goldenApple >= 0) {
            return goldenApple;
        }
        if (bestPotion >= 0) {
            return bestPotion;
        }
        if (goldenApple >= 0) {
            return goldenApple;
        }
        // Pending gradual healing no longer blocks another meal. Callers use
        // projectedHealth() to stop the chain before queued healing would
        // exceed the intended recovery target.
        return allowFood ? bestFood : -1;
    }

    private static int findBestOrdinaryFoodSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!isOrdinaryFood(human, stack)) {
                continue;
            }
            FoodProperties food = stack.getFoodProperties(human);
            int score = Math.round(food.getNutrition() * (1.0F + food.getSaturationModifier()));
            if (score > bestScore) {
                bestSlot = i;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    private static boolean isOrdinaryFood(Human human, ItemStack stack) {
        return !stack.is(Items.GOLDEN_APPLE)
                && !stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                && isRecoveryFood(human, stack);
    }

    private static boolean isRecoveryFood(Human human, ItemStack stack) {
        return !stack.isEmpty() && stack.isEdible() && stack.getFoodProperties(human) != null;
    }

    private static boolean isHealingPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
        return effects.stream().anyMatch(effect -> effect.getEffect() == MobEffects.HEAL
                || effect.getEffect() == MobEffects.REGENERATION);
    }

    /** Exact built-in drinkable Instant Health II used by the recovery kits. */
    public static boolean isStrongInstantHealingPotion(ItemStack stack) {
        return stack.is(Items.POTION)
                && PotionUtils.getPotion(stack) == Potions.STRONG_HEALING;
    }

    private static boolean isSupportedSupplyPotion(ItemStack stack) {
        if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION)) {
            return false;
        }
        return PotionUtils.getMobEffects(stack).stream().anyMatch(effect ->
                effect.getEffect() == MobEffects.HEAL
                        || effect.getEffect() == MobEffects.REGENERATION
                        || effect.getEffect() == MobEffects.DAMAGE_BOOST
                        || effect.getEffect() == MobEffects.MOVEMENT_SPEED
                        || effect.getEffect() == MobEffects.MOVEMENT_SLOWDOWN
                        || effect.getEffect() == MobEffects.WEAKNESS);
    }

    private static int healingPotionScore(ItemStack stack) {
        int score = 0;
        for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
            if (effect.getEffect() == MobEffects.HEAL) {
                score += 100 + effect.getAmplifier() * 40;
            } else if (effect.getEffect() == MobEffects.REGENERATION) {
                score += 40 + effect.getAmplifier() * 20 + effect.getDuration() / 20;
            }
        }
        return score;
    }

    private static boolean insertInventory(HumanData data, ItemStack source) {
        if (source.isEmpty()) {
            return true;
        }
        for (int i = 0; i < data.getInventoryItemsSize() && !source.isEmpty(); i++) {
            ItemStack stored = data.getInventoryItem(i);
            if (stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, source)) {
                continue;
            }
            int room = stored.getMaxStackSize() - stored.getCount();
            int moved = Math.min(room, source.getCount());
            if (moved > 0) {
                ItemStack merged = stored.copy();
                merged.grow(moved);
                source.shrink(moved);
                data.setInventoryItem(i, merged);
            }
        }
        for (int i = 0; i < data.getInventoryItemsSize() && !source.isEmpty(); i++) {
            if (!data.getInventoryItem(i).isEmpty()) {
                continue;
            }
            int moved = Math.min(source.getMaxStackSize(), source.getCount());
            ItemStack placed = source.copy();
            placed.setCount(moved);
            source.shrink(moved);
            data.setInventoryItem(i, placed);
        }
        return source.isEmpty();
    }

    private static double healthRatio(Human human) {
        return human.getHealth() / Math.max(1.0F, human.getMaxHealth());
    }
}
