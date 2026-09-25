package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Owns the exact two-location exchange between a preferred bow/crossbow and a
 * close-range weapon. No temporary empty slot or generic put-away path is used,
 * so a full 30-slot HumanData backpack cannot consume either weapon.
 */
public final class RangedWeaponCustody {
    private static final String PRIMARY_OWNER = "humangunner:primary_bow_owner";
    private static final String ACTIVE = "humangunner:ranged_custody_active";
    private static final String RESERVED_SLOT = "humangunner:ranged_custody_slot";
    private static final String SNAPSHOT = "humangunner:ranged_custody_snapshot";
    private static final double MELEE_ENTER_DISTANCE_SQR = 16.0D;
    private static final double RANGED_RETURN_DISTANCE_SQR = 36.0D;

    private RangedWeaponCustody() {
    }

    public static boolean isActive(Human human) {
        return human.getPersistentData().getBoolean(ACTIVE);
    }

    public static boolean isBowOrCrossbow(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || SpartanEquipmentCompat.isSpartanRangedWeapon(stack));
    }

    /** Gun ownership always outranks the bow/crossbow loadout, even while the gun is stored. */
    public static boolean hasGunPriority(Human human) {
        return GunCustody.hasOwnedGun(human);
    }

    public static boolean isCrossbowWeapon(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof CrossbowItem
                || SpartanRangedCompat.isHeavyCrossbow(stack));
    }

    public static boolean isBowWeapon(ItemStack stack) {
        return isBowOrCrossbow(stack) && !isCrossbowWeapon(stack);
    }

    static boolean controlsMainHand(Human human) {
        if (GunCustody.hasOwnedGun(human)) {
            return false;
        }
        return isActive(human)
                || isBowOrCrossbow(human.getMainHandItem())
                || findBestStoredRangedSlot(human, true) >= 0
                || findBestStoredRangedSlot(human, false) >= 0;
    }

    static void registerPreferred(Human human, ItemStack ranged) {
        if (!isBowOrCrossbow(ranged)) {
            return;
        }
        ranged.getOrCreateTag().putString(PRIMARY_OWNER, human.getUUID().toString());
    }

    static void tick(Human human) {
        if (GunCustody.hasOwnedGun(human)) {
            clearLease(human);
            return;
        }
        if (isBowOrCrossbow(human.getMainHandItem())) {
            registerPreferred(human, human.getMainHandItem());
            if (isActive(human)) {
                // A third-party hand writer may already have completed the
                // restoration. Drop stale lease metadata before evaluating
                // the five-block transition again.
                clearLease(human);
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
            if (state.valid() && state.distanceSqr() <= MELEE_ENTER_DISTANCE_SQR) {
                beginCloseMelee(human);
            }
            return;
        }

        // A non-gunner which owns a bow or crossbow treats it as its default
        // weapon. At close range it keeps an already-held melee weapon; at six
        // blocks or while idle it returns to ranged mode.
        if (state.valid()
                && state.distanceSqr() <= MELEE_ENTER_DISTANCE_SQR
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())) {
            adoptExistingCloseMelee(human);
        } else if (!state.valid() || state.distanceSqr() >= RANGED_RETURN_DISTANCE_SQR) {
            restorePreferred(human, "prefer_ranged");
        }
    }

    static void prepareForManualInventory(Human human) {
        if (!isActive(human)) return;
        if (human.isUsingItem()) human.stopUsingItem();
        if (!restorePreferred(human, "owner_inventory_open")) clearLease(human);
    }

    static void adoptManualLoadout(Human human) {
        clearLease(human);
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
        if (isActive(human) || GunCustody.hasOwnedGun(human)) {
            return isActive(human);
        }
        ItemStack held = human.getMainHandItem();
        if (!isBowOrCrossbow(held)) {
            return false;
        }
        if (human.isUsingItem()) {
            if (!isBowOrCrossbow(human.getUseItem())) {
                return false;
            }
            human.stopUsingItem();
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        int meleeSlot = findBestMeleeSlot(data);
        if (meleeSlot < 0) {
            return false;
        }

        int ownedBefore = countOwnedRanged(human);
        registerPreferred(human, held);
        ItemStack ranged = held.copy();
        ItemStack melee = data.getInventoryItem(meleeSlot).copy();
        CompoundTag ownerData = human.getPersistentData();
        ownerData.putBoolean(ACTIVE, true);
        ownerData.putInt(RESERVED_SLOT, meleeSlot);
        ownerData.put(SNAPSHOT, ranged.save(new CompoundTag()));

        // Publish the ranged weapon into the occupied melee slot first. The
        // following hand write completes an exact swap and cannot depend on capacity.
        data.setInventoryItem(meleeSlot, ranged);
        human.setItemSlot(EquipmentSlot.MAINHAND, melee);
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanGunner.LOGGER.debug(
                "Ranged custody begin npc={} slot={} ranged={} melee={}",
                human.getUUID(), meleeSlot, itemId(ranged), itemId(melee)
        );
        assertOwnershipCount(human, ownedBefore, "begin_close_melee");
        return true;
    }

    static boolean restorePreferred(Human human, String reason) {
        if (GunCustody.hasOwnedGun(human) || human.isUsingItem()) {
            return false;
        }
        if (isBowOrCrossbow(human.getMainHandItem())) {
            registerPreferred(human, human.getMainHandItem());
            clearLease(human);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            return true;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        int rangedSlot = findBestStoredRangedSlot(human, true);
        if (rangedSlot < 0) {
            rangedSlot = findBestStoredRangedSlot(human, false);
        }
        if (rangedSlot >= 0) {
            int ownedBefore = countOwnedRanged(human);
            ItemStack ranged = data.getInventoryItem(rangedSlot).copy();
            registerPreferred(human, ranged);
            ItemStack displaced = human.getMainHandItem().copy();
            // Write the high-value weapon to the hand before replacing its
            // backpack slot. An intervening callback can at worst see a duplicate.
            human.setItemSlot(EquipmentSlot.MAINHAND, ranged);
            data.setInventoryItem(rangedSlot, displaced);
            human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
            HumanGunner.LOGGER.debug(
                    "Ranged custody restore npc={} reason={} slot={} ranged={} displaced={}",
                    human.getUUID(), reason, rangedSlot, itemId(ranged), itemId(displaced)
            );
            clearLease(human);
            assertOwnershipCount(human, ownedBefore, "restore_preferred");
            return true;
        }

        CompoundTag ownerData = human.getPersistentData();
        if (!ownerData.getBoolean(ACTIVE) || !ownerData.contains(SNAPSHOT)) {
            return false;
        }
        ItemStack recovered = ItemStack.of(ownerData.getCompound(SNAPSHOT));
        if (!isBowOrCrossbow(recovered)) {
            clearLease(human);
            return false;
        }
        ItemStack displaced = human.getMainHandItem().copy();
        if (!displaced.isEmpty()) {
            ItemStack moving = displaced.copy();
            HumanLootManager.storeWithEviction(human, moving);
            if (!moving.isEmpty()) {
                human.spawnAtLocation(moving);
            }
        }
        registerPreferred(human, recovered);
        human.setItemSlot(EquipmentSlot.MAINHAND, recovered);
        human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        HumanGunner.LOGGER.error(
                "Reconstructed missing preferred ranged weapon npc={} reason={} ranged={}",
                human.getUUID(), reason, itemId(recovered)
        );
        clearLease(human);
        return true;
    }

    private static void adoptExistingCloseMelee(Human human) {
        HumanData data = human.getData();
        if (data == null) {
            return;
        }
        int rangedSlot = findBestStoredRangedSlot(human, true);
        if (rangedSlot < 0) {
            rangedSlot = findBestStoredRangedSlot(human, false);
        }
        if (rangedSlot < 0) {
            return;
        }
        ItemStack ranged = data.getInventoryItem(rangedSlot);
        registerPreferred(human, ranged);
        data.setInventoryItem(rangedSlot, ranged);
        CompoundTag ownerData = human.getPersistentData();
        ownerData.putBoolean(ACTIVE, true);
        ownerData.putInt(RESERVED_SLOT, rangedSlot);
        ownerData.put(SNAPSHOT, ranged.copy().save(new CompoundTag()));
        human.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        HumanGunner.LOGGER.debug(
                "Ranged custody adopted close melee npc={} slot={} ranged={} melee={}",
                human.getUUID(), rangedSlot, itemId(ranged), itemId(human.getMainHandItem())
        );
    }

    private static boolean shouldReturnToRanged(Human human) {
        LivingTarget state = targetState(human);
        return !state.valid() || state.distanceSqr() >= RANGED_RETURN_DISTANCE_SQR;
    }

    private static LivingTarget targetState(Human human) {
        if (human.getTarget() == null || !human.getTarget().isAlive()) {
            return new LivingTarget(false, Double.POSITIVE_INFINITY);
        }
        return new LivingTarget(true, human.distanceToSqr(human.getTarget()));
    }

    private static int findBestMeleeSlot(HumanData data) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!HumanLootManager.isDedicatedMeleeWeapon(stack)) {
                continue;
            }
            double score = HumanLootManager.mainHandWeaponScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static int findBestStoredRangedSlot(Human human, boolean requireOwner) {
        HumanData data = human.getData();
        if (data == null) {
            return -1;
        }
        String owner = human.getUUID().toString();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.getInventoryItemsSize(); i++) {
            ItemStack stack = data.getInventoryItem(i);
            if (!isBowOrCrossbow(stack)) {
                continue;
            }
            boolean owned = stack.hasTag() && owner.equals(stack.getTag().getString(PRIMARY_OWNER));
            if (requireOwner != owned) {
                continue;
            }
            double score = rangedScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static double rangedScore(ItemStack stack) {
        double score = stack.getItem() instanceof CrossbowItem ? 12.0D : 10.0D;
        score += EnchantmentHelper.getEnchantments(stack).values().stream()
                .mapToInt(Integer::intValue).sum() * 2.0D;
        if (stack.isDamageableItem()) {
            score += 5.0D * (1.0D - (double) stack.getDamageValue() / stack.getMaxDamage());
        }
        if (SpartanEquipmentCompat.isSpartanRangedWeapon(stack)) {
            score += 2.0D;
        }
        return score;
    }

    private static void clearLease(Human human) {
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
            HumanGunner.LOGGER.error(
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
