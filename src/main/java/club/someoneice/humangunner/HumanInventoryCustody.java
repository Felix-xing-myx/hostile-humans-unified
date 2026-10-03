package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Lossless backpack storage and atomic, single-slot equipment exchanges. */
public final class HumanInventoryCustody {
    private HumanInventoryCustody() {
    }

    /** Store the complete supplied stack, or materialise its remainder in-world. */
    public static boolean stowOrDrop(Human human, ItemStack source) {
        if (source.isEmpty()) return true;
        if (human.level().isClientSide) return false;
        HumanData data = human.getData();
        if (data != null && storePickupTransactionally(data, source)) {
            source.setCount(0);
            return true;
        }

        // Commit either complete storage or a complete world drop. If another
        // mod cancels item insertion, keep the source and let the caller abort
        // replacing its equipped copy; never leave a partial backpack commit.
        ItemStack remainder = source.copy();
        ItemEntity dropped = human.spawnAtLocation(remainder);
        if (dropped != null) {
            dropped.setPickUpDelay(80);
            source.setCount(0);
            return true;
        }
        return false;
    }

    /** Atomically exchange only the requested equipment slot. */
    public static boolean swapRequestedSlot(
            Human human,
            Predicate<ItemStack> predicate,
            EquipmentSlot requestedSlot
    ) {
        if (human.level().isClientSide) {
            return false;
        }
        HumanData data = human.getData();
        if (data == null) {
            return false;
        }
        // HumanData currently owns 30 backpack slots. Always trust the live
        // container size instead of the base mod's historical 0-15 combat
        // window, otherwise equipment in slots 16-29 exists and is saved but
        // can never be selected again.
        int candidateCount = data.getInventoryItemsSize();
        for (int index = 0; index < candidateCount; index++) {
            ItemStack candidate = data.getInventoryItem(index);
            if (candidate.isEmpty() || !predicate.test(candidate)) {
                continue;
            }

            ItemStack previous = human.getItemBySlot(requestedSlot).copy();
            ItemStack replacement = candidate.copy();
            // The inventory slot is the transaction's guaranteed destination;
            // no capacity check and no second hand mutation are involved.
            data.setInventoryItem(index, previous);
            human.setItemSlot(requestedSlot, replacement);
            return true;
        }
        return false;
    }

    /** All-or-nothing pickup storage across the complete live backpack. */
    public static boolean storePickupTransactionally(HumanData data, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return false;
        }
        int end = data.getInventoryItemsSize();
        int start = 0;
        long capacity = 0;
        for (int index = start; index < end; index++) {
            ItemStack stored = data.getInventoryItem(index);
            if (stored.isEmpty()) {
                capacity += incoming.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(stored, incoming)) {
                capacity += Math.max(0, stored.getMaxStackSize() - stored.getCount());
            }
            if (capacity >= incoming.getCount()) {
                break;
            }
        }
        if (capacity < incoming.getCount()) {
            return false;
        }

        ItemStack moving = incoming.copy();
        mergeAndFill(data, moving, start, end);
        return moving.isEmpty();
    }

    private static void mergeAndFill(HumanData data, ItemStack source, int start, int end) {
        for (int index = start; index < end && !source.isEmpty(); index++) {
            ItemStack stored = data.getInventoryItem(index);
            if (stored.isEmpty()
                    || !ItemStack.isSameItemSameTags(stored, source)
                    || stored.getCount() >= stored.getMaxStackSize()) {
                continue;
            }
            int moved = Math.min(source.getCount(), stored.getMaxStackSize() - stored.getCount());
            ItemStack merged = stored.copy();
            merged.grow(moved);
            source.shrink(moved);
            data.setInventoryItem(index, merged);
        }
        for (int index = start; index < end && !source.isEmpty(); index++) {
            if (!data.getInventoryItem(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(source.getCount(), source.getMaxStackSize());
            data.setInventoryItem(index, source.copyWithCount(moved));
            source.shrink(moved);
        }
    }
}
