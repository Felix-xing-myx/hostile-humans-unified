package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** One-time, raw-file migration. Default-merged values must never be mistaken for user overrides. */
final class EquipmentConfigMigration {
    static final String RECEIPT = "_faction_equipment_schema";
    private EquipmentConfigMigration() {}
    static boolean migrate(JsonObject file, String rank) {
        if (file.has(RECEIPT)) return false;
        JsonObject loadout = UnifiedConfig.object(file,"equipment_loadout").deepCopy();
        JsonObject old = UnifiedConfig.object(file,"tacz");
        if (!loadout.has("gun_chance") && old.has("gun_chance")) {
            double value = old.get("gun_chance").getAsDouble();
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid legacy gun chance");
            double legacy = switch (rank) { case "roamer" -> .08; case "tier1" -> .1; case "tier2" -> .4; case "tier3" -> .7; default -> throw new IllegalArgumentException("rank"); };
            // Shipped old defaults are not evidence of a custom override; preserve new faction differences.
            if (Double.compare(value, legacy) != 0) loadout.addProperty("gun_chance", value);
        }
        file.add("equipment_loadout", loadout);
        file.addProperty(RECEIPT, 1);
        return true;
    }
}
