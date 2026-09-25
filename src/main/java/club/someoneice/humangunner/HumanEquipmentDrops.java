package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Exact per-owned-item death rolls, independent of the base mod's drop path:
 * guns 1% (configurable), equipment 5%, and all other carried supplies 20%.
 */
final class HumanEquipmentDrops {
    private static final double EQUIPMENT_DROP_CHANCE = 0.05D;
    private static final double SUPPLY_DROP_CHANCE = 0.20D;
    private static final Map<Human, List<ItemStack>> PRE_DEATH_SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private HumanEquipmentDrops() {
    }

    /** Capture ownership before the base mod mutates and clears equipment. */
    static void captureBeforeDeath(Human human) {
        PRE_DEATH_SNAPSHOTS.put(human, collectOwned(human));
        // The entity is committed to death at this point (totem cancellation
        // has already returned). Suppress the base equipment-roll producer as
        // a first line of defence; LivingDrops cleanup below is still required
        // for other contributors and older base paths. A strongly negative
        // chance also remains impossible under Looting adjustments.
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            human.setDropChance(slot, -100.0F);
        }
    }

    static void rebuildOwnedDrops(
            Human human, Collection<ItemEntity> drops, double gunDropChance
    ) {
        List<ItemStack> owned = PRE_DEATH_SNAPSHOTS.remove(human);
        if (owned == null) {
            // Defensive fallback for non-standard death paths.
            owned = collectOwned(human);
        }

        // Hostile Humans changes durability, spawns each equipped stack and
        // clears the slot before LivingDropsEvent. Exact NBT comparison alone
        // therefore leaves the mandatory copy behind. Controlled equipment is
        // matched by item identity; ordinary supplies keep strict NBT matching.
        List<ItemStack> ownedSnapshot = owned;
        drops.removeIf(drop -> matchesOwnedDrop(drop.getItem(), ownedSnapshot));

        for (ItemStack stack : owned) {
            double chance = dropChance(stack, gunDropChance);
            int kept = 0;
            for (int unit = 0; unit < stack.getCount(); unit++) {
                if (human.getRandom().nextDouble() < chance) {
                    kept++;
                }
            }
            if (kept <= 0) {
                continue;
            }
            ItemStack result = stack.copy();
            result.setCount(kept);
            ItemEntity drop = new ItemEntity(
                    human.level(), human.getX(), human.getY() + 0.35D, human.getZ(), result
            );
            drop.setDefaultPickUpDelay();
            drops.add(drop);
        }
    }

    private static List<ItemStack> collectOwned(Human human) {
        List<ItemStack> owned = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = human.getItemBySlot(slot);
            if (!equipped.isEmpty()) {
                owned.add(equipped.copy());
            }
        }
        HumanData data = human.getData();
        if (data != null) {
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack stored = data.getInventoryItem(i);
                if (!stored.isEmpty()) {
                    owned.add(stored.copy());
                }
            }
        }
        return owned;
    }

    private static boolean matchesOwnedDrop(ItemStack candidate, List<ItemStack> owned) {
        boolean controlledCandidate = isControlledEquipment(candidate);
        for (ItemStack stack : owned) {
            if (ItemStack.isSameItemSameTags(candidate, stack)) {
                return true;
            }
            if (controlledCandidate && isControlledEquipment(stack)
                    && (candidate.getItem() == stack.getItem()
                    || (club.someoneice.humangunner.GunSupport.get().isGun(candidate)
                    && club.someoneice.humangunner.GunSupport.get().isGun(stack)))) {
                return true;
            }
        }
        return false;
    }

    private static double dropChance(ItemStack stack, double gunDropChance) {
        if (club.someoneice.humangunner.GunSupport.get().isGun(stack)) {
            return gunDropChance;
        }
        return isControlledEquipment(stack) ? EQUIPMENT_DROP_CHANCE : SUPPLY_DROP_CHANCE;
    }

    private static boolean isControlledEquipment(ItemStack stack) {
        return !stack.isEmpty()
                && (club.someoneice.humangunner.GunSupport.get().isGun(stack)
                || stack.getItem() instanceof ArmorItem
                || SpartanEquipmentCompat.isShield(stack)
                || HumanLootManager.isNonGunWeapon(stack));
    }
}
