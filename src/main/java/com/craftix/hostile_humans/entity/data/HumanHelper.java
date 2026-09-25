package com.craftix.hostile_humans.entity.data;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class HumanHelper {
    public static CompoundTag saveArmorItems(CompoundTag compoundTag, NonNullList<ItemStack> armor) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < armor.size(); ++i) {
            ItemStack itemStack = (ItemStack)armor.get(i);
            if (itemStack.isEmpty()) continue;
            CompoundTag compoundTagSlot = new CompoundTag();
            compoundTagSlot.putByte("Slot", (byte)i);
            itemStack.save(compoundTagSlot);
            listTag.add(compoundTagSlot);
        }
        if (!listTag.isEmpty()) {
            compoundTag.put("Armor", (Tag)listTag);
        }
        return compoundTag;
    }

    public static void loadArmorItems(CompoundTag compoundTag, NonNullList<ItemStack> armor) {
        HumanHelper.resetNonNullList(armor);
        ListTag listTag = compoundTag.getList("Armor", 10);
        for (int i = 0; i < listTag.size(); ++i) {
            CompoundTag compoundTagSlot = listTag.getCompound(i);
            int index = compoundTagSlot.getByte("Slot") & 0xFF;
            if (index < 0 || index >= armor.size()) continue;
            armor.set(index, ItemStack.of((CompoundTag)compoundTagSlot));
        }
    }

    public static CompoundTag saveInventoryItems(CompoundTag compoundTag, NonNullList<ItemStack> inventory) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack = (ItemStack)inventory.get(i);
            if (itemStack.isEmpty()) continue;
            CompoundTag compoundTagSlot = new CompoundTag();
            compoundTagSlot.putByte("Slot", (byte)i);
            itemStack.save(compoundTagSlot);
            listTag.add(compoundTagSlot);
        }
        if (!listTag.isEmpty()) {
            compoundTag.put("Inventory", (Tag)listTag);
        }
        return compoundTag;
    }

    public static void loadInventoryItems(CompoundTag compoundTag, NonNullList<ItemStack> inventory) {
        HumanHelper.resetNonNullList(inventory);
        ListTag listTag = compoundTag.getList("Inventory", 10);
        for (int i = 0; i < listTag.size(); ++i) {
            CompoundTag compoundTagSlot = listTag.getCompound(i);
            int index = compoundTagSlot.getByte("Slot") & 0xFF;
            if (index < 0 || index >= inventory.size()) continue;
            inventory.set(index, ItemStack.of((CompoundTag)compoundTagSlot));
        }
    }

    public static CompoundTag saveHandItems(CompoundTag compoundTag, NonNullList<ItemStack> hand) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < hand.size(); ++i) {
            ItemStack itemStack = (ItemStack)hand.get(i);
            if (itemStack.isEmpty()) continue;
            CompoundTag compoundTagSlot = new CompoundTag();
            compoundTagSlot.putByte("Slot", (byte)i);
            itemStack.save(compoundTagSlot);
            listTag.add(compoundTagSlot);
        }
        if (!listTag.isEmpty()) {
            compoundTag.put("Hand", (Tag)listTag);
        }
        return compoundTag;
    }

    public static void loadHandItems(CompoundTag compoundTag, NonNullList<ItemStack> hand) {
        HumanHelper.resetNonNullList(hand);
        ListTag listTag = compoundTag.getList("Hand", 10);
        for (int i = 0; i < listTag.size(); ++i) {
            CompoundTag compoundTagSlot = listTag.getCompound(i);
            int index = compoundTagSlot.getByte("Slot") & 0xFF;
            if (index < 0 || index >= hand.size()) continue;
            hand.set(index, ItemStack.of((CompoundTag)compoundTagSlot));
        }
    }

    private static void resetNonNullList(NonNullList<ItemStack> list) {
        for (int index = 0; index < list.size(); ++index) {
            list.set(index, ItemStack.EMPTY);
        }
    }
}

