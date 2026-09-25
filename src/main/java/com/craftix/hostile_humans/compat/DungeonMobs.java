package com.craftix.hostile_humans.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** Registry-only adapter: optional mods never participate in class linkage. */
public final class DungeonMobs {
    public static EntityType<?> getRedstoneGolem() {
        return ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("dungeons_mobs", "redstone_golem"));
    }
}

