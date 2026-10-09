package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.data.HumanData;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import java.util.ArrayList;
import java.util.List;

/** Per-rank startup snapshot; optional potion registrations resolve only at spawn time. */
final class SpawnPotionConfig {
    private static final java.util.Set<String> UNAVAILABLE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    record Entry(ResourceLocation item, ResourceLocation potion, int weight) {
        ItemStack resolve() {
            var registeredItem = BuiltInRegistries.ITEM.getOptional(item);
            var registeredPotion = BuiltInRegistries.POTION.getOptional(potion);
            if (registeredItem.isEmpty() || !(registeredItem.get() instanceof PotionItem)
                    || registeredPotion.isEmpty()) {
                if (UNAVAILABLE.add(item + "/" + potion)) HumanGunner.LOGGER.warn(
                        "Cannot grant configured spawn potion item={} potion={}: unregistered ID or item is not a PotionItem",
                        item, potion);
                return ItemStack.EMPTY;
            }
            return PotionUtils.setPotion(new ItemStack(registeredItem.get()), registeredPotion.get());
        }
    }
    record Pool(int minimum, int maximum, List<Entry> entries) {
        int rollCount(RandomSource random) { return random.nextInt(minimum, maximum + 1); }
    }
    record Granted(int recovery, int tactical) { }
    private record Available(ItemStack stack, int weight) { }
    final boolean enabled;
    final Pool recovery, tactical;

    SpawnPotionConfig(JsonObject root) {
        enabled = UnifiedConfig.bool(root, "enabled", true);
        recovery = parsePool(UnifiedConfig.object(root, "recovery"));
        tactical = parsePool(UnifiedConfig.object(root, "tactical"));
    }

    static Pool parsePool(JsonObject root) {
        int low = count(root, "count_min"), high = count(root, "count_max");
        List<Entry> entries = new ArrayList<>();
        if (root.has("pool") && root.get("pool").isJsonArray()) {
            int inspected = 0;
            for (var element : root.getAsJsonArray("pool")) {
                if (inspected++ >= 128) break;
                if (!element.isJsonObject()) continue;
                Entry entry = parseEntry(element.getAsJsonObject());
                if (entry != null) entries.add(entry);
            }
        }
        return new Pool(Math.min(low, high), Math.max(low, high), List.copyOf(entries));
    }

    private static int count(JsonObject root, String key) {
        try {
            var value = root.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
            return Math.max(0, Math.min(30, value.getAsBigDecimal().intValueExact()));
        } catch (RuntimeException invalid) { return 0; }
    }

    private static Entry parseEntry(JsonObject root) {
        try {
            String item = root.get("item").getAsString(), potion = root.get("potion").getAsString();
            ResourceLocation itemId = ResourceLocation.tryParse(item), potionId = ResourceLocation.tryParse(potion);
            if (!item.contains(":") || !potion.contains(":") || itemId == null || potionId == null) return null;
            if (root.has("weight") && (!root.get("weight").isJsonPrimitive()
                    || !root.get("weight").getAsJsonPrimitive().isNumber())) return null;
            int weight = root.has("weight") ? root.get("weight").getAsBigDecimal().intValueExact() : 1;
            return weight > 0 && weight <= 1_000_000 ? new Entry(itemId, potionId, weight) : null;
        } catch (RuntimeException invalid) { return null; }
    }

    Granted grant(HumanData data, RandomSource random) {
        if (!enabled) return new Granted(0, 0);
        int first = insertPool(data, random, recovery, recovery.rollCount(random));
        int inserted = insertPool(data, random, tactical, tactical.rollCount(random));
        return new Granted(first, inserted);
    }

    private static int insertPool(HumanData data, RandomSource random, Pool pool, int requested) {
        List<Available> available = new ArrayList<>();
        long total = 0;
        for (Entry entry : pool.entries) {
            ItemStack stack = entry.resolve();
            if (!stack.isEmpty()) { available.add(new Available(stack, entry.weight)); total += entry.weight; }
        }
        if (total == 0) return 0; // An empty/unavailable pool never manufactures fallback potions.
        int inserted = 0;
        for (int i = 0; i < requested; i++) {
            double roll = random.nextDouble() * total;
            Available chosen = available.get(available.size() - 1);
            for (Available entry : available) if ((roll -= entry.weight) < 0) { chosen = entry; break; }
            if (!RecoverySupplies.insertInventory(data, chosen.stack.copy())) break;
            inserted++;
        }
        return inserted;
    }
}
