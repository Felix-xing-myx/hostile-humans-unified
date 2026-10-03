package club.someoneice.humangunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;
import java.util.function.Predicate;

/** Startup-only discovery. Persistent receipts prevent deleted entries reappearing. */
final class EquipmentIntegrationDefaults {
    private static final List<String> TYPES = List.of("dagger", "longsword", "katana", "saber", "rapier",
            "spear", "battleaxe", "flanged_mace", "warhammer", "glaive", "scythe");
    private EquipmentIntegrationDefaults() { }

    static boolean seed(JsonObject equipment) {
        return seed(equipment, mod -> ModList.get() != null && ModList.get().isLoaded(mod),
                id -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)) != null
                        && ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)) != Items.AIR);
    }

    static boolean seed(JsonObject equipment, Predicate<String> loaded, Predicate<String> registered) {
        boolean changed = false;
        JsonObject ranks = equipment.getAsJsonObject("equipment_loadouts");
        if (ranks == null) return false;
        for (String rank : List.of("roamer", "tier1", "tier2", "tier3")) {
            if (!ranks.has(rank) || !ranks.get(rank).isJsonObject()) continue;
            JsonObject settings = ranks.getAsJsonObject(rank);
            for (String mod : List.of("spartanweaponry", "spartanshields")) {
                String flag = mod.equals("spartanweaponry") ? "automatic_spartan_weapons" : "automatic_spartan_shields";
                String receipt = "_imported_" + mod;
                if (!UnifiedConfig.bool(settings, flag, true) || !loaded.test(mod)
                        || UnifiedConfig.bool(settings, receipt, false)) continue;
                List<String> materials = switch (rank) {
                    case "roamer" -> List.of("wooden", "stone", "copper");
                    case "tier1" -> List.of("stone", "copper", "iron");
                    case "tier2" -> List.of("iron", "diamond");
                    default -> List.of("diamond", "netherite");
                };
                boolean any = false;
                for (String material : materials) {
                    if (mod.equals("spartanweaponry")) {
                        for (String type : TYPES) {
                            String id = mod + ":" + material + "_" + type;
                            any |= append(settings, "mainhand", id, mod, registered);
                            any |= append(settings, "inventory", id, mod, registered);
                        }
                        for (String type : List.of("longbow", "heavy_crossbow"))
                            any |= append(settings, "ranged_mainhand", mod + ":" + material + "_" + type, mod, registered);
                    } else {
                        for (String type : List.of("basic_shield", "tower_shield"))
                            any |= append(settings, "shield_pool", mod + ":" + material + "_" + type, mod, registered);
                    }
                }
                // Do not finalize an import before item registries are ready.
                if (any) {
                    settings.addProperty(receipt, true);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean append(JsonObject root, String pool, String id, String mod, Predicate<String> registered) {
        if (!registered.test(id) || !root.has(pool) || !root.get(pool).isJsonArray()) return false;
        JsonArray entries = root.getAsJsonArray(pool);
        for (var entry : entries) {
            if (entry.isJsonObject() && entry.getAsJsonObject().has("item")
                    && entry.getAsJsonObject().get("item").isJsonPrimitive()
                    && entry.getAsJsonObject().get("item").getAsString().equals(id)) return true;
        }
        JsonObject row = new JsonObject();
        row.addProperty("item", id);
        row.addProperty("weight", 1);
        row.addProperty("requires_mod", mod);
        entries.add(row);
        return true;
    }
}
