package club.someoneice.humangunner;

import com.google.gson.JsonParser;
import java.util.Map;

/** Metadata aliases, immutable normalized data and the actual built-in definitions. */
public final class GunSpecializationTest {
    public static void main(String[] args) {
        check(GunSpecialization.category("lmg") == GunSpecialization.Category.MACHINE_GUN, "JEG lmg");
        check(GunSpecialization.category("machine_gun") == GunSpecialization.Category.MACHINE_GUN, "native machine gun");
        check(GunSpecialization.category("snipers_rifle") == GunSpecialization.Category.SNIPER, "native sniper");
        check(GunSpecialization.category("magnum") == GunSpecialization.Category.PISTOL, "magnum");
        check(GunSpecialization.category("unrecognized") == GunSpecialization.Category.OTHER, "neutral unknown");
        Map<String, Double> custom = GunSpecialization.parse(JsonParser.parseString("{\"rifle\":2}"));
        double sum = custom.entrySet().stream().filter(e -> !e.getKey().equals("other")).mapToDouble(Map.Entry::getValue).sum();
        check(Math.abs(sum - 7) < 1e-10, "known mean is one");
        check(custom.get("other") == 1, "unknown never gains implicit bias");
        try { custom.put("rifle", 4.0); throw new AssertionError("mutable"); } catch (UnsupportedOperationException expected) { }
        for (String invalid : new String[]{"{\"other\":2}","{\"typo\":1}","{\"rifle\":0}","{\"rifle\":1e100}"}) {
            try { GunSpecialization.parse(JsonParser.parseString(invalid)); throw new AssertionError(invalid); }
            catch (IllegalArgumentException expected) { }
        }
        for (var d : FactionDefinitions.current().definitions().values()) {
            check(d.preference().size() == 13 && d.bossPreference().size() == 13, "thirteen centers");
            check(d.weight() == 10, "equal faction spawn weights");
            check(d.gunSpecialization().size() == 8, "all categories defined");
        }
        System.out.println("Gun specialization metadata and definition checks passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
