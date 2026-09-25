package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Keeps only an owner's explicit armour choices authoritative after menu edits. */
final class HumanManagedLoadout {
    private static final String LEGACY_MANAGED = HumanGunner.MOD_ID + ":owner_managed_loadout";
    private static final String ARMOUR_LOCK_PREFIX = HumanGunner.MOD_ID + ":owner_armour_lock_";

    private HumanManagedLoadout() {}

    static void recordManualEquipmentChange(Human human, EquipmentSlot slot, ItemStack stack) {
        if (!isArmourSlot(slot)) return;
        String key = armourLockKey(slot);
        if (stack.isEmpty()) human.getPersistentData().remove(key);
        else human.getPersistentData().putBoolean(key, true);
    }

    static boolean isArmourLocked(Human human, EquipmentSlot slot) {
        if (!isArmourSlot(slot)) return false;
        migrateLegacyLock(human);
        String key = armourLockKey(slot);
        if (human.getItemBySlot(slot).isEmpty()) {
            // A broken piece is removed by vanilla equipment durability. Its
            // slot immediately becomes eligible for a stored replacement.
            human.getPersistentData().remove(key);
            return false;
        }
        return human.getPersistentData().getBoolean(key);
    }

    static void clear(Human human) {
        human.getPersistentData().remove(LEGACY_MANAGED);
        for (EquipmentSlot slot : armourSlots()) {
            human.getPersistentData().remove(armourLockKey(slot));
        }
    }

    static void reconcile(Human human) {
        migrateLegacyLock(human);
        if (human.isUsingItem()) human.stopUsingItem();
        GunCustody.adoptManualLoadout(human);
        RangedWeaponCustody.adoptManualLoadout(human);
        equipStoredMeleeIfMainHandEmpty(human);
    }

    /**
     * Preserve every explicit non-empty main-hand choice. If the owner removed
     * a gun without replacing it, however, an existing backpack melee weapon
     * must become usable instead of leaving the gunner to punch empty-handed.
     */
    static boolean equipStoredMeleeIfMainHandEmpty(Human human) {
        if (!human.getMainHandItem().isEmpty()) return false;
        HumanData data = human.getData();
        if (data == null) return false;

        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < data.getInventoryItemsSize(); slot++) {
            ItemStack candidate = data.getInventoryItem(slot);
            if (!HumanLootManager.isDedicatedMeleeWeapon(candidate)) continue;
            double score = HumanLootManager.mainHandWeaponScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return false;

        ItemStack melee = data.getInventoryItem(bestSlot).copy();
        // Publish the hand copy before clearing the backpack slot. A synchronous
        // equipment callback can therefore see a duplicate, never a lost item.
        human.setItemSlot(EquipmentSlot.MAINHAND, melee);
        data.setInventoryItem(bestSlot, ItemStack.EMPTY);
        human.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        HumanGunner.LOGGER.debug(
                "Equipped owner-managed melee fallback npc={} slot={} item={}",
                human.getUUID(), bestSlot,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(melee.getItem())
        );
        return true;
    }

    private static void migrateLegacyLock(Human human) {
        if (!human.getPersistentData().getBoolean(LEGACY_MANAGED)) return;
        // Old versions stored one global lock and therefore cannot identify
        // which armour slot was edited. Preserve every currently worn piece,
        // while intentionally releasing both hands under the new policy.
        for (EquipmentSlot slot : armourSlots()) {
            if (!human.getItemBySlot(slot).isEmpty()) {
                human.getPersistentData().putBoolean(armourLockKey(slot), true);
            }
        }
        human.getPersistentData().remove(LEGACY_MANAGED);
    }

    private static boolean isArmourSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static EquipmentSlot[] armourSlots() {
        return new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
    }

    private static String armourLockKey(EquipmentSlot slot) {
        return ARMOUR_LOCK_PREFIX + slot.getName();
    }
}
