package com.craftix.hostile_humans.entity.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Shared inventory codec. Existing save keys and unsigned byte slot IDs are preserved. */
public final class HumanHelper {
    private HumanHelper() { }

    public static CompoundTag saveArmorItems(CompoundTag tag, NonNullList<ItemStack> items) {
        return saveItems(tag, "Armor", items);
    }
    public static CompoundTag saveInventoryItems(CompoundTag tag, NonNullList<ItemStack> items) {
        return saveItems(tag, "Inventory", items);
    }
    public static CompoundTag saveHandItems(CompoundTag tag, NonNullList<ItemStack> items) {
        return saveItems(tag, "Hand", items);
    }
    public static void loadArmorItems(CompoundTag tag, NonNullList<ItemStack> items) {
        loadItems(tag, "Armor", items);
    }
    public static void loadInventoryItems(CompoundTag tag, NonNullList<ItemStack> items) {
        loadItems(tag, "Inventory", items);
    }
    public static void loadHandItems(CompoundTag tag, NonNullList<ItemStack> items) {
        loadItems(tag, "Hand", items);
    }

    private static CompoundTag saveItems(CompoundTag tag, String key, NonNullList<ItemStack> items) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) slot);
            stack.save(entry);
            list.add(entry);
        }
        // A reused tag must not retain yesterday's equipment after all slots
        // were emptied, or the next load would resurrect removed/broken items.
        if (list.isEmpty()) tag.remove(key);
        else tag.put(key, list);
        return tag;
    }

    private static void loadItems(CompoundTag tag, String key, NonNullList<ItemStack> items) {
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot") & 0xFF;
            if (slot < items.size()) items.set(slot, ItemStack.of(entry));
        }
    }
}

