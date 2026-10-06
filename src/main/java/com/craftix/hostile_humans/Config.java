package com.craftix.hostile_humans;
import club.someoneice.humangunner.UnifiedConfig;
import com.craftix.hostile_humans.entity.entities.SpawnerEntity;
/** Native gameplay reads the unified JSON snapshot. */
public final class Config {
    private Config() {}
    public static String disabledStructures() { return UnifiedConfig.get().disabledStructures(); }
    public static int maxTargeting() { return UnifiedConfig.get().maxTargeting(); }
    public static boolean noWaystones() { return UnifiedConfig.get().noWaystones(); }
    public static SpawnerEntity.SpawnType eventType() { return SpawnerEntity.SpawnType.valueOf(UnifiedConfig.get().battleEvent()); }
}
