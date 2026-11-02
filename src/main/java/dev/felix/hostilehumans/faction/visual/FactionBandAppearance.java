package dev.felix.hostilehumans.faction.visual;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Client resource catalog; unknown data-pack factions keep a neutral, tintable uniform. */
final class FactionBandAppearance {
    private static final Set<String> BUILTIN = Set.of("crimson", "azure", "verdant", "ashen");
    record Textures(ResourceLocation cloth, ResourceLocation trim) {}
    private static final java.util.Map<String, Textures[]> CACHE = new java.util.HashMap<>();
    static {
        for (String faction : java.util.List.of("crimson", "azure", "verdant", "ashen", "base")) {
            Textures[] grades = new Textures[4];
            for (int i = 0; i < grades.length; i++) {
                String base = "textures/entity/faction_band/" + faction + "/grade_" + (i + 1);
                grades[i] = new Textures(new ResourceLocation(HumanRuntime.MOD_ID, base + "_cloth.png"),
                        new ResourceLocation(HumanRuntime.MOD_ID, base + "_trim.png"));
            }
            CACHE.put(faction, grades);
        }
    }
    private FactionBandAppearance() {}
    static Textures textures(String faction, int grade) {
        ResourceLocation id = faction == null ? null : ResourceLocation.tryParse(faction);
        String key = id != null && id.getNamespace().equals(HumanRuntime.MOD_ID) && BUILTIN.contains(id.getPath())
                ? id.getPath() : "base";
        return CACHE.get(key)[Math.max(1, Math.min(4, grade)) - 1];
    }
}
