package club.someoneice.humangunner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Predicate;

/** Immutable startup config. Registry availability is checked only when a spawn rolls equipment. */
public final class ConfiguredEquipmentLoadout {
    final Rules rules;
    final Pool<ItemEntry> mainhand, rangedMainhand, inventory, bonusMainhand, offhand, shields;
    final Pool<ArmorSetEntry> armorSets;
    final double rangedChance, offhandChance, bonusChance;
    final boolean rangedInBackpack, firearms;
    final boolean shieldEnchantments;
    final int shieldMin, shieldMax;
    final int weaponMax;
    final double secondWeaponChance, thirdWeaponChance;

    private ConfiguredEquipmentLoadout(JsonObject root, String rank) {
        JsonObject r = UnifiedConfig.object(root, "rules");
        float min = (float) UnifiedConfig.number(r, "damage_percent_min", 0, 0, 1);
        float max = (float) UnifiedConfig.number(r, "damage_percent_max", .85, 0, 1);
        rules = new Rules((float) UnifiedConfig.number(r, "enchant_chance", 0, 0, 1),
                Math.min(min, max), Math.max(min, max), UnifiedConfig.bool(r, "elite_enchantments", false));
        mainhand = items(array(root, "mainhand"), rank + ".mainhand");
        rangedMainhand = items(array(root, "ranged_mainhand"), rank + ".ranged_mainhand");
        inventory = items(array(root, "inventory"), rank + ".inventory");
        JsonObject off = UnifiedConfig.object(root, "offhand");
        offhand = items(array(off, "entries"), rank + ".offhand.entries");
        offhandChance = UnifiedConfig.number(off, "chance", 0, 0, 1);
        JsonObject bonus = UnifiedConfig.object(root, "bonus_mainhand");
        bonusMainhand = items(array(bonus, "entries"), rank + ".bonus_mainhand.entries");
        bonusChance = UnifiedConfig.number(bonus, "chance", 0, 0, 1);
        shields = items(array(root, "shield_pool"), rank + ".shield_pool");
        List<ArmorSetEntry> armor = new ArrayList<>();
        for (JsonElement element : array(root, "armor_sets")) {
            try {
                JsonObject entry = element.getAsJsonObject();
                armor.add(new ArmorSetEntry(weight(entry), text(entry, "requires_mod"),
                        optionalId(entry, "head"), optionalId(entry, "chest"),
                        optionalId(entry, "legs"), optionalId(entry, "feet")));
            } catch (RuntimeException invalid) { warn(rank + ".armor_sets", element); }
        }
        armorSets = new Pool<>(armor);
        rangedChance = UnifiedConfig.number(root, "ranged_chance", rank.equals("tier3") ? 1 : .2, 0, 1);
        rangedInBackpack = UnifiedConfig.bool(root, "ranged_in_backpack", rank.equals("tier3"));
        firearms = UnifiedConfig.bool(root, "allow_tacz_firearms", true);
        shieldEnchantments = UnifiedConfig.bool(root, "automatic_shield_enchantments", true);
        int low = (int) UnifiedConfig.number(root, "guaranteed_shields_min", 1, 0, 3);
        int high = (int) UnifiedConfig.number(root, "guaranteed_shields_max", 1, 0, 3);
        shieldMin = Math.min(low, high);
        shieldMax = Math.max(low, high);
        JsonObject weapons = UnifiedConfig.object(root, "weapon_count");
        // This is a spawn cap, not an inventory cap during subsequent looting.
        weaponMax = rank.equals("roamer") ? 2 : 3;
        secondWeaponChance = UnifiedConfig.number(weapons, "second_weapon_chance", .45, 0, .49);
        thirdWeaponChance = UnifiedConfig.number(weapons, "third_weapon_chance", .20, 0, .49);
    }

    static ConfiguredEquipmentLoadout parse(JsonObject root, String rank) {
        return new ConfiguredEquipmentLoadout(root, rank);
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static Pool<ItemEntry> items(JsonArray array, String path) {
        List<ItemEntry> result = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                JsonObject e = element.getAsJsonObject();
                ResourceLocation id = requiredId(e, "item");
                Map<ResourceLocation, Integer> enchantments = new LinkedHashMap<>();
                for (var enchant : UnifiedConfig.object(e, "enchantments").entrySet()) {
                    ResourceLocation key = ResourceLocation.tryParse(enchant.getKey());
                    if (key == null || !enchant.getKey().contains(":")) continue;
                    try {
                        int level = enchant.getValue().getAsBigDecimal().intValueExact();
                        if (level > 0 && level <= 255) enchantments.put(key, level);
                    } catch (RuntimeException invalid) { warn(path + ".enchantments", enchant.getValue()); }
                }
                int count = 1;
                if (e.has("count")) {
                    JsonElement amount = e.get("count");
                    if (!amount.isJsonPrimitive() || !amount.getAsJsonPrimitive().isNumber())
                        throw new IllegalArgumentException("Count must be numeric");
                    count = amount.getAsBigDecimal().intValueExact();
                    if (count < 1 || count > 64) throw new IllegalArgumentException("Count out of range");
                }
                result.add(new ItemEntry(id, weight(e), text(e, "requires_mod"), count, Map.copyOf(enchantments)));
            } catch (RuntimeException invalid) { warn(path, element); }
        }
        return new Pool<>(result);
    }

    private static int weight(JsonObject root) {
        if (!root.has("weight")) return 1;
        if (!root.get("weight").isJsonPrimitive() || !root.get("weight").getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Weight must be numeric");
        int value = root.get("weight").getAsBigDecimal().intValueExact();
        if (value < 0 || value > 1000000) throw new IllegalArgumentException("Weight out of range");
        return value;
    }

    private static String text(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static ResourceLocation requiredId(JsonObject root, String key) {
        String value = text(root, key);
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id == null || !value.contains(":")) throw new IllegalArgumentException("Invalid item ID");
        return id;
    }

    private static ResourceLocation optionalId(JsonObject root, String key) {
        return !root.has(key) || root.get(key).isJsonNull() ? null : requiredId(root, key);
    }

    private static void warn(String path, JsonElement value) {
        HumanGunner.LOGGER.warn("Ignoring invalid equipment config entry {}: {}", path, value);
    }

    record Rules(float enchantChance, float damageMin, float damageMax, boolean eliteEnchantments) { }
    interface WeightedEntry { int weight(); boolean available(); }
    record ItemEntry(ResourceLocation id, int weight, String requiresMod, int count,
                     Map<ResourceLocation, Integer> enchantments) implements WeightedEntry {
        @Override public boolean available() {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return loaded(requiresMod) && item != null && item != Items.AIR;
        }
    }
    record ArmorSetEntry(int weight, String requiresMod, ResourceLocation head,
                         ResourceLocation chest, ResourceLocation legs, ResourceLocation feet) implements WeightedEntry {
        @Override public boolean available() {
            return loaded(requiresMod) && armor(head, EquipmentSlot.HEAD) && armor(chest, EquipmentSlot.CHEST)
                    && armor(legs, EquipmentSlot.LEGS) && armor(feet, EquipmentSlot.FEET);
        }
        private static boolean armor(ResourceLocation id, EquipmentSlot slot) {
            if (id == null) return true;
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
        }
    }
    private static boolean loaded(String mod) { return mod == null || mod.isBlank() || ModList.get().isLoaded(mod); }

    static final class Pool<T extends WeightedEntry> {
        private final List<T> entries;
        Pool(List<T> entries) { this.entries = List.copyOf(entries); }
        List<T> entries() { return entries; }
        T roll(RandomSource random) { return roll(random, entry -> true); }
        T roll(RandomSource random, Predicate<T> eligible) {
            List<T> available = entries.stream().filter(e -> e.weight() > 0 && e.available() && eligible.test(e)).toList();
            // Use double weights so even a large user pool cannot overflow an integer sum.
            double total = 0;
            for (T entry : available) total += entry.weight();
            if (total <= 0) return null;
            double roll = random.nextDouble() * total;
            for (T entry : available) if ((roll -= entry.weight()) < 0) return entry;
            return available.get(available.size() - 1);
        }
    }
}
