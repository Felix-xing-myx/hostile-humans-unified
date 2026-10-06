package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;

/** Rank isolation, disabled/empty safety, ranges and ID parsing; no live entity required. */
public final class SpawnPotionConfigTest {
    public static void main(String[] args) {
        UnifiedConfig config = new UnifiedConfig(new JsonObject());
        int[] recoveryMin = {2, 2, 4, 6}, recoveryMax = {4, 5, 7, 8};
        int[] tacticalMin = {1, 2, 3, 5}, tacticalMax = {2, 3, 5, 7};
        String[] ranks = {"roamer", "tier1", "tier2", "tier3"};
        for (int i = 0; i < ranks.length; i++) {
            var value = config.spawnPotions(ranks[i]);
            check(value.recovery.minimum() == recoveryMin[i] && value.recovery.maximum() == recoveryMax[i], "recovery defaults");
            check(value.tactical.minimum() == tacticalMin[i] && value.tactical.maximum() == tacticalMax[i], "tactical defaults");
            JsonObject projected = TierConfigProjection.tier(UnifiedConfig.defaults(), ranks[i]);
            check(projected.getAsJsonObject("potions").getAsJsonObject("recovery").has("pool"), "rank-file projection includes potions");
        }
        var custom = new UnifiedConfig(json("""
                {"tiers":{"tier1":{"potions":{"enabled":false,"recovery":{"pool":[]}}}}}
                """));
        check(!custom.spawnPotions("tier1").enabled && custom.spawnPotions("tier2").enabled, "rank override isolation");
        check(custom.spawnPotions("tier1").recovery.entries().isEmpty(), "explicit empty pool stays empty");
        check(custom.spawnPotions("tier1").grant(null, RandomSource.create(1)).equals(new SpawnPotionConfig.Granted(0, 0)), "disabled config does not touch inventory");
        var empty = new SpawnPotionConfig(json("""
                {"recovery":{"count_min":2,"count_max":4,"pool":[]},
                 "tactical":{"count_min":1,"count_max":2,"pool":[]},
                 "bonus":{"item":"minecraft:potion","potion":"minecraft:long_swiftness","chance":1}}
                """));
        check(empty.grant(null, RandomSource.create(2)).equals(new SpawnPotionConfig.Granted(0, 0)), "empty pools also suppress tactical bonus");
        var pool = SpawnPotionConfig.parsePool(json("""
                {"count_min":12,"count_max":3,"pool":[
                 {"item":"minecraft:splash_potion","potion":"othermod:custom_potion","weight":5},
                 {"item":"minecraft:potion","potion":"minecraft:healing","weight":0},
                 {"item":"minecraft:potion","potion":"minecraft:healing","weight":"2"},
                 {"item":"bad id","potion":"minecraft:healing"}]}
                """));
        check(pool.minimum() == 3 && pool.maximum() == 12 && pool.entries().size() == 1, "normalize ranges and filter invalid entries");
        check(pool.entries().get(0).potion().toString().equals("othermod:custom_potion"), "optional mod potion IDs parsed before registration");
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < 200; i++) { int rolled = pool.rollCount(random); check(rolled >= 3 && rolled <= 12, "inclusive bottle range"); }
        var bounded = SpawnPotionConfig.parsePool(json("{\"count_min\":-2,\"count_max\":999,\"pool\":[]}"));
        check(bounded.minimum() == 0 && bounded.maximum() == 30, "bounded bottle totals");
        System.out.println("PASS: per-rank potion configuration, projection, empty/disabled safety and bounds");
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
