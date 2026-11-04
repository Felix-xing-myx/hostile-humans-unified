package dev.felix.hostilehumans.settlement;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Portable generation proposal. Saving a draft never enables a server generation group. */
public record SettlementGenerationRules(
        boolean enabled, String group, int weight, String dimension,
        List<String> allowedBiomes, List<String> excludedBiomes,
        Placement placement, int minY, int maxY, int maxSlope,
        Water water, Terrain terrain, int minimumDay) {
    public enum Placement { SURFACE, UNDERGROUND }
    public enum Water { ANY, DRY, WATER }
    public enum Terrain { NONE, BEARD_THIN, BEARD_BOX }

    public static final SettlementGenerationRules DEFAULT = new SettlementGenerationRules(
            false, "hostile_humans_unified:default", 1, "minecraft:overworld",
            List.of(), List.of(), Placement.SURFACE, -64, 319, 8, Water.DRY, Terrain.NONE, 0);

    public SettlementGenerationRules {
        requireId(group);
        requireId(dimension);
        allowedBiomes = checkedSelectors(allowedBiomes);
        excludedBiomes = checkedSelectors(excludedBiomes);
        if (weight < 1 || weight > 1000 || minY < -2048 || maxY > 2047 || minY > maxY
                || maxSlope < 0 || maxSlope > 64 || minimumDay < 0
                || placement == null || water == null || terrain == null)
            throw new IllegalArgumentException("invalid natural generation rule");
    }

    private static void requireId(String id) {
        if (id == null || id.length() > 128 || !id.contains(":") || ResourceLocation.tryParse(id) == null)
            throw new IllegalArgumentException("invalid natural generation ID");
    }

    private static List<String> checkedSelectors(List<String> selectors) {
        if (selectors.size() > 32) throw new IllegalArgumentException("too many biome selectors");
        for (String selector : selectors) requireId(selector.startsWith("#") ? selector.substring(1) : selector);
        return List.copyOf(selectors);
    }

    private static List<String> selectors(CompoundTag tag, String key) {
        String text = tag.getString(key);
        if (text.length() > 4096) throw new IllegalArgumentException("biome selector list too long");
        if (text.isBlank()) return List.of();
        return List.of(text.strip().split("\\s*,\\s*", -1));
    }

    public static SettlementGenerationRules read(CompoundTag tag) {
        if (tag.isEmpty()) return DEFAULT;
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1)
            throw new IllegalArgumentException("unknown natural generation rule schema");
        return new SettlementGenerationRules(tag.getBoolean("enabled"), tag.getString("group"),
                tag.getInt("weight"), tag.getString("dimension"), selectors(tag, "allowed_biomes"),
                selectors(tag, "excluded_biomes"), Placement.valueOf(tag.getString("placement")),
                tag.getInt("min_y"), tag.getInt("max_y"), tag.getInt("max_slope"),
                Water.valueOf(tag.getString("water")), Terrain.valueOf(tag.getString("terrain")),
                tag.getInt("minimum_day"));
    }

    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putBoolean("enabled", enabled);
        tag.putString("group", group);
        tag.putInt("weight", weight);
        tag.putString("dimension", dimension);
        tag.putString("allowed_biomes", String.join(",", allowedBiomes));
        tag.putString("excluded_biomes", String.join(",", excludedBiomes));
        tag.putString("placement", placement.name());
        tag.putInt("min_y", minY);
        tag.putInt("max_y", maxY);
        tag.putInt("max_slope", maxSlope);
        tag.putString("water", water.name());
        tag.putString("terrain", terrain.name());
        tag.putInt("minimum_day", minimumDay);
        return tag;
    }
}
