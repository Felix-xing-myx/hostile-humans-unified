package club.someoneice.humangunner;

import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Random;

/** Conditional distributions, cap competition and actual profile precedence; no game world. */
public final class SpawnMeleeReservePolicyTest {
    public static void main(String[] args) throws Exception {
        String[] tiers = {"tier1", "tier2", "tier3"};
        double[] seconds = {.8, .9, 1}, thirds = {.2, .35, .5};
        for (int tier = 0; tier < tiers.length; tier++) {
            var config = new FactionEquipmentConfig(new com.google.gson.JsonObject(), tiers[tier]);
            var policy = config.profile("ashen").meleeReserves();
            check(policy != null && policy.preferOneHanded(), "gray has single-hand supply preference");
            check(policy.secondChance() == seconds[tier] && policy.thirdChance() == thirds[tier], "rank defaults");
            for (String faction : new String[]{"crimson", "azure", "verdant", "neutral"})
                check(config.profile(faction).meleeReserves() == null, "other factions unchanged");
            Random random = new Random(231023 + tier);
            int second = 0, third = 0;
            for (int i = 0; i < 100000; i++) {
                int target = policy.target(3, random.nextDouble(), random.nextDouble());
                if (target >= 2) second++;
                if (target == 3) third++;
                check(policy.additional(target, 1, 3, 3) == 0, "full weapon allocation cannot grow");
            }
            check(Math.abs(second / 100000.0 - seconds[tier]) < .01, "second probability");
            check(Math.abs(third / 100000.0 - seconds[tier] * thirds[tier]) < .01, "third is conditional");
            check(policy.additional(2, 1, 2, 3) == 1, "one gun and one melee can gain second melee");
            check(policy.additional(3, 1, 2, 3) == 1, "gun competes with third melee inside same cap");
            check(policy.additional(2, 2, 2, 3) == 0, "existing pair needs no duplicate grant");
        }
        var override = JsonParser.parseString("""
                {"faction_overrides":{"ashen":{"melee_reserves":{
                  "enabled":true,"second_weapon_chance":0.25,"third_weapon_chance":0,
                  "prefer_one_handed":false}}}}
                """).getAsJsonObject();
        var changed = new FactionEquipmentConfig(override, "tier3").profile("ashen").meleeReserves();
        check(changed.secondChance() == .25 && !changed.preferOneHanded(), "explicit faction override wins");
        override.getAsJsonObject("faction_overrides").getAsJsonObject("ashen")
                .getAsJsonObject("melee_reserves").addProperty("enabled", false);
        check(new FactionEquipmentConfig(override, "tier3").profile("ashen").meleeReserves() == null,
                "explicit disable restores generic supply");
        for (String malformed : new String[]{"{\"second_weapon_chance\":1.1}",
                "{\"third_weapon_chance\":-0.1}", "{\"prefer_one_handed\":\"yes\"}"}) {
            boolean rejected = false;
            try {
                FactionEquipmentConfig.prepare(JsonParser.parseString("{\"melee_reserves\":" + malformed + "}")
                        .getAsJsonObject(), Map.of());
            } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "bad known settings reject before publication");
        }
        check(calls("ConfiguredHumanEquipment", "generateReserves", "generateMeleeReserves"), "spawn uses supply policy");
        check(calls("FactionEquipmentPools", "singleHandedMelee", "supportsOneHand"), "BC ability is authoritative");
        System.out.println("SpawnMeleeReservePolicyTest passed; spawn-world acceptance remains unverified");
    }
    private static boolean calls(String owner, String methodName, String called) throws Exception {
        var node = new org.objectweb.asm.tree.ClassNode();
        try (var stream = SpawnMeleeReservePolicyTest.class.getClassLoader()
                .getResourceAsStream("club/someoneice/humangunner/" + owner + ".class")) {
            if (stream == null) throw new AssertionError("Missing " + owner);
            new org.objectweb.asm.ClassReader(stream).accept(node, 0);
        }
        for (var method : node.methods) if (method.name.equals(methodName))
            for (var insn : method.instructions)
                if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call && call.name.equals(called)) return true;
        return false;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
