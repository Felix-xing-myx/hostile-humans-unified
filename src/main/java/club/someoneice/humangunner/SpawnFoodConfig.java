package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayList;
import java.util.List;

/** Restart-scoped food provisioning; explicit empty pools never manufacture default food. */
final class SpawnFoodConfig {
    record Range(int minimum, int maximum) {
        int roll(RandomSource random) { return random.nextInt(minimum, maximum + 1); }
    }
    record Preset(int weight, Range food, Range goldenApples) { }
    record Entry(ResourceLocation item, int weight) { }
    private record Available(Item item, int weight) { }
    final boolean enabled;
    final List<Preset> presets;
    final List<Entry> entries;
    final int varietyMin, varietyMax;
    final double enchantedAppleChance;
    private static final Preset NONE = new Preset(1, new Range(0, 0), new Range(0, 0));

    SpawnFoodConfig(JsonObject root) {
        enabled = UnifiedConfig.bool(root, "enabled", true);
        enchantedAppleChance = UnifiedConfig.number(root, "enchanted_golden_apple_chance", 0, 0, 1);
        int low = integer(root, "variety_min", 4, 1, 30);
        int high = integer(root, "variety_max", 7, 1, 30);
        varietyMin = Math.max(1, Math.min(low, high)); varietyMax = Math.max(varietyMin, Math.max(low, high));
        List<Preset> quantities = new ArrayList<>();
        if (root.has("presets") && root.get("presets").isJsonArray()) {
            int inspected = 0;
            for (var element : root.getAsJsonArray("presets")) {
                if (inspected++ >= 128) break;
                if (!element.isJsonObject()) continue;
                JsonObject row = element.getAsJsonObject();
                int weight = integer(row, "weight", 1, 0, 1_000_000);
                if (weight > 0) quantities.add(new Preset(weight,
                        range(row, "count_min", "count_max", 1920),
                        range(row, "golden_apple_min", "golden_apple_max", 64)));
            }
        }
        presets = List.copyOf(quantities);
        List<Entry> foods = new ArrayList<>();
        if (root.has("pool") && root.get("pool").isJsonArray()) {
            int inspected = 0;
            for (var element : root.getAsJsonArray("pool")) {
                if (inspected++ >= 128) break;
                try {
                    var row = element.getAsJsonObject();
                    String text = row.get("item").getAsString();
                    ResourceLocation id = ResourceLocation.tryParse(text);
                    int weight = integer(row, "weight", 1, 0, 1_000_000);
                    if (id != null && text.contains(":") && weight > 0) foods.add(new Entry(id, weight));
                } catch (RuntimeException ignored) { }
            }
        }
        entries = List.copyOf(foods);
    }
    private static Range range(JsonObject root, String minKey, String maxKey, int cap) {
        int low = integer(root, minKey, 0, 0, cap), high = integer(root, maxKey, 0, 0, cap);
        return new Range(Math.min(low, high), Math.max(low, high));
    }
    private static int integer(JsonObject root, String key, int fallback, int min, int max) {
        try {
            var value = root.get(key);
            if (value == null) return fallback;
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
            return Math.max(min, Math.min(max, value.getAsBigDecimal().intValueExact()));
        } catch (RuntimeException invalid) { return 0; }
    }
    Preset roll(RandomSource random) {
        if (!enabled || presets.isEmpty()) return NONE;
        long total = presets.stream().mapToLong(Preset::weight).sum();
        double value = random.nextDouble() * total;
        for (var preset : presets) if ((value -= preset.weight()) < 0) return preset;
        return presets.get(presets.size() - 1);
    }
    /** Weighted without replacement: a food weight changes inclusion, not its stack count. */
    List<Item> availableFoods(Human human) {
        List<Available> candidates = new ArrayList<>();
        for (Entry entry : entries) {
            var item = BuiltInRegistries.ITEM.getOptional(entry.item());
            if (item.isPresent() && new ItemStack(item.get()).isEdible()
                    && new ItemStack(item.get()).getFoodProperties(human) != null
                    && item.get() != net.minecraft.world.item.Items.GOLDEN_APPLE
                    && item.get() != net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE)
                candidates.add(new Available(item.get(), entry.weight()));
        }
        List<Item> result = new ArrayList<>();
        while (!candidates.isEmpty()) {
            long total = candidates.stream().mapToLong(Available::weight).sum();
            double value = human.getRandom().nextDouble() * total;
            int index = candidates.size() - 1;
            for (int i = 0; i < candidates.size(); i++) {
                if ((value -= candidates.get(i).weight()) < 0) { index = i; break; }
            }
            result.add(candidates.remove(index).item());
        }
        return result;
    }
}
