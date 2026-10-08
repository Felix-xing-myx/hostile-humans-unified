package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import net.minecraft.util.RandomSource;

/** Real five-file read/projection/snapshot checks, independent of a running game. */
public final class SupplyConfigurationTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var directory = Files.createTempDirectory("human-supply-config-");
        try {
            var defaults = UnifiedConfig.defaults();
            SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            var rankPath = directory.resolve(SplitConfigFiles.DIRECTORY).resolve("tier2.json");
            var file = JsonParser.parseString(Files.readString(rankPath)).getAsJsonObject();
            file.add("food", json("""
                    {"enabled":true,"variety_min":1,"variety_max":1,"enchanted_golden_apple_chance":0,
                     "presets":[{"weight":1,"count_min":13,"count_max":13,"golden_apple_min":0,"golden_apple_max":0}],
                     "pool":[{"item":"example:custom_meal","weight":7}]}
                    """));
            file.add("potions", json("""
                    {"enabled":true,"recovery":{"count_min":3,"count_max":3,"pool":[
                     {"item":"minecraft:potion","potion":"example:custom_healing","weight":4}]},
                     "tactical":{"count_min":0,"count_max":0,"pool":[]},"bonus":{"chance":0}}
                    """));
            file.getAsJsonObject("equipment_loadout").add("armor_sets", JsonParser.parseString("""
                    [{"weight":1,"head":"example:helmet","chest":"example:chestplate",
                    "legs":"example:leggings","feet":"example:boots"}]
                    """));
            Files.writeString(rankPath, file.toString(), StandardCharsets.UTF_8);
            var loaded = SplitConfigFiles.load(directory, defaults, (rank, data) -> false);
            var config = new UnifiedConfig(loaded);
            var food = config.spawnFood("tier2");
            check(food.entries.size() == 1 && food.entries.get(0).item().toString().equals("example:custom_meal"),
                    "disk custom food pool replaces built-in pool");
            check(food.roll(RandomSource.create(1)).food().roll(RandomSource.create(2)) == 13,
                    "disk custom food count reaches spawn snapshot");
            check(food.enchantedAppleChance == 0, "zero enchanted-apple chance remains zero");
            check(config.spawnFood("tier1").presets.size() == 3, "other ranks retain default quantities");
            var potions = config.spawnPotions("tier2");
            check(potions.recovery.entries().size() == 1
                    && potions.recovery.entries().get(0).potion().toString().equals("example:custom_healing"),
                    "disk custom potion ID replaces default registered potion candidates");
            check(potions.recovery.rollCount(RandomSource.create(3)) == 3, "custom bottle count survives full load");
            check(potions.tactical.entries().isEmpty() && potions.tactical.maximum() == 0,
                    "zero tactical supply is not repopulated");
            var armor = config.equipmentLoadout("tier2").armorSets.entries();
            check(armor.size() == 1 && armor.get(0).head().toString().equals("example:helmet")
                    && armor.get(0).feet().toString().equals("example:boots"), "custom armor set survives full load");
            file.getAsJsonObject("food").add("pool", JsonParser.parseString("[]"));
            file.getAsJsonObject("potions").addProperty("enabled", false);
            file.getAsJsonObject("equipment_loadout").add("armor_sets", JsonParser.parseString("[]"));
            Files.writeString(rankPath, file.toString(), StandardCharsets.UTF_8);
            config = new UnifiedConfig(SplitConfigFiles.load(directory, defaults, (rank, data) -> false));
            check(config.spawnFood("tier2").entries.isEmpty(), "empty disk food pool stays empty");
            check(!config.spawnPotions("tier2").enabled, "disabled disk potion pool stays disabled");
            check(config.equipmentLoadout("tier2").armorSets.entries().isEmpty(), "empty disk armor pool stays empty");
            var invalid = new SpawnFoodConfig(json("""
                    {"enabled":false,"variety_min":"bad","variety_max":0,"presets":[],"pool":[]}
                    """));
            check(invalid.roll(RandomSource.create(4)).food().maximum() == 0, "disabled supplies roll zero food");
            check(invalid.varietyMin >= 1 && invalid.varietyMax >= invalid.varietyMin, "invalid variety never divides by zero");
            int[][] quantities = {{20,32,1,1},{32,48,1,2},{64,80,2,3},{96,112,3,4}};
            int i=0;
            for (String rank : TierConfigProjection.RANKS) {
                var f = new UnifiedConfig(defaults).spawnFood(rank);
                var first = f.presets.get(0);
                check(first.food().minimum()==quantities[i][0] && first.food().maximum()==quantities[i][1]
                        && first.goldenApples().minimum()==quantities[i][2]
                        && first.goldenApples().maximum()==quantities[i][3], "legacy first quantity preset retained " + rank);
                check(f.entries.size()==19, "original food candidates retained " + rank); i++;
            }
            check(calls("club/someoneice/humangunner/RecoverySupplies", "provisionInitialKit",
                    "club/someoneice/humangunner/UnifiedConfig", "spawnFood"),
                    "actual spawn kit obtains the configured food snapshot");
            check(calls("club/someoneice/humangunner/RecoverySupplies", "provisionFoodVariety",
                    "club/someoneice/humangunner/SpawnFoodConfig", "availableFoods"),
                    "actual food insertion resolves configured candidates");
            System.out.println("SupplyConfigurationTest: " + checks + " checks passed");
        } finally {
            try (var files = Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static boolean calls(String owner, String methodName, String target, String targetMethod) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = SupplyConfigurationTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing compiled class " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals(target) && call.name.equals(targetMethod)) return true;
        }
        return false;
    }
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
