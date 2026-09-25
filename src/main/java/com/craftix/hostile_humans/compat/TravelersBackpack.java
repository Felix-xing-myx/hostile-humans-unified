package com.craftix.hostile_humans.compat;

import com.craftix.hostile_humans.entity.entities.ModEntityType;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public class TravelersBackpack {
    private static final String BACK_SLOT = "back";

    public static void apply(LivingEntity living) {
        if (living.getRandom().nextInt(0, 3) != 0) {
            return;
        }
        CuriosApi.getCuriosInventory((LivingEntity)living).ifPresent(curiosInventory -> curiosInventory.getStacksHandler(BACK_SLOT).ifPresent(backSlot -> TravelersBackpack.equipBackpack(curiosInventory, backSlot, living)));
    }

    private static void equipBackpack(ICuriosItemHandler curiosInventory, ICurioStacksHandler backSlot, LivingEntity living) {
        if (backSlot.getStacks().getSlots() <= 0 || !backSlot.getStacks().getStackInSlot(0).isEmpty()) {
            return;
        }
        ItemStack backpack = TravelersBackpack.pickBackpack(living);
        if (backpack.isEmpty()) {
            return;
        }
        backpack.getOrCreateTag().putInt("SleepingBagColor", DyeColor.values()[living.getRandom().nextInt(DyeColor.values().length)].getId());
        curiosInventory.setEquippedCurio(BACK_SLOT, 0, backpack);
    }

    private static ItemStack pickBackpack(LivingEntity living) {
        List<Item> entries = net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries().stream()
                .filter(e -> e.getKey().location().getNamespace().equals("travelersbackpack"))
                .filter(e -> e.getKey().location().getPath().endsWith("_travelers_backpack"))
                .map(java.util.Map.Entry::getValue).toList();
        if (entries.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack standardBackpack = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("travelersbackpack", "standard")).getDefaultInstance();
        for (int attempt = 0; attempt < 8; ++attempt) {
            ItemStack backpack = ((Item)entries.get(living.getRandom().nextInt(entries.size()))).getDefaultInstance();
            String itemPath = TravelersBackpack.getItemPath(backpack);
            if (itemPath.contains("end") || living.getType() != ModEntityType.HUMAN2.get() && (itemPath.contains("netherite") || itemPath.contains("diamond") || itemPath.contains("gold") || itemPath.contains("emerald"))) continue;
            if (!standardBackpack.isEmpty() && living.getRandom().nextFloat() < 0.5f) {
                backpack = standardBackpack.copy();
            }
            return backpack;
        }
        return standardBackpack.isEmpty() ? ItemStack.EMPTY : standardBackpack.copy();
    }

    private static String getItemPath(ItemStack stack) {
        return stack.getItem().builtInRegistryHolder().key().location().getPath();
    }
}

