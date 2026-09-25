package com.craftix.hostile_humans.item;

import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"hostile_humans");
    public static final RegistryObject<Item> HUMAN1_SPAWN_EGG = ITEMS.register("human_tier1_spawn_egg", () -> new ForgeSpawnEggItem(ModEntityType.HUMAN1, MapColor.COLOR_ORANGE.col, MapColor.TERRACOTTA_WHITE.col, new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HUMAN2_SPAWN_EGG = ITEMS.register("human_tier2_spawn_egg", () -> new ForgeSpawnEggItem(ModEntityType.HUMAN2, MapColor.COLOR_ORANGE.col, MapColor.GOLD.col, new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HUMAN3_SPAWN_EGG = ITEMS.register("human_tier3_spawn_egg", () -> new ForgeSpawnEggItem(ModEntityType.HUMAN3, 0x24304A, 0xB88A2B, new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ROAMER_SPAWN_EGG = ITEMS.register("human_roamer_spawn_egg", () -> new ForgeSpawnEggItem(ModEntityType.ROAMER, MapColor.COLOR_ORANGE.col, MapColor.COLOR_BLACK.col, new Item.Properties().rarity(Rarity.EPIC)));
}

