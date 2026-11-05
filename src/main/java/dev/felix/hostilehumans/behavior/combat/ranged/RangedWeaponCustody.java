package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CloseDefenseHold;
import dev.felix.hostilehumans.behavior.combat.CloseRangedTactics;
import dev.felix.hostilehumans.behavior.combat.CombatAiConfig;
import dev.felix.hostilehumans.behavior.combat.CombatWeaponPolicy;
import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.behavior.combat.WeaponTransitionPolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatLastStand;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.inventory.HumanEquipmentExchange;
import dev.felix.hostilehumans.inventory.HumanInventoryCustody;
import dev.felix.hostilehumans.inventory.HumanLootManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Owns the exact two-location exchange between a preferred bow/crossbow and a
 * close-range weapon. No temporary empty slot or generic put-away path is used,
 * so a full 30-slot HumanData backpack cannot consume either weapon.
 */
public final class RangedWeaponCustody {
    private static final String PRIMARY_OWNER = "hostile_humans_unified:primary_bow_owner";
    private static final String PRIMARY_ID = "hostile_humans_unified:primary_ranged_identity";
    private static final String ACTIVE = "hostile_humans_unified:ranged_custody_active";
    private static final String RESERVED_SLOT = "hostile_humans_unified:ranged_custody_slot";
    private static final String SNAPSHOT = "hostile_humans_unified:ranged_custody_snapshot";
    private static final double MELEE_ENTER_DISTANCE_SQR = WeaponTransitionPolicy.BOW_ENTER * WeaponTransitionPolicy.BOW_ENTER;
    private static final double RANGED_RETURN_DISTANCE_SQR = WeaponTransitionPolicy.BOW_LEAVE * WeaponTransitionPolicy.BOW_LEAVE;
    private static final java.util.Map<Human, HumanEquipmentExchange.Handle> PRIMARY = new java.util.WeakHashMap<>();

    private RangedWeaponCustody() {
    }

    public static boolean isActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE);
    }

    public static boolean isBowOrCrossbow(ItemStack stack) {
        return CombatWeaponPolicy.isBowOrCrossbow(stack);
    }

    /** Loaded ammunition, a payable reload or a paid reload in progress outranks bows. */
    public static boolean hasGunPriority(Human human) {
        return AmmoWeaponSelection.gunHasPriority(human);
    }

    public static boolean isCrossbowWeapon(ItemStack stack) {
        return CombatWeaponPolicy.isCrossbow(stack);
    }

    public static boolean isBowWeapon(ItemStack stack) {
        return isBowOrCrossbow(stack) && !isCrossbowWeapon(stack);
    }

    public static boolean controlsMainHand(Human human) {
        if (hasGunPriority(human) || !HumanAmmo.hasArrows(human)) {
            return false;
        }
        return isActive(human)
                || isBowOrCrossbow(human.getMainHandItem())
                || hasStoredRangedWeapon(human);
    }

    private static boolean hasStoredRangedWeapon(Human human) {
        HumanData data = human.getData();
        return HumanAmmo.hasArrows(human) && data != null && data.hasStoredBowOrCrossbow(human.tickCount);
    }

    public static void registerPreferred(Human human, ItemStack ranged) {
        if (!isBowOrCrossbow(ranged)) {
            return;
        }
        HumanEquipmentExchange.Handle handle = HumanEquipmentExchange.bind(human, ranged);
        if (handle != null) {
            PRIMARY.put(human, handle);
            human.getPersistentData().putUUID(PRIMARY_ID, handle.identity());
        }
        String owner = human.getStringUUID();
        if (!ranged.hasTag() || !owner.equals(ranged.getTag().getString(PRIMARY_OWNER))) {
            ranged.getOrCreateTag().putString(PRIMARY_OWNER, owner);
        }
    }

    public static void tick(Human human) {
        if (hasGunPriority(human) || !HumanAmmo.hasArrows(human)) {
            RangedTerrainCombat.clear(human);
            clearLease(human);
            return;
        }
        if (!isActive(human) && !isBowOrCrossbow(human.getMainHandItem())
                && !hasStoredRangedWeapon(human)) return;
        if (isBowOrCrossbow(human.getMainHandItem())) {
            registerPreferred(human, human.getMainHandItem());
            if (isActive(human)) {
                // A third-party hand writer may already have completed the
                // restoration. Drop stale lease metadata before evaluating
                // the six-block transition again.
                clearLease(human);
            }
        }

        if (isActive(human) && human.isUsingItem()
                && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            boolean returningToRanged = shouldReturnToRanged(human);
            boolean defenseActive = "defend".equals(human.getPersistentData()
                    .getString("hostile_humans_unified:ai_phase"));
            if (shouldReleaseReturnShield(returningToRanged, defenseActive,
                    human.getTicksUsingItem(), CombatAiConfig.forHuman(human).shieldBlockDurationMaxTicks())) {
                // A lingering off-hand block must not hold the melee lease
                // forever after its target dies or leaves close range.
                human.stopUsingItem();
            }
        }

        if (human.isUsingItem()) {
            // A ranged draw may be cancelled for an urgent close switch. Food,
            // potion and shield leases must finish before main-hand ownership changes.
            if (!isBowOrCrossbow(human.getUseItem())) {
                return;
            }
        }

        if (isActive(human)) {
            if (shouldReturnToRanged(human)) {
                restorePreferred(human, "distance_or_idle");
            }
            return;
        }

        LivingTarget state = targetState(human);
        if (isBowOrCrossbow(human.getMainHandItem())) {
            if (state.valid() && (CloseRangedTactics.keepDefense(human)
                    || state.distanceSqr() <= MELEE_ENTER_DISTANCE_SQR)) {
                beginCloseMelee(human);
            }
            return;
        }

        // A non-gunner which owns a bow or crossbow treats it as its default
        // weapon. At close range it keeps an already-held melee weapon; at six
        // blocks or while idle it returns to ranged mode.
        if (state.valid() && !HumanCombatDirector.strategies(human).keepsRangedDistance()
                && state.distanceSqr() <= MELEE_ENTER_DISTANCE_SQR
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) {
            adoptExistingCloseMelee(human);
        } else if (!state.valid() || state.distanceSqr() > RANGED_RETURN_DISTANCE_SQR
                || HumanCombatDirector.strategies(human).keepsRangedDistance()) {
            restorePreferred(human, "prefer_ranged");
        }
    }

    public static void prepareForManualInventory(Human human) {
        CloseRangedTactics.clear(human);
        RangedTerrainCombat.clear(human);
        CloseDefenseHold.clear(human);
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePreferred(human, "owner_inventory_open")) clearLease(human);
    }

    public static void adoptManualLoadout(Human human) {
        CloseRangedTactics.clear(human);
        RangedTerrainCombat.clear(human);
        CloseDefenseHold.clear(human);
        clearLease(human);
        PRIMARY.remove(human);
        human.getPersistentData().remove(PRIMARY_ID);
        clearPrimaryMarker(human.getMainHandItem());
        clearPrimaryMarker(human.getOffhandItem());
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                clearPrimaryMarker(data.getInventoryItem(i));
            }
        }
        registerPreferred(human, human.getMainHandItem());
    }

    static boolean beginCloseMelee(Human human) {
        if (human.isFleeing || human.getTarget() == null || !human.getTarget().isAlive()
                || human.distanceToSqr(human.getTarget()) > MELEE_ENTER_DISTANCE_SQR) return false;
        if (isActive(human) || hasGunPriority(human) || !HumanAmmo.hasArrows(human)) {
            return isActive(human);
        }
        ItemStack held = human.getMainHandItem();
        if (HumanCombatDirector.mobileCloseDefense(human) && !HumanCombatDirector.mobileMeleeThreat(human)) return false;
        if (!isBowOrCrossbow(held)) {
            return false;
        }
        if (human.isUsingItem()) {
            if (!isBowOrCrossbow(human.getUseItem())) {
                return false;
            }
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        ItemStack melee = HumanLootManager.bestOwnedMeleeWeapon(human);
        if (melee.isEmpty()) {
            return false;
        }

        // No melee fallback means keep drawing/firing; failed preflight must
        // not cancel the bow on every close-range server tick.
        if (human.isUsingItem()) human.stopUsingItem();

        int ownedBefore = countOwnedRanged(human);
        registerPreferred(human, held);
        ItemStack ranged = held;
        var result = HumanEquipmentExchange.request(human, "bow-close-melee",
                java.util.List.of(new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,
                        candidate -> candidate == melee)));
        if (result != HumanEquipmentExchange.Result.SUCCESS) return false;
        human.getPersistentData().putBoolean(ACTIVE, true);
        CloseDefenseHold.begin(human);
        CloseRangedTactics.beginMelee(human);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanRuntime.LOGGER.debug(
                "Ranged custody begin npc={} ranged={} melee={}",
                human.getUUID(), itemId(ranged), itemId(melee)
        );
        assertOwnershipCount(human, ownedBefore, "begin_close_melee");
        return true;
    }

    public static boolean restorePreferred(Human human, String reason) {
        if (!reason.equals("owner_inventory_open") && HumanCombatDirector.mobileMeleeThreat(human)
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) return false;
        if (!reason.equals("owner_inventory_open") && RetreatLastStand.permitsMelee(human)
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) return false;
        boolean keepDistance = HumanCombatDirector.strategies(human).keepsRangedDistance()
                && HumanAmmo.hasArrows(human);
        if (isActive(human) && !keepDistance && human.getTarget() != null && human.getTarget().isAlive()
                && human.distanceToSqr(human.getTarget()) <= RANGED_RETURN_DISTANCE_SQR
                && !reason.equals("owner_inventory_open")) return false;
        if (isActive(human) && ((!keepDistance && CloseRangedTactics.keepDefense(human))
                || human.hhu$shieldDefense().shouldSuppressRecovery())) return false;
        if (isActive(human) && CloseDefenseHold.shouldHold(human)) return false;
        if (hasGunPriority(human) || !HumanAmmo.hasArrows(human) || human.isUsingItem()) {
            return false;
        }
        if (isBowOrCrossbow(human.getMainHandItem())) {
            registerPreferred(human, human.getMainHandItem());
            if (isActive(human)) CloseRangedTactics.returnedToBow(human);
            clearLease(human);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            return true;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        if (!isActive(human) && !data.hasStoredBowOrCrossbow(human.tickCount)) return false;
        int rangedSlot = findBestStoredRangedSlot(human);
        if (rangedSlot >= 0) {
            if (!AmmoWeaponSelection.mayRestore(human, data.getInventoryItem(rangedSlot))) {
                clearLease(human); return false;
            }
            int ownedBefore = countOwnedRanged(human);
            ItemStack ranged = data.getInventoryItem(rangedSlot);
            ItemStack displaced = human.getMainHandItem();
            var handle = PRIMARY.get(human);
            if (handle == null) { registerPreferred(human, ranged); handle = PRIMARY.get(human); }
            if (handle == null) return false;
            var result = HumanEquipmentExchange.request(human, "bow-return",
                    java.util.List.of(new HumanEquipmentExchange.Need(EquipmentSlot.MAINHAND,
                            RangedWeaponCustody::isBowOrCrossbow, handle)));
            if (result != HumanEquipmentExchange.Result.SUCCESS) return false;
            if (isActive(human)) CloseRangedTactics.returnedToBow(human);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            HumanRuntime.LOGGER.debug(
                    "Ranged custody restore npc={} reason={} slot={} ranged={} displaced={}",
                    human.getUUID(), reason, rangedSlot, itemId(ranged), itemId(displaced)
            );
            clearLease(human);
            assertOwnershipCount(human, ownedBefore, "restore_preferred");
            return true;
        }

        // A stale lease snapshot must never manufacture a missing weapon.
        clearLease(human);
        return false;
    }

    private static void adoptExistingCloseMelee(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        int rangedSlot = findBestStoredRangedSlot(human);
        if (rangedSlot < 0) {
            return;
        }
        ItemStack ranged = data.getInventoryItem(rangedSlot);
        registerPreferred(human, ranged);
        data.setInventoryItem(rangedSlot, ranged);
        CompoundTag ownerData = human.getPersistentData();
        ownerData.putBoolean(ACTIVE, true);
        ownerData.putInt(RESERVED_SLOT, rangedSlot);

        CloseDefenseHold.begin(human);
        CloseRangedTactics.beginMelee(human);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanRuntime.LOGGER.debug(
                "Ranged custody adopted close melee npc={} slot={} ranged={} melee={}",
                human.getUUID(), rangedSlot, itemId(ranged), itemId(human.getMainHandItem())
        );
    }

    private static boolean shouldReturnToRanged(Human human) {
        LivingTarget state = targetState(human);
        if (HumanCombatDirector.mobileCloseDefense(human) && HumanAmmo.hasArrows(human))
            return !HumanCombatDirector.mobileMeleeThreat(human);
        if (RangedTerrainCombat.holdsMelee(human)) return false;
        if (state.valid() && CloseRangedTactics.keepDefense(human)) return false;
        return !state.valid() || state.distanceSqr() > RANGED_RETURN_DISTANCE_SQR;
    }

    static boolean shouldReleaseReturnShield(boolean returningToRanged, boolean defenseActive,
                                             int shieldUseTicks, int configuredMaxBlockTicks) {
        return returningToRanged && (!defenseActive
                || shieldUseTicks > configuredMaxBlockTicks + 20);
    }

    private static LivingTarget targetState(Human human) {
        if (human.getTarget() == null || !human.getTarget().isAlive()) {
            return new LivingTarget(false, Double.POSITIVE_INFINITY);
        }
        return new LivingTarget(true, human.distanceToSqr(human.getTarget()));
    }

    /** Read-only preview of the same weapon restorePreferred will choose. */
    public static ItemStack preferredForClearance(Human human) {
        if (HumanCombatDirector.mobileCloseDefense(human) && GunCustody.isCloseDefenseActive(human)) {
            ItemStack gun = GunCustody.primary(human);
            if (!gun.isEmpty()) return gun;
        }
        if (isBowOrCrossbow(human.getMainHandItem()) || GunSupport.isGunItem(human.getMainHandItem())
                || !isActive(human)) return human.getMainHandItem();
        int slot = findBestStoredRangedSlot(human);
        return slot >= 0 ? human.getData().getInventoryItem(slot) : human.getMainHandItem();
    }

    private static int findBestStoredRangedSlot(Human human) {
        HumanData data = human.getData();
        if (data == null) return -1;
        var handle = PRIMARY.get(human);
        if (handle == null && human.getPersistentData().hasUUID(PRIMARY_ID)) {
            handle = new HumanEquipmentExchange.Handle(human.getPersistentData().getUUID(PRIMARY_ID), human.getUUID());
            PRIMARY.put(human, handle);
        }
        if (handle != null) {
            ItemStack actual = HumanEquipmentExchange.locate(human, handle);
            if (actual.isEmpty()) {
                PRIMARY.remove(human); human.getPersistentData().remove(PRIMARY_ID);
                return -1; // Fail this transaction; a later fresh selection may choose another real weapon.
            }
            for (int i = 0; i < data.getInventoryItemsSize(); i++) if (data.getInventoryItem(i) == actual) return i;
            return -1;
        }
        String owner = human.getStringUUID();
        int ownedSlot = -1;
        double ownedScore = Double.NEGATIVE_INFINITY;
        int[] alternatives = new int[data.getInventoryItemsSize()];
        int alternativeCount = 0;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            if (HumanInventoryCustody.isReservedForRecovery(data, i)) continue;
            ItemStack stack = data.getInventoryItem(i);
            if (!isBowOrCrossbow(stack)) {
                continue;
            }
            boolean owned = stack.hasTag() && owner.equals(stack.getTag().getString(PRIMARY_OWNER));
            if (!owned) {
                alternatives[alternativeCount++] = i;
                continue;
            }
            double score = rangedScore(stack);
            if (score > ownedScore) {
                ownedScore = score;
                ownedSlot = i;
            }
        }
        if (ownedSlot >= 0) return ownedSlot;
        int otherSlot = -1;
        double otherScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < alternativeCount; i++) {
            int slot = alternatives[i];
            double score = rangedScore(data.getInventoryItem(slot));
            if (score > otherScore) {
                otherScore = score;
                otherSlot = slot;
            }
        }
        return otherSlot;
    }

    private static double rangedScore(ItemStack stack) {
        double score = stack.getItem() instanceof CrossbowItem ? 12.0D : 10.0D;
        for (int level : EnchantmentHelper.getEnchantments(stack).values()) score += level * 2.0D;
        if (stack.isDamageableItem()) {
            score += 5.0D * (1.0D - (double) stack.getDamageValue() / stack.getMaxDamage());
        }
        if (SpartanEquipmentCompat.isSpartanRangedWeapon(stack)) {
            score += 2.0D;
        }
        return score;
    }

    private static void clearLease(Human human) {
        if (isActive(human) && !CloseDefenseHold.shouldHold(human)) CloseDefenseHold.clear(human);
        CompoundTag data = human.getPersistentData();
        data.remove(ACTIVE);
        data.remove(RESERVED_SLOT);
        data.remove(SNAPSHOT);
    }

    private static void clearPrimaryMarker(ItemStack stack) {
        if (stack.hasTag()) stack.getTag().remove(PRIMARY_OWNER);
    }

    private static int countOwnedRanged(Human human) {
        int count = isBowOrCrossbow(human.getMainHandItem()) ? 1 : 0;
        if (isBowOrCrossbow(human.getOffhandItem())) {
            count++;
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                if (isBowOrCrossbow(data.getInventoryItem(i))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void assertOwnershipCount(Human human, int expected, String operation) {
        int actual = countOwnedRanged(human);
        if (actual != expected) {
            HumanRuntime.LOGGER.error(
                    "Ranged custody ownership invariant failed npc={} operation={} before={} after={}",
                    human.getUUID(), operation, expected, actual
            );
        }
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "empty"
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private record LivingTarget(boolean valid, double distanceSqr) {
    }
}
