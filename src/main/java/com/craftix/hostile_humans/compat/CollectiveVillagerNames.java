package com.craftix.hostile_humans.compat;
import com.craftix.hostile_humans.HostileHumans;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Legacy optional naming bridge; local names avoid unsupported third-party internals. */
public final class CollectiveVillagerNames {
    public static void nameEntity(Entity entity) {
        var names = HostileHumans.patreonNames;
        if (!names.isEmpty()) entity.setCustomName(Component.literal(names.get(entity.level().random.nextInt(names.size()))));
    }
    public static void addCustomName(String name) {
        // Supporter names are owned by the local immutable name catalog.
    }
}

