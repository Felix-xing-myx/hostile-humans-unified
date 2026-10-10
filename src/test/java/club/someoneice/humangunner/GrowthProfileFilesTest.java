package club.someoneice.humangunner;

import com.google.gson.*;
import java.nio.file.*;
import java.util.Arrays;

/** Current-settings relocation, isolation, formatting and failure preservation. */
final class GrowthProfileFilesTest {
    static void run() throws Exception {
        Path directory = Files.createTempDirectory("human-growth-layout-");
        JsonObject defaults = UnifiedConfig.defaults();
        SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
        Path files = directory.resolve(SplitConfigFiles.DIRECTORY);
        Path global = files.resolve("global.json"), tier = files.resolve("tier1.json");
        JsonObject g = SplitConfigFiles.read(global, 2), t = SplitConfigFiles.read(tier, 2);
        JsonObject prior = new JsonObject();
        prior.addProperty("start_day", 17);
        prior.addProperty("early_gun_chance_multiplier", .73);
        prior.addProperty("early_totem_keep_chance", .42);
        TierConfigProjection.put(g, "growth.profiles.tier1", prior);
        // Explicit destination wins, while fields absent there retain the user's source value.
        t.getAsJsonObject("growth_profile").remove("early_totem_keep_chance");
        t.getAsJsonObject("growth_profile").remove("start_day");
        t.getAsJsonObject("growth_profile").addProperty("early_gun_chance_multiplier", .21);
        Files.writeString(global, SplitConfigFiles.format(g));
        Files.writeString(tier, SplitConfigFiles.format(t));
        JsonObject root = SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
        JsonObject result = SplitConfigFiles.read(tier, 2).getAsJsonObject("growth_profile");
        check(result.get("start_day").getAsInt() == 17, "source curve retained");
        check(result.get("early_totem_keep_chance").getAsDouble() == .42, "source equipment setting retained");
        check(result.get("early_gun_chance_multiplier").getAsDouble() == .21, "destination custom value wins");
        check(!SplitConfigFiles.read(global, 2).getAsJsonObject("growth").has("profiles"), "global source removed after destination saves");
        check(new UnifiedConfig(root).equipmentGrowth().tiers().get("tier1").startDay() == 17, "runtime reads relocated tier curve");
        byte[] stable = Files.readAllBytes(tier);
        SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
        check(Arrays.equals(stable, Files.readAllBytes(tier)), "layout is stable across reloads");
        JsonObject text = JsonParser.parseString("{\"_说明_a_中文\":\"note\",\"a\":true,\"_说明_b_中文\":\"second\",\"b\":3}").getAsJsonObject();
        String formatted = SplitConfigFiles.format(text);
        check(formatted.contains("true,\n\n"), "blank line separates described settings");
        check(JsonParser.parseString(formatted).equals(text), "formatting is lossless strict JSON");
        g = SplitConfigFiles.read(global, 2);
        TierConfigProjection.put(g, "growth.profiles.tier1", prior);
        Files.writeString(global, SplitConfigFiles.format(g));
        Files.writeString(tier, "invalid");
        byte[] retained = Files.readAllBytes(global);
        GrowthProfileFiles.relocate(files, defaults);
        check(Arrays.equals(retained, Files.readAllBytes(global)), "invalid destination never removes source");
        check(Files.readString(tier).equals("invalid"), "invalid destination not overwritten");
        System.out.println("GrowthProfileFilesTest: 10 checks passed");
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
