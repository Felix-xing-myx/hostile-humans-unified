package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Independent rank budgets, AI overrides and the authoritative global switches. */
public final class TierRuntimeConfigTest {
    public static void main(String[] args) {
        UnifiedConfig defaults = new UnifiedConfig(new JsonObject());
        check(defaults.ammoConsumptionEnabled(), "consumption defaults on");
        check(defaults.ammoReserve("roamer").equals(new UnifiedConfig.AmmoReserve(32, 128, 6, 12)), "roamer budget");
        check(defaults.ammoReserve("tier1").equals(new UnifiedConfig.AmmoReserve(64, 128, 12, 24)), "tier1 budget");
        check(defaults.ammoReserve("tier2").equals(new UnifiedConfig.AmmoReserve(128, 192, 32, 48)), "tier2 budget");
        check(defaults.ammoReserve("tier3").equals(new UnifiedConfig.AmmoReserve(192, 192, 48, 72)), "tier3 budget");
        UnifiedConfig configured = config("""
                {"ammunition":{"consume_ammunition":false},
                 "tiers":{"tier1":{"ammunition":{"arrows":{"min":8,"max":4},
                           "magazines":{"min":0,"max":0}}}},
                 "ai":{"retreat_health_ratio":0.4},
                 "tier_ai":{"tier1":{"retreat_health_ratio":0.7,"shield_block_cooldown_ticks":15},
                            "tier2":{"retreat_health_ratio":0.2}}}
                """);
        check(!configured.ammoConsumptionEnabled(), "global free-ammo switch");
        check(configured.ammoReserve("tier1").equals(new UnifiedConfig.AmmoReserve(4, 8, 0, 0)), "inverted ranges and zero");
        check(configured.ammoReserve("tier2").equals(defaults.ammoReserve("tier2")), "ammo overrides isolated by rank");
        check(configured.combatAi("tier1").retreatHealthRatio() == 0.7, "tier1 AI override");
        check(configured.combatAi("tier2").retreatHealthRatio() == 0.2, "tier2 AI override");
        check(configured.combatAi("roamer").retreatHealthRatio() == 0.4, "legacy common AI inherited");
        check(configured.combatAi("tier1").shieldBlockCooldownTicks() == 15, "tier shield override");
        UnifiedConfig disabled = config("""
                {"ai":{"enabled":false},"tier_ai":{"tier1":{"enabled":true}}}
                """);
        check(!disabled.combatAi("tier1").enabled() && !disabled.combatAi("tier3").enabled(), "rank cannot override global master switch");
        UnifiedConfig bounded = config("""
                {"tiers":{"roamer":{"ammunition":{"arrows":{"min":-1,"max":9999},
                                 "magazines":{"min":-1,"max":9999}}}}}
                """);
        check(bounded.ammoReserve("roamer").equals(new UnifiedConfig.AmmoReserve(0, 1920, 0, 720)), "bounded integers");
        UnifiedConfig invalid = config("""
                {"tiers":{"roamer":{"ammunition":{"arrows":{"min":"4","max":2.5},
                                 "magazines":{"min":null,"max":false}}}}}
                """);
        check(invalid.ammoReserve("roamer").equals(defaults.ammoReserve("roamer")), "invalid budgets preserve defaults");
        UnifiedConfig nativeSettings = config("""
                {"native_gameplay":{"max_targeting":7,"battle_event":"Disabled"},
                 "tier_ai":{"tier1":{"flee_hp":0.6,"greet_chance":0.8}}}
                """);
        check(nativeSettings.maxTargeting() == 7
                && nativeSettings.battleEvent().equals("Disabled"), "native global settings");
        check(nativeSettings.combatAi("tier1").legacyFleeHealthRatio() == 0.6
                && nativeSettings.combatAi("tier1").greetChance() == 0.8
                && nativeSettings.combatAi("tier2").greetChance() == 0.05, "native AI isolated by rank");
        System.out.println("PASS: per-rank AI/ammunition and global switch authority");
    }

    private static UnifiedConfig config(String json) { return new UnifiedConfig(JsonParser.parseString(json).getAsJsonObject()); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
