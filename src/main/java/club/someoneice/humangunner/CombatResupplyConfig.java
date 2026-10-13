package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Server-only nearby supply policy; never grants movement or equipment control. */
public record CombatResupplyConfig(boolean enabled, int arrowLow, int arrowTarget, int reloads,
        int recoveryItems, double radius, int intervalTicks) {
    static CombatResupplyConfig parse(JsonObject data) {
        int low = (int) UnifiedConfig.number(data, "arrow_low", 8, 0, 4096);
        return new CombatResupplyConfig(UnifiedConfig.bool(data, "enabled", true), low,
                Math.max(low + 1, (int) UnifiedConfig.number(data, "arrow_target", 32, 1, 4097)),
                (int) UnifiedConfig.number(data, "magazine_target_reloads", 2, 1, 16),
                (int) UnifiedConfig.number(data, "recovery_items_per_pickup", 2, 1, 64),
                UnifiedConfig.number(data, "radius", 2, .5, 2.5),
                (int) UnifiedConfig.number(data, "interval_ticks", 20, 10, 200));
    }

    static boolean ammunitionNeeded(boolean active, boolean ownsWeapon, boolean consumes,
                                    int count, int low, int target) {
        return ownsWeapon && consumes && (active ? count < target : count <= low);
    }
}
