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
    record Entry(ResourceLocation item, int weight, int category) { }
    private record Available(Item item, int weight, int category) { }
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
                    if (id != null && text.contains(":") && weight > 0) foods.add(new Entry(id, weight, category(row,id)));
                } catch (RuntimeException ignored) { }
            }
        }
        JsonObject itemPool=FactionEquipmentConfig.object(root,"item_pool");
        for(var extra:FactionEquipmentPools.list(itemPool,"extra")) try {
            ResourceLocation id=ResourceLocation.tryParse(extra.getAsString());
            if(id!=null && foods.stream().noneMatch(e->e.item().equals(id))) foods.add(new Entry(id,1,category(new JsonObject(),id)));
        } catch(RuntimeException invalid) { FactionEquipmentConfig.warn(extra.toString(),"Invalid extra food ID"); }
        foods.removeIf(entry->denied(entry.item(),itemPool));
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
                if(candidates.stream().noneMatch(e->e.item()==item.get())) candidates.add(new Available(item.get(), entry.weight(),entry.category()));
        }
        List<Item> result = new ArrayList<>();
        double[] categoryWeights=FactionEquipmentConfig.of(human).weights("food_category_weights",4);
        while (!candidates.isEmpty()) {
            double[] possible=categoryWeights.clone();
            for(int group=0;group<4;group++) {
                final int selected=group;
                if(candidates.stream().noneMatch(e->e.category()==selected)) possible[group]=0;
            }
            int group=FactionEquipmentPolicy.choose(possible,human.getRandom().nextDouble());
            if(group<0) break;
            List<Available> members=candidates.stream().filter(e->e.category()==group).toList();
            int chosen=FactionEquipmentPolicy.choose(members.stream().mapToDouble(Available::weight).toArray(),human.getRandom().nextDouble());
            Available selected=members.get(chosen);
            result.add(selected.item()); candidates.remove(selected);
        }
        return result;
    }
    private static boolean denied(ResourceLocation id,JsonObject pool) {
        for(var deny:FactionEquipmentPools.list(pool,"deny")) if(deny.isJsonPrimitive()) {
            String text=deny.getAsString();
            if(text.equals(id.toString())||text.equals(id.getNamespace())||text.equals(id.getNamespace()+":*")) return true;
        }
        return false;
    }
    private static int category(JsonObject row,ResourceLocation id) {
        if(row.has("category")&&row.get("category").isJsonPrimitive()) {
            int index=List.of("cooked_meat","staple","light","meal").indexOf(row.get("category").getAsString());
            if(index>=0) return index;
            FactionEquipmentConfig.warn(row.toString(),"Unknown food category; classifying explicit candidate by ID");
        }
        return switch(id.toString()) {
            case "minecraft:cooked_beef","minecraft:cooked_porkchop","minecraft:cooked_chicken","minecraft:cooked_salmon" -> 0;
            case "minecraft:bread","minecraft:baked_potato" -> 1;
            case "minecraft:apple","minecraft:carrot","minecraft:sweet_berries" -> 2;
            default -> 3;
        };
    }

}
