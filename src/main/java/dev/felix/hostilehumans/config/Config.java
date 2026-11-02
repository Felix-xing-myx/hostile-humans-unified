package dev.felix.hostilehumans.config;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.world.SpawnerEntity;
/** Native gameplay reads the unified JSON snapshot. */
public final class Config {
    private Config() {}
    public static String disabledStructures() { return UnifiedConfig.get().disabledStructures(); }
    public static int maxTargeting() { return UnifiedConfig.get().maxTargeting(); }
    public static SpawnerEntity.SpawnType eventType() { return SpawnerEntity.SpawnType.valueOf(UnifiedConfig.get().battleEvent()); }
}
