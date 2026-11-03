package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.behavior.combat.retreat.RecoveryIntentPolicy;
import dev.felix.hostilehumans.equipment.SpawnFoodConfig;
import dev.felix.hostilehumans.equipment.SpawnPotionConfig;
import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.ActionProgressSnapshot;
import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.CombatAiConfig;
import dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatRecoveryPolicy;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.firstaid.FirstAidCompat;
import dev.felix.hostilehumans.compat.scguns.ScgunsMedical;
import dev.felix.hostilehumans.compat.scguns.ScgunsSettings;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.ControlledEquipmentPolicy;
import dev.felix.hostilehumans.inventory.HumanInventoryCustody;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.progression.TierAttributes;

import net.minecraft.core.registries.BuiltInRegistries;
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

import java.util.List;
import java.util.EnumSet;
import dev.felix.hostilehumans.behavior.ActionCoordinator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Finite, inventory-backed recovery supplies. Food healing is derived from
 * nutrition and saturation and then delivered over time; no health is granted
 * unless a real consumable completes its use animation.
 */
public final class RecoverySupplies {
    private static final String KIT_GRANTED = "hostile_humans_unified:recovery_kit_granted_v2";
    private static final String PENDING_HEAL = "hostile_humans_unified:pending_food_heal";
    private static final String NEXT_HEAL_TICK = "hostile_humans_unified:next_food_heal_tick";
    private static final String FOOD_HEAL_DELAY = "hostile_humans_unified:food_heal_delay";
    private static final String TIER_THREE_MARKER = "hostile_humans_unified:tier_three";
    // Removed fixed-slot lease metadata. Retain the key only to clean old
    // entities when they are first reconciled by the inventory custody logic.
    private static final String RECOVERY_LEASE = "hostile_humans_unified:recovery_offhand_lease_v3";

    /**
     * One authoritative owner for a temporary hand/inventory exchange. Goal
     * instances may be stopped or replaced on adjacent ticks; keeping the
     * active session here makes completion idempotent and prevents two goals
     * from settling the same off-hand stack twice.
     */
    private static final Map<Human, UseSession> ACTIVE_USES = new WeakHashMap<>();
    // Rejected/cancelled uses and drinks latch until release; successful food may repeat.
    private static final Map<Human, ManualUse> MANUAL_USES = new WeakHashMap<>();
    private static final class ManualUse {
        UseSession session;
        boolean repeatReady;
        ItemStack remainder = ItemStack.EMPTY;
    }
    private static final Map<Human, RecoveryIntentPolicy> INTENTS = new WeakHashMap<>();
    private static final Map<Human, Boolean> COMPLETION_RECEIPTS = new WeakHashMap<>();
    private static final String BUFF_COOLDOWN = HumanRuntime.MOD_ID + ":self_buff_cooldown";
    private static final Map<Human, PotionRecoveryPolicy.Clock> REGENERATION_CLOCKS = new WeakHashMap<>();
    private static final Map<Human, ShieldSnapshot> SHIELD_AUDIT = new WeakHashMap<>();

    private static final double FOOD_USE_TIME_MULTIPLIER = 0.70D;

    private record ShieldSnapshot(int count, String state) {
    }

    public record UseSession(
            InteractionHand hand,
            ItemStack itemAtStart,
            int expectedCompletionTick,
            double startingHealthRatio,
            boolean food,
            boolean healingPotion,
            ItemStack actualItem,
            ActionCoordinator.Token token
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
        RandomSource random = human.getRandom();
        SpawnFoodConfig foodConfig = UnifiedConfig.get().spawnFood(TierAttributes.key(human));
        SpawnFoodConfig.Preset preset = foodConfig.roll(random);

        int appleCount = preset.goldenApples().roll(random);
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, appleCount);
        insertInventory(data, apples);
        int insertedApples = appleCount - apples.getCount();
        int enchantedApples = provisionEnchantedGoldenApple(human, foodConfig);
        int foodCount = foodConfig.foodCount(human, preset);
        int insertedFood = provisionFoodVariety(human, data, foodCount, foodConfig);
        SpawnPotionConfig.Granted potions = UnifiedConfig.get().spawnPotions(TierAttributes.key(human))
                .grant(human);
        int insertedPotions = potions.recovery();
        int tacticalItems = potions.tactical();

        human.getPersistentData().putInt("hostile_humans_unified:spawn_food_shortfall",foodCount-insertedFood);
        human.getPersistentData().putInt("hostile_humans_unified:recovery_kit_potions", insertedPotions);
        human.getPersistentData().putInt("hostile_humans_unified:tactical_kit_items", tacticalItems);
        human.getPersistentData().putInt("hostile_humans_unified:recovery_kit_food", insertedFood);
        human.getPersistentData().putInt("hostile_humans_unified:recovery_kit_golden_apples", insertedApples);
        human.getPersistentData().putInt("hostile_humans_unified:recovery_kit_enchanted_apples", enchantedApples);
    }

    public static boolean hasUsableSupply(Human human) {
        return hasSupply(human, true);
    }

    /** Physical recovery reserves, independent of temporary cooldowns and active buffs. */
    public static boolean isHealingSupplyForResupply(Human human, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isRecoveryFood(human, stack) || isHealingPotion(stack) || FirstAidCompat.isMedicine(stack)) return true;
        var medical = ScgunsMedical.profile(stack);
        return medical != null && (medical.healing() > 0
                || medical.effects().stream().anyMatch(ScgunsSettings.Effect::isRegeneration));
    }

    public static java.util.List<ActionProgressSnapshot> progress(Human human) {
        UseSession session = ACTIVE_USES.get(human);
        if (session == null || human.level().isClientSide) return java.util.List.of();
        if (OffhandFoodUse.owns(human, session)) return OffhandFoodUse.progress(human, session);
        var authority = HumanBehaviorControl.peekActions(human);
        boolean valid = authority != null && authority.running(session.token()) && isUseInProgress(human, session)
                && human.isAlive() && !human.isRemoved() && !human.isNoAi() && !HumanInventorySession.isEditing(human);
        long elapsed = valid ? Math.max(0, human.getTicksUsingItem()) : -1;
        long remaining = valid ? Math.max(0, human.getUseItemRemainingTicks()) : -1;
        return java.util.List.of(new ActionProgressSnapshot("recovery", ActionProgressSnapshot.item(session.actualItem()),
                ActionProgressSnapshot.location(human, session.actualItem()), valid ? "running" : "invalid",
                human.level().getGameTime(), elapsed, valid ? elapsed + remaining : -1, valid ? 1 : -1,
                elapsed, remaining, "当前恢复物品的原生实际使用计时"));
    }

    public static boolean hasActiveUse(Human human) {
        return ACTIVE_USES.containsKey(human);
    }

    public static boolean reservesHand(Human human, EquipmentSlot slot) {
        UseSession session = ACTIVE_USES.get(human);
        return session != null && (session.hand() == InteractionHand.MAIN_HAND
                ? slot == EquipmentSlot.MAINHAND : slot == EquipmentSlot.OFFHAND);
    }

    /** Only an already running drink gets the short completion barrier, never queued/chained uses. */
    public static boolean isDrinkingPotion(Human human) {
        UseSession session = ACTIVE_USES.get(human);
        ManualUse manual = MANUAL_USES.get(human);
        return session != null && session.itemAtStart().is(Items.POTION) && session.itemAtStart().getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK
                && ((manual != null && manual.session == session) || human.tickCount <= session.expectedCompletionTick() + 40)
                && isUseInProgress(human, session);
    }

    /** Server-owned parallel food state; never use it to suppress combat goals. */
    public static boolean isEatingFood(Human human) {
        UseSession session = ACTIVE_USES.get(human);
        return session != null && OffhandFoodUse.running(human, session);
    }

    public static boolean isUseInProgress(Human human, UseSession session) {
        if (session == null || ACTIVE_USES.get(human) != session) return false;
        return OffhandFoodUse.owns(human, session) ? OffhandFoodUse.running(human, session)
                : human.isUsingItem() && human.getUsedItemHand() == session.hand()
                && human.getItemInHand(session.hand()) == session.actualItem()
                && ItemStack.matches(session.actualItem(), session.itemAtStart());
    }

    static boolean canStartCombatFood(Human human) { return OffhandFoodUse.canStart(human); }

    /** Optional item adapters may settle only our inventory-backed use session. */
    public static boolean ownsSupplyUse(Human human, ItemStack stack) {
        UseSession session = ACTIVE_USES.get(human);
        return session != null && ItemStack.isSameItemSameTags(session.itemAtStart(), stack)
                && session.itemAtStart().getCount() == stack.getCount()
                && (!human.isUsingItem() || human.getUsedItemHand() == session.hand());
    }

    /** Hand ownership lasts only for the current real consumable. */
    public static boolean hasHandCustody(Human human) {
        return ACTIVE_USES.containsKey(human);
    }

    /** Inventory ownership belongs only to the exchange transaction. */
    public static boolean reservesInventorySlot(Human human, int slot) { return false; }


    public static boolean hasOrdinaryFood(Human human) {
        if (isOrdinaryFood(human, human.getOffhandItem()) || isOrdinaryFood(human, human.getMainHandItem())) return true;
        HumanData data = human.getData();
        if (data == null) return false;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (isOrdinaryFood(human, data.getInventoryItem(i))) return true;
        }
        return false;
    }

    private static int findBestSlot(Human human, boolean preferFood, boolean allowFood) {
        return findBestSlot(human, preferFood, allowFood, false);
    }
    private static int findBestSlot(Human human, boolean preferFood, boolean allowFood, boolean instantOnly) {
        if (human.getData() == null || unavailable(human)) return -3;
        double ratio = healthRatio(human), missing = human.getMaxHealth() - human.getHealth();
        boolean regen = intent(human).wantsRegeneration(human.tickCount, ratio);
        RecoverySelectionPolicy.Candidate best = null;
        for (int i = -2; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack stack = candidateAt(human, i);
            if (stack.isEmpty() || !canUsePotionNow(human, stack)) continue;
            int rank; double healing; double quality;
            if (isInstantHealingPotion(stack)) {
                if (!PotionRecoveryPolicy.urgentInstantHealing(ratio)) continue;
                rank = 1; healing = recoveryUseDuration(stack) <= 100 ? 8 : 0; quality = HumanLootManager.potionValue(PotionUtils.getMobEffects(stack), false);
            } else if (instantOnly) continue;
            else if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE)) {
                if (ratio >= .25 || !usefulApple(human, stack)) continue;
                rank = stack.is(Items.ENCHANTED_GOLDEN_APPLE) ? 0 : 2;
                healing = appleHealing(human, stack); quality = rank == 0 ? 260 : 160;
            } else if (MedicalSupplies.isSupply(stack)) {
                if (ratio >= .25 || !MedicalSupplies.canUse(human, stack)) continue;
                if (MedicalSupplies.isMorphine(stack)) { rank = 7; healing = 0; }
                else { rank = MedicalSupplies.directHealing(stack) > 0 ? 3 : 5; healing = MedicalSupplies.expectedHealing(human, stack, 100); }
                quality = MedicalSupplies.inventoryValue(stack);
            } else if (isRegenerationPotion(stack)) {
                if (!regen) continue;
                rank = 4; healing = PotionUtils.getMobEffects(stack).stream()
                        .filter(e -> e.getEffect() == MobEffects.REGENERATION)
                        .mapToDouble(e -> Math.min(Math.max(0, 100 - recoveryUseDuration(stack)), e.getDuration()) / 20D * (e.getAmplifier() >= 1 ? 2 : 1)).sum();
                quality = HumanLootManager.potionValue(PotionUtils.getMobEffects(stack), false);
            } else if (allowFood && isOrdinaryFood(human, stack) && needsFood(human)
                    && (!human.isFleeing || RetreatRecoveryPolicy.canStartFood(human, human.getTarget()))) {
                rank = 6; healing = Math.min(foodHealingValue(human, stack), Math.max(0, 100D - recoveryUseDuration(stack)) / Math.max(1, CombatAiConfig.forHuman(human).gradualHealIntervalTicks()));
                quality = HumanLootManager.foodValue(stack.getFoodProperties(human));
            } else continue;
            if (!Double.isNaN(healing) && healing <= 0 && rank == 6) continue;
            boolean instant = rank == 1;
            rank = RecoverySelectionPolicy.retreatRank(rank, isRecoveryFood(human, stack), human.isFleeing);
            double netMissing = instant ? missing : Math.max(0, missing - pendingHealingWithin(human, 100));
            var candidate = new RecoverySelectionPolicy.Candidate(i, rank, Math.min(netMissing, healing), recoveryUseDuration(stack), quality);
            if (RecoverySelectionPolicy.better(candidate, best)) best = candidate;
        }
        return best == null ? -3 : best.slot();
    }
    private static boolean hasSupply(Human human, boolean allowFood) { return hasRecoveryChoice(human, allowFood); }
    public static boolean isRecoverySupply(Human human, ItemStack stack) {
        return MedicalSupplies.isSupply(stack) || isSupportedSupplyPotion(stack) || isRecoveryFood(human, stack);
    }
    public static boolean hasRecoveryChoice(Human human, boolean allowFood) { return findBestSlot(human, false, allowFood) >= -2; }
    public static boolean recentActualDamage(Human human) { return intent(human).recentDamage(human.tickCount); }
    public static boolean needsFood(Human human) {
        return human.getHealth() < human.getMaxHealth() && projectedHealth(human) < human.getMaxHealth();
    }
    private static double appleHealing(Human human, ItemStack stack) {
        FoodProperties food = stack.getFoodProperties(human);
        if (food == null) return 0;
        double amount = 0;
        for (var pair : food.getEffects()) {
            var effect = pair.getFirst();
            if (effect.getEffect() != MobEffects.REGENERATION || !newEffectBenefit(human, effect)) continue;
            amount += Math.min(Math.max(0, 100 - recoveryUseDuration(stack)), effect.getDuration()) / 20D * (effect.getAmplifier() >= 1 ? 2 : 1) * pair.getSecond();
        }
        return Math.min(Math.max(0, human.getMaxHealth() - human.getHealth() - pendingHealingWithin(human, 100)), amount);
    }
    private static boolean newEffectBenefit(Human human, MobEffectInstance effect) {
        var current = human.getEffect(effect.getEffect());
        return current == null || effect.getAmplifier() > current.getAmplifier();
    }
    private static boolean usefulApple(Human human, ItemStack stack) {
        FoodProperties food = stack.getFoodProperties(human);
        if (food == null) return false;
        for (var pair : food.getEffects()) {
            var effect = pair.getFirst();
            if (!effect.getEffect().isBeneficial() || !newEffectBenefit(human, effect)) continue;
            if (effect.getEffect() != MobEffects.REGENERATION || appleHealing(human, stack) > 0) return true;
        }
        return false;
    }
    /** Expected already queued restoration in this horizon, excluding unused food inventory. */
    static double pendingHealingWithin(Human human, int ticks) {
        double food = Math.min(Math.max(0, human.getPersistentData().getFloat(PENDING_HEAL)),
                ticks / Math.max(1D, CombatAiConfig.forHuman(human).gradualHealIntervalTicks()));
        var state = human.getPersistentData().getCompound(HumanRuntime.MOD_ID + ":first_aid");
        int delay = Math.max(1, state.getInt("next_pulse")), interval = Math.max(1, state.getInt("interval"));
        int pulses = ticks < delay ? 0 : Math.min(Math.max(0, state.getInt("pulses")), 1 + (ticks - delay) / interval);
        double scheduled = pulses * Math.max(0, state.getFloat("heal_per_pulse"));
        var regeneration = human.getEffect(MobEffects.REGENERATION);
        double regen = regeneration == null ? 0 : Math.min(ticks, regeneration.getDuration() == -1 ? ticks : regeneration.getDuration())
                / 20D * (regeneration.getAmplifier() >= 1 ? 2 : 1);
        return food + scheduled + regen;
    }

    public static boolean hasPendingGradualHealing(Human human) {
        return pendingGradualHealing(human) > 0.0F;
    }

    public static float pendingGradualHealing(Human human) {
        return Math.max(0.0F, human.getPersistentData().getFloat(PENDING_HEAL)) + MedicalSupplies.pendingHealing(human);
    }

    /** Observation of the food queue only; medicine and potion effects are separate channels. */
    public static float pendingFoodHealing(Human human) {
        float pending = human.getPersistentData().getFloat(PENDING_HEAL);
        return Float.isFinite(pending) ? Math.max(0.0F, pending) : 0.0F;
    }

    public static float projectedHealth(Human human) {
        return Math.min(human.getMaxHealth(), human.getHealth() + pendingGradualHealing(human));
    }

    public static UseSession beginUse(Human human) {
        return beginUse(human, false);
    }


    public static UseSession beginIdleUse(Human human) {
        return beginUse(human, true);
    }

    public static boolean hasUrgentInstantHealing(Human human) {
        return PotionRecoveryPolicy.urgentInstantHealing(healthRatio(human))
                && findBestInstantHealingSlot(human) >= -2;
    }

    public static boolean hasInstantHealingInProgress(Human human) {
        UseSession active = ACTIVE_USES.get(human);
        ItemStack item = active != null ? active.itemAtStart() : human.getUseItem();
        return item.is(Items.POTION) && PotionUtils.getMobEffects(item).stream()
                .anyMatch(effect -> effect.getEffect() == MobEffects.HEAL);
    }

    static UseSession beginUrgentInstantHealing(Human human) {
        return beginInventoryUse(human, human.getData(), findBestSlot(human, false, true));
    }

    private static int findBestInstantHealingSlot(Human human) {
        return findBestSlot(human, false, false, true);
    }

    /** Starts food-only recovery without selecting potions or either golden apple. */
    public static UseSession beginCombatFoodUse(Human human) {
        if (!canStartCombatFood(human)) {
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
        return beginInventoryUse(human, human.getData(), findBestSlot(human, false, true));
    }

    public static boolean hasEnchantedGoldenApple(Human human) {
        return healthRatio(human) < .25D && ((human.getOffhandItem().is(Items.ENCHANTED_GOLDEN_APPLE) && usefulApple(human, human.getOffhandItem()))
                || findItemSlot(human.getData(), Items.ENCHANTED_GOLDEN_APPLE) >= 0);
    }

    public static boolean hasUsefulDrinkableBuff(Human human) {
        return findUsefulDrinkableBuffSlot(human) >= -2;
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
                            || effect.getEffect() == MobEffects.WEAKNESS
                            || effect.getEffect() == MobEffects.POISON);
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
        if (unavailable(human)) return ItemStack.EMPTY;
        var actions = HumanBehaviorControl.actions(human);
        var token = actions.request("recovery:take-one", EnumSet.of(ActionCoordinator.Resource.INVENTORY), 70, true, 2);
        if (!actions.acquire(token)) {
            actions.finish(token, ActionCoordinator.Status.CANCELLED, "inventory busy"); return ItemStack.EMPTY;
        }
        ItemStack actual = data.getInventoryItem(slot);
        ItemStack result = actual.isEmpty() ? ItemStack.EMPTY : actual.split(1);
        actions.finish(token, result.isEmpty() ? ActionCoordinator.Status.FAILED : ActionCoordinator.Status.COMPLETED,
                result.isEmpty() ? "source absent" : "one real item removed");
        return result;
    }

    private static UseSession beginUse(Human human, boolean preferFood) {
        if (!OffhandFoodUse.canStart(human)) return null;
        HumanData data = human.getData();
        int slot = findBestSlot(human, preferFood, true);
        return beginInventoryUse(human, data, slot);
    }

    private static UseSession beginInventoryUse(Human human, HumanData data, int slot) {
        if (human.hhu$shieldDefense().shouldSuppressRecovery() || unavailable(human)
                || !CombatAiConfig.forHuman(human).enabled() || !CombatAiConfig.forHuman(human).itemRecoveryEnabled()
                || data == null || slot < -2 || slot >= data.getInventoryItemsSize() || hasActiveUse(human)) return null;
        ItemStack actual = candidateAt(human, slot);
        if (actual.isEmpty() || actualReferences(human, actual) != 1 || !canUsePotionNow(human, actual)) return null;
        boolean parallelFood = isRecoveryFood(human, actual);
        if (isOrdinaryFood(human, actual) && !needsFood(human)) return null;
        if (parallelFood ? !OffhandFoodUse.canStart(human) : human.isUsingItem()) return null;
        // Exact reference predicate: equal type/NBT never substitutes another real stack.
        var exchange = HumanEquipmentExchange.request(human, "recovery", List.of(
                new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, candidate -> candidate == actual)));
        if (exchange != HumanEquipmentExchange.Result.SUCCESS) return null;
        return beginHeldOffhandUse(human, actual, parallelFood, null);
    }

    /** Explicit supply input preserves occupied hands; an empty offhand may draw real backpack food. */
    public static UseSession beginManualOffhandUse(Human human) {
        if (human.level().isClientSide) return null;
        ManualUse existing = MANUAL_USES.get(human);
        if (existing != null && !existing.repeatReady) return existing.session;
        ManualUse manual = existing == null ? new ManualUse() : existing;
        manual.repeatReady = false;
        MANUAL_USES.put(human, manual);
        if (unavailable(human) || !HumanCombatAuthority.live(human) || hasActiveUse(human)) return null;
        ItemStack actual = prepareManualSupply(human, manual.remainder);
        boolean food = ControlledEquipmentPolicy.offhandFood(actual) && isRecoveryFood(human, actual);
        boolean drink = actual.is(Items.POTION) && actual.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK;
        if (!food && !drink) return null;
        return beginHeldOffhandUse(human, actual, food, manual);
    }

    /** Preserve usable manual equipment; explicit supply input can stow an empty bottle/bowl on refill. */
    private static ItemStack prepareManualSupply(Human human, ItemStack previousRemainder) {
        ItemStack held = human.getOffhandItem();
        if (human.getData() == null || (!held.isEmpty()
                && !ControlledEquipmentPolicy.supplyRemainder(held)
                && (held != previousRemainder || isRecoveryFood(human, held)))) return held;
        HumanData data = human.getData();
        for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            ItemStack candidate = data.getInventoryItem(slot);
            if (!ControlledEquipmentPolicy.offhandFood(candidate) || !isRecoveryFood(human, candidate)) continue;
            var result = HumanEquipmentExchange.request(human, "manual:food-refill", List.of(
                    new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, item -> item == candidate)));
            return result == HumanEquipmentExchange.Result.SUCCESS ? human.getOffhandItem() : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** Held food repeats only after confirmed consumption. Release cancels an unfinished session. */
    public static void tickManualUse(Human human, boolean held) {
        if (human.level().isClientSide) return;
        if (!held) {
            cancelManualUse(human);
            MANUAL_USES.remove(human);
            return;
        }
        ManualUse pending = MANUAL_USES.get(human);
        if (pending == null || pending.repeatReady) beginManualOffhandUse(human);
        ManualUse manual = MANUAL_USES.get(human);
        if (manual != null) tickManualSession(human, manual, true);
    }

    /** Keep the press latched until release; never reset already scheduled recovery effects. */
    public static void cancelManualUse(Human human) {
        ManualUse manual = MANUAL_USES.get(human);
        if (manual != null && manual.session != null)
            finishUse(human, manual.session, completed(human, manual.session));
        if (manual != null) manual.repeatReady = false;
    }

    private static void tickManualSession(Human human, ManualUse manual, boolean advanceFood) {
        UseSession session = manual.session;
        if (session == null) return;
        if (ACTIVE_USES.get(human) != session) { manual.session = null; return; }
        boolean done = completed(human, session);
        var actions = HumanBehaviorControl.actions(human);
        if (unavailable(human) || !HumanCombatAuthority.live(human) || !actions.running(session.token())
                || (!done && (human.getOffhandItem() != session.actualItem()
                || actualReferences(human, session.actualItem()) != 1
                || !ItemStack.matches(session.actualItem(), session.itemAtStart())))) {
            finishUse(human, session, done); return;
        }
        if (done) { finishUse(human, session, true); return; }
        if (!isUseInProgress(human, session) || !actions.heartbeat(session.token())) {
            finishUse(human, session, false); return;
        }
        if (advanceFood) {
            OffhandFoodUse.tick(human);
            if (!isUseInProgress(human, session)) finishUse(human, session, completed(human, session));
        }
    }

    /** Shared execution kernel after the caller has selected its policy and real hand layout. */
    private static UseSession beginHeldOffhandUse(Human human, ItemStack actual, boolean parallelFood, ManualUse manual) {
        if (unavailable(human) || hasActiveUse(human) || actual.isEmpty() || human.getOffhandItem() != actual
                || actualReferences(human, actual) != 1
                || (parallelFood ? !OffhandFoodUse.canStart(human) : human.isUsingItem())) return null;
        var actions = HumanBehaviorControl.actions(human);
        boolean drink = actual.is(Items.POTION) && actual.getUseAnimation() == net.minecraft.world.item.UseAnim.DRINK;
        // The exchange already released INVENTORY and MAIN_HAND. Parallel food leaves native weapon use free.
        var resources = EnumSet.of(ActionCoordinator.Resource.OFF_HAND);
        if (!parallelFood) resources.add(ActionCoordinator.Resource.USE_ITEM);
        var token = manual == null
                ? actions.request("recovery:use", resources, 70, !drink, recoveryUseDuration(actual) + 100L)
                : actions.requestLease("recovery:manual-use", resources, 70, !drink, 20L);
        if (!actions.acquire(token)) {
            actions.finish(token, ActionCoordinator.Status.CANCELLED, "use resources unavailable");
            return null;
        }
        UseSession session = new UseSession(InteractionHand.OFF_HAND, actual.copy(),
                human.tickCount + recoveryUseDuration(actual), healthRatio(human), parallelFood,
                isHealingPotion(actual), actual, token);
        ACTIVE_USES.put(human, session);
        if (manual != null) manual.session = session;
        COMPLETION_RECEIPTS.remove(human);
        if (parallelFood) OffhandFoodUse.start(human, session);
        else { human.startUsingItem(InteractionHand.OFF_HAND); accelerateFoodUse(human, actual); }
        if (unavailable(human) || (manual != null && (!HumanCombatAuthority.live(human)
                || MANUAL_USES.get(human) != manual || manual.session != session))
                || !HumanBehaviorControl.actions(human).running(session.token())
                || !isUseInProgress(human, session)) {
            HumanBehaviorControl.actions(human).finish(session.token(), ActionCoordinator.Status.FAILED, "use start rejected or changed");
            finishUse(human, session, false);
            // A start callback may have cancelled custody before vanilla publishes its use state.
            if (!ACTIVE_USES.containsKey(human) && human.isUsingItem()
                    && human.getUsedItemHand() == session.hand() && human.getUseItem() == actual)
                human.stopUsingItem();
            return null;
        }
        return session;
    }

    /** No implicit chained use: yield to defense and choose again from the current layout. */
    public static UseSession continueFoodUse(Human human, UseSession completedSession, double targetHealthRatio) {
        // Finish each real use before a new decision. No fixed-slot or hidden multi-meal lease.
        finishUse(human, completedSession, completed(human, completedSession));
        return null;
    }

    public static boolean completed(Human human, UseSession session) {
        if (session == null || ACTIVE_USES.get(human) != session) return false;
        return OffhandFoodUse.owns(human, session) ? OffhandFoodUse.completed(human, session)
                : Boolean.TRUE.equals(COMPLETION_RECEIPTS.get(human));
    }

    public static boolean shouldCommitToUse(Human human, UseSession session, CombatAiConfig config) {
        if (isDrinkingPotion(human)) return true;
        if (!human.isUsingItem()) {
            return completed(human, session);
        }
        int remaining = human.getUseItemRemainingTicks();
        double damagePaidRatio = Math.max(0.0D, session.startingHealthRatio() - healthRatio(human));
        boolean nearCompletion = remaining <= config.recoveryCommitTicks();
        boolean healthCanPay = healthRatio(human) > config.criticalHealthRatio() * 0.75D;
        return healthCanPay && (nearCompletion || damagePaidRatio < config.recoveryDamageToleranceRatio());
    }

    /** Living-tick safety net: invalid ownership cannot finish later. */
    public static void tickHandCustody(Human human) {
        ManualUse manual = MANUAL_USES.get(human);
        if (manual != null) {
            tickManualSession(human, manual, false);
            if (HumanCombatAuthority.bound(human)) return;
            MANUAL_USES.remove(human);
        }
        if (unavailable(human) || !CombatAiConfig.forHuman(human).enabled()
                || !CombatAiConfig.forHuman(human).itemRecoveryEnabled()) { clear(human, "recovery unavailable"); return; }
        intent(human).advance(human.tickCount);
        UseSession session = ACTIVE_USES.get(human);
        if (session == null) return;
        boolean done = completed(human, session);
        boolean running = HumanBehaviorControl.actions(human).running(session.token());
        if (!running || (!done && human.getOffhandItem() != session.actualItem())
                || human.tickCount > session.expectedCompletionTick() + 40
                || (human.hhu$shieldDefense().shouldSuppressRecovery() && !isDrinkingPotion(human))) {
            finishUse(human, session, done); return;
        }
        // Ordinary food yields before its next consumption tick. Golden apples keep their
        // own selection rank, preventing cancel/reselect loops against urgent healing.
        if (!done && isOrdinaryFood(human, session.itemAtStart()) && hasUrgentInstantHealing(human)) {
            finishUse(human, session, false);
            return;
        }
        OffhandFoodUse.tick(human);
        if (!isUseInProgress(human, session)) finishUse(human, session, completed(human, session));
    }

    private static boolean unavailable(Human human) {
        return human.level().isClientSide || !human.isAlive() || human.isRemoved()
                || human.isNoAi() || HumanInventorySession.isEditing(human);
    }
    /** Hook for unload, death, editing, ability changes and behavior-session cancellation. */
    public static void clear(Human human, String reason) {
        UseSession session = ACTIVE_USES.get(human);
        if (session != null) finishUse(human, session, completed(human, session));
        MANUAL_USES.remove(human);
        OffhandFoodUse.clear(human);
        INTENTS.remove(human);
        COMPLETION_RECEIPTS.remove(human);
        // Action/hand cancellation must not restart an already active potion's healing cadence.
        if (!human.isAlive() || human.isRemoved()) REGENERATION_CLOCKS.remove(human);
        human.getPersistentData().remove(RECOVERY_LEASE);
    }
    private static RecoveryIntentPolicy intent(Human human) {
        return INTENTS.computeIfAbsent(human, ignored -> new RecoveryIntentPolicy());
    }
    /** Supply final real health loss only; blocked/absorbed damage is excluded. */
    public static void recordActualDamage(Human human, float loss) {
        if (!human.level().isClientSide && !unavailable(human))
            intent(human).record(human.tickCount, loss, human.getMaxHealth());
    }

    /** Cancels without changing the current real layout before manual edits. */
    public static void prepareForManualInventory(Human human) { clear(human, "manual inventory edit"); }

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
        HumanRuntime.LOGGER.debug(
                "Shield offhand transition npc={} from={} to={} activeRecovery={} slot={} ownedShields={}",
                human.getUUID(), itemId(previous), itemId(current), active != null,
                active == null ? "none" : active.token().sequence(), shieldCount
        );
        if (SpartanEquipmentCompat.isShield(previous)
                && !SpartanEquipmentCompat.isShield(current)
                && active == null) {
            HumanRuntime.LOGGER.warn(
                    "Shield left offhand outside recovery custody npc={} from={} to={} ownedShields={}",
                    human.getUUID(), itemId(previous), itemId(current), shieldCount
            );
        }
    }

    /** Periodic whole-owner audit, including all 30 HumanData slots. */
    public static void auditOwnedShields(Human human) {
        ShieldSnapshot current = shieldSnapshot(human);
        ShieldSnapshot previous = SHIELD_AUDIT.put(human, current);
        if (previous == null || (previous.count() == current.count() && previous.state().equals(current.state()))) {
            return;
        }
        HumanRuntime.LOGGER.debug(
                "Shield ownership changed npc={} beforeCount={} afterCount={} activeRecovery={} before=[{}] after=[{}]",
                human.getUUID(), previous.count(), current.count(), ACTIVE_USES.containsKey(human),
                previous.state(), current.state()
        );
        if (current.count() < previous.count() && human.isAlive()) {
            HumanRuntime.LOGGER.warn(
                    "Shield ownership decreased npc={} beforeCount={} afterCount={} activeRecovery={} before=[{}] after=[{}]",
                    human.getUUID(), previous.count(), current.count(), ACTIVE_USES.containsKey(human),
                    previous.state(), current.state()
            );
        }
    }

    public static void finishUse(Human human, UseSession session, boolean completed) {
        if (session == null || ACTIVE_USES.get(human) != session) return;
        boolean receipt = completed && completed(human, session);
        // Invalidate ownership before native release callbacks can re-enter cleanup.
        ACTIVE_USES.remove(human, session);
        ManualUse manual = MANUAL_USES.get(human);
        if (manual != null && manual.session == session) {
            manual.session = null;
            // Only real food completion rearms held input. Failed uses and drinks require release.
            if (receipt && session.food()) {
                manual.remainder = human.getOffhandItem();
                manual.repeatReady = true;
            }
        }
        if (isUsingSessionHand(human, session) && human.getUseItem() == session.actualItem()) human.stopUsingItem();
        OffhandFoodUse.clear(human);
        COMPLETION_RECEIPTS.remove(human);
        HumanBehaviorControl.actions(human).finish(session.token(), receipt ? ActionCoordinator.Status.COMPLETED
                : ActionCoordinator.Status.CANCELLED, receipt ? "real consumable completed" : "use cancelled or changed");
        if (receipt) {
            if (session.food()) queueFoodHealing(human, session.itemAtStart());
            if (isBuffPotion(session.itemAtStart())) human.getPersistentData().putInt(BUFF_COOLDOWN, 600);
            if (isRegenerationPotion(session.itemAtStart())) intent(human).consumed();
            rememberRecoveryItem(human, session.itemAtStart());
        }
        // The current real remainder stays in the real hand. Current tactics request the next layout.
        human.getPersistentData().remove(RECOVERY_LEASE);
    }

    public static boolean hasRecoverableShield(Human human) {
        return !HumanLootManager.bestOwnedShield(human).isEmpty();
    }

    private static boolean isUsingSessionHand(Human human, UseSession session) {
        return human.isUsingItem() && human.getUsedItemHand() == session.hand();
    }

    /** A caller with a current shield need can request its present real layout. */
    public static boolean restoreShieldAfterRecovery(Human human) {
        if (hasActiveUse(human) || unavailable(human)) return false;
        ItemStack selected = HumanLootManager.bestOwnedShield(human);
        if (selected.isEmpty()) return false;
        return HumanEquipmentExchange.request(human, "recovery:shield", List.of(
                new HumanEquipmentExchange.Need(EquipmentSlot.OFFHAND, candidate -> candidate == selected)))
                == HumanEquipmentExchange.Result.SUCCESS;
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
        StringBuilder state = HumanRuntime.LOGGER.isDebugEnabled() ? new StringBuilder() : null;
        if (SpartanEquipmentCompat.isShield(human.getMainHandItem())) {
            count += human.getMainHandItem().getCount();
            if (state != null) appendShieldState(state, "main", human.getMainHandItem());
        }
        if (SpartanEquipmentCompat.isShield(human.getOffhandItem())) {
            count += human.getOffhandItem().getCount();
            if (state != null) appendShieldState(state, "off", human.getOffhandItem());
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (!SpartanEquipmentCompat.isShield(stored)) {
                    continue;
                }
                count += stored.getCount();
                if (state != null) appendShieldState(state, "inv" + i, stored);
            }
        }
        return new ShieldSnapshot(count, state == null ? "" : state.toString());
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

    /** Total Human regeneration; vanilla pulses are suppressed by HumanRecoveryEffects. */
    private static void tickRegenerationHealing(Human human) {
        MobEffectInstance regeneration = human.getEffect(MobEffects.REGENERATION);
        if (human.level().isClientSide || !human.isAlive() || regeneration == null
                || !PotionRecoveryPolicy.regenerationActive(regeneration.getDuration())) {
            REGENERATION_CLOCKS.remove(human);
            return;
        }
        int interval = PotionRecoveryPolicy.REGENERATION_INTERVAL_TICKS;
        PotionRecoveryPolicy.Clock clock = REGENERATION_CLOCKS.computeIfAbsent(human,
                ignored -> new PotionRecoveryPolicy.Clock(human.tickCount, interval));
        if (clock.pulse(human.tickCount, interval) && human.getHealth() < human.getMaxHealth()) {
            human.heal(PotionRecoveryPolicy.regenerationStep(regeneration.getAmplifier()));
        }
    }

    private static boolean canUsePotionNow(Human human, ItemStack stack) {
        if (MedicalSupplies.isSupply(stack)) return MedicalSupplies.canUse(human, stack);
        if (!stack.is(Items.POTION)) return true;
        MobEffectInstance active = human.getEffect(MobEffects.REGENERATION);
        return active == null || !PotionRecoveryPolicy.regenerationActive(active.getDuration())
                || PotionUtils.getMobEffects(stack).stream().noneMatch(effect -> effect.getEffect() == MobEffects.REGENERATION);
    }

    /** Called after native completion; all NPC-specific potion settlement belongs here. */
    public static void onUseCompleted(Human human, InteractionHand hand, ItemStack before) {
        UseSession session = ACTIVE_USES.get(human);
        if (human.level().isClientSide || session == null || !HumanBehaviorControl.actions(human).running(session.token()) || session.hand() != hand
                || !ItemStack.matches(before, session.itemAtStart()) || COMPLETION_RECEIPTS.containsKey(human)) return;
        consumeCompletedPotion(human, hand, before);
        ItemStack after = human.getItemInHand(hand);
        COMPLETION_RECEIPTS.put(human, !ItemStack.isSameItemSameTags(before, after) || after.getCount() < before.getCount());
    }

    /** Advance effects even while inventory editing pauses AI equipment writers. */
    public static void tickEffects(Human human) {
        if (human.level().isClientSide || !human.isAlive()) return;
        int buffCooldown = human.getPersistentData().getInt(BUFF_COOLDOWN);
        if (buffCooldown > 0) human.getPersistentData().putInt(BUFF_COOLDOWN, buffCooldown - 1);
        tickRegenerationHealing(human);
        MedicalSupplies.tick(human);
        tickGradualHealing(human);
    }

    /** Preserve modded finish results that already consumed the bottle. */
    private static void consumeCompletedPotion(Human human, InteractionHand hand, ItemStack before) {
        if (human.level().isClientSide || !before.is(Items.POTION)) return;
        ItemStack after = human.getItemInHand(hand);
        if (!PotionRecoveryPolicy.needsPotionCharge(ItemStack.isSameItemSameTags(before, after),
                before.getCount(), after.getCount())) return;
        ItemStack remainder = after.copy();
        remainder.shrink(1);
        human.setItemInHand(hand, remainder.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : remainder);
        // A potion stack keeps its remaining doses; stow the empty bottle separately
        // without evicting recovery gear, or drop it when no inventory space remains.
        if (!remainder.isEmpty()) HumanInventoryCustody.stowOrDrop(human, new ItemStack(Items.GLASS_BOTTLE));
    }

    private static void tickGradualHealing(Human human) {
        float pending = human.getPersistentData().getFloat(PENDING_HEAL);
        if (!human.isAlive()) return;
        if (pending <= 0.0F) {
            human.getPersistentData().remove(FOOD_HEAL_DELAY);
            human.getPersistentData().remove(NEXT_HEAL_TICK);
            return;
        }
        float healthBefore = human.getHealth();
        float missingHealth = human.getMaxHealth() - healthBefore;
        if (missingHealth <= 0.0F) return;
        // Entity.tickCount resets on load. Persist a remaining delay instead of
        // a deadline in that transient clock; old saved courses resume in one interval.
        if (!human.getPersistentData().contains(FOOD_HEAL_DELAY)) {
            human.getPersistentData().putInt(FOOD_HEAL_DELAY,
                    CombatAiConfig.forHuman(human).gradualHealIntervalTicks());
        }
        human.getPersistentData().remove(NEXT_HEAL_TICK);
        int remaining = human.getPersistentData().getInt(FOOD_HEAL_DELAY) - 1;
        if (remaining > 0) {
            human.getPersistentData().putInt(FOOD_HEAL_DELAY, remaining);
            return;
        }
        float step = Math.min(Math.min(1.0F, pending), missingHealth);
        human.heal(step);
        float restored = Math.max(0.0F, human.getHealth() - healthBefore);
        pending -= Math.min(step, restored);
        if (pending <= 0.0F) {
            human.getPersistentData().remove(PENDING_HEAL);
            human.getPersistentData().remove(NEXT_HEAL_TICK);
            human.getPersistentData().remove(FOOD_HEAL_DELAY);
        } else {
            human.getPersistentData().putFloat(PENDING_HEAL, pending);
            human.getPersistentData().putInt(
                    FOOD_HEAL_DELAY,
                    CombatAiConfig.forHuman(human).gradualHealIntervalTicks()
            );
        }
    }

    private static void queueFoodHealing(Human human, ItemStack food) {
        float amount = foodHealingValue(human, food);
        if (amount <= 0.0F) {
            return;
        }
        CombatAiConfig config = CombatAiConfig.forHuman(human);
        float pending = human.getPersistentData().getFloat(PENDING_HEAL);
        human.getPersistentData().putFloat(PENDING_HEAL, pending + amount);
        human.getPersistentData().putInt(
                FOOD_HEAL_DELAY,
                config.gradualHealIntervalTicks()
        );
        human.getPersistentData().putFloat("hostile_humans_unified:last_food_heal_value", amount);
    }

    private static float foodHealingValue(Human human, ItemStack food) {
        FoodProperties properties = food.getFoodProperties(human);
        if (properties == null) {
            return 0.0F;
        }
        CombatAiConfig config = CombatAiConfig.forHuman(human);
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
                "hostile_humans_unified:last_recovery_item",
                BuiltInRegistries.ITEM.getKey(item.getItem()).toString()
        );
    }

    private static int provisionFoodVariety(Human human, HumanData data, int totalCount, SpawnFoodConfig config) {
        List<Item> available = config.availableFoods(human);
        if (!config.enabled || available.isEmpty() || totalCount <= 0) return 0;
        int variety = Math.min(available.size(), human.getRandom().nextInt(config.varietyMin, config.varietyMax + 1));
        int remaining=totalCount;
        int nonStackable=0;
        for(int slot=0;slot<data.getInventoryItemsSize();slot++) {
            ItemStack stored=data.getInventoryItem(slot);
            if(stored.isEdible() && stored.getMaxStackSize()==1) nonStackable+=stored.getCount();
        }
        List<Item> selected=available.subList(0,variety);
        for(int i=0;i<variety&&remaining>0;i++) {
            Item item=selected.get(i);
            int upper=Math.max(1,remaining-Math.max(0,variety-i-1));
            int count=i==variety-1?remaining:human.getRandom().nextInt(1,Math.min(item.getMaxStackSize(),upper)+1);
            if(item.getMaxStackSize()==1) count=Math.min(count,Math.max(0,2-nonStackable));
            if(count<=0) continue;
            ItemStack stack=new ItemStack(item,count);
            insertInventory(data,stack);
            int inserted=count-stack.getCount();
            remaining-=inserted;
            if(item.getMaxStackSize()==1) nonStackable+=inserted;
        }
        // Transfer all remaining count only to selected, allowed, stackable foods.
        List<Item> stackable=selected.stream().filter(item->item.getMaxStackSize()>1).toList();
        for(int cursor=0;remaining>0 && !stackable.isEmpty() && cursor<totalCount;cursor++) {
            Item item=stackable.get(cursor%stackable.size());
            ItemStack stack=new ItemStack(item,Math.min(remaining,item.getMaxStackSize()));
            int before=stack.getCount();
            insertInventory(data,stack);
            if(stack.getCount()==before) break;
            remaining-=before-stack.getCount();
        }
        return totalCount-remaining;
    }

    private static int provisionEnchantedGoldenApple(Human human, SpawnFoodConfig config) {
        int requested = config.enchantedAppleCount(human);
        if (requested <= 0) return 0;
        ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, requested);
        // Spawn grants never evict equipment or spill overflow into the world.
        insertInventory(human.getData(), apple);
        return requested - apple.getCount();
    }

    private static int findUsefulDrinkableBuffSlot(Human human) {
        if (human.getPersistentData().getInt(BUFF_COOLDOWN) > 0) return -3;
        if (human.getData() == null) return -3;
        for (int i = -2; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack stack = candidateAt(human, i);
            if (!isBuffPotion(stack) || !canUsePotionNow(human, stack)) continue;
            boolean any = false, duplicate = false;
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
                if (!effect.getEffect().isBeneficial() || effect.getEffect() == MobEffects.HEAL
                        || effect.getEffect() == MobEffects.REGENERATION) continue;
                any = true;
                if (human.hasEffect(effect.getEffect())) duplicate = true;
            }
            if (any && !duplicate) return i;
        }
        return -3;
    }
    private static boolean isBuffPotion(ItemStack stack) {
        return stack.is(Items.POTION) && !isHealingPotion(stack) && PotionUtils.getMobEffects(stack).stream()
                .anyMatch(effect -> effect.getEffect().isBeneficial());
    }
    private static boolean isRegenerationPotion(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getMobEffects(stack).stream()
                .anyMatch(effect -> effect.getEffect() == MobEffects.REGENERATION);
    }
    private static int actualReferences(Human human, ItemStack actual) {
        int references = 0;
        for (EquipmentSlot equipment : EquipmentSlot.values()) if (human.getItemBySlot(equipment) == actual) references++;
        if (human.getData() != null) for (int slot = 0; slot < human.getData().getInventoryItemsSize(); slot++)
            if (human.getData().getInventoryItem(slot) == actual) references++;
        return references;
    }
    private static ItemStack candidateAt(Human human, int slot) {
        return slot == -2 ? human.getMainHandItem() : slot == -1 ? human.getOffhandItem()
                : human.getData().getInventoryItem(slot);
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

    private static void accelerateFoodUse(Human human, ItemStack stack) {
        if (MedicalSupplies.isSupply(stack)) return;
        if (stack.getUseAnimation() != net.minecraft.world.item.UseAnim.EAT && !isInstantHealingPotion(stack)) {
            return;
        }
        int original = Math.max(1, stack.getUseDuration());
        int accelerated = recoveryUseDuration(stack);
        human.advanceUseTime(original - accelerated);
    }

    private static int recoveryUseDuration(ItemStack stack) {
        int medicalDuration = MedicalSupplies.useDuration(stack);
        if (medicalDuration > 0) return medicalDuration;
        return isInstantHealingPotion(stack)
                ? PotionRecoveryPolicy.instantHealingUseDuration(stack.getUseDuration())
                : acceleratedUseDuration(stack.getUseDuration(),
                        stack.getUseAnimation() == net.minecraft.world.item.UseAnim.EAT);
    }

    public static int acceleratedUseDuration(int originalTicks, boolean food) {
        int safeTicks = Math.max(1, originalTicks);
        return food
                ? Math.max(1, (int) Math.ceil(safeTicks * FOOD_USE_TIME_MULTIPLIER))
                : safeTicks;
    }

    public static boolean shouldContinueCombatEating(
            double projectedHealthRatio,
            double targetHealthRatio,
            boolean hasFood
    ) {
        return hasFood && projectedHealthRatio < targetHealthRatio;
    }

    private static int findBestOrdinaryFoodSlot(Human human) {
        if (!needsFood(human) || human.getData() == null) return -3;
        int best = -3; double amount = -1;
        for (int i = -2; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack stack = candidateAt(human, i);
            if (!isOrdinaryFood(human, stack)) continue;
            double heal = Math.min(human.getMaxHealth() - projectedHealth(human), foodHealingValue(human, stack));
            if (heal > amount) { best = i; amount = heal; }
        }
        return best;
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
        for (MobEffectInstance effect : effects) {
            if (effect.getEffect() == MobEffects.HEAL || effect.getEffect() == MobEffects.REGENERATION) return true;
        }
        return false;
    }

    static boolean isInstantHealingPotion(ItemStack stack) {
        return stack.is(Items.POTION) && PotionUtils.getMobEffects(stack).stream()
                .anyMatch(effect -> effect.getEffect() == MobEffects.HEAL);
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

    private static int healingPotionScore(Human human, ItemStack stack) {
        if (MedicalSupplies.isMedicine(stack)) {
            return MedicalSupplies.canUse(human, stack)
                    ? (int) MedicalSupplies.inventoryValue(stack) * 2 : Integer.MIN_VALUE;
        }
        if (!stack.is(Items.POTION) || !canUsePotionNow(human, stack)) return Integer.MIN_VALUE;
        int score = 0;
        boolean healing = false;
        for (MobEffectInstance effect : PotionUtils.getMobEffects(stack)) {
            if (effect.getEffect() == MobEffects.HEAL) {
                healing = true;
                score += 100 + effect.getAmplifier() * 40;
            } else if (effect.getEffect() == MobEffects.REGENERATION) {
                healing = true;
                score += 40 + effect.getAmplifier() * 20 + effect.getDuration() / 20;
            }
        }
        return healing ? score : Integer.MIN_VALUE;
    }

    public static boolean insertInventory(HumanData data, ItemStack source) {
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
