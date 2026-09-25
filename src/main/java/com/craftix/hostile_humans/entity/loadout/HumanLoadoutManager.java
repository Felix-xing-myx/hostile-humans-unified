package com.craftix.hostile_humans.entity.loadout;

import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

public final class HumanLoadoutManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Map<HumanTier, HumanLoadout> LOADOUTS = Map.of();

    private HumanLoadoutManager() {
    }

    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener((PreparableReloadListener)new ReloadListener());
    }

    @Nullable
    public static HumanLoadout get(HumanTier tier) {
        return LOADOUTS.get(tier);
    }

    private static final class ReloadListener
    extends SimpleJsonResourceReloadListener {
        private ReloadListener() {
            super(GSON, "human_loadouts");
        }

        protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resourceManager, ProfilerFiller profiler) {
            EnumMap<HumanTier, HumanLoadout> parsedLoadouts = new EnumMap<HumanTier, HumanLoadout>(HumanTier.class);
            for (Map.Entry<ResourceLocation, JsonElement> entry : data.entrySet()) {
                HumanTier tier = ReloadListener.parseTier(entry.getKey());
                if (tier == null || !entry.getValue().isJsonObject()) continue;
                try {
                    parsedLoadouts.put(tier, ReloadListener.parseLoadout(entry.getValue().getAsJsonObject()));
                }
                catch (RuntimeException exception) {
                    HostileHumans.LOGGER.error("Failed to parse human loadout {}", entry.getKey(), exception);
                }
            }
            LOADOUTS = Map.copyOf(parsedLoadouts);
            HostileHumans.LOGGER.info("Loaded {} human loadout definitions", LOADOUTS.size());
        }

        @Nullable
        private static HumanTier parseTier(ResourceLocation id) {
            return switch (id.getPath()) {
                case "tier1" -> HumanTier.LEVEL1;
                case "tier2" -> HumanTier.LEVEL2;
                case "roamer" -> HumanTier.ROAMER;
                default -> null;
            };
        }

        private static HumanLoadout parseLoadout(JsonObject root) {
            JsonObject rulesObject = GsonHelper.getAsJsonObject((JsonObject)root, (String)"rules", (JsonObject)new JsonObject());
            Rules rules = new Rules(GsonHelper.getAsFloat((JsonObject)rulesObject, (String)"enchant_chance", (float)0.0f), GsonHelper.getAsFloat((JsonObject)rulesObject, (String)"damage_percent_min", (float)0.0f), GsonHelper.getAsFloat((JsonObject)rulesObject, (String)"damage_percent_max", (float)0.85f));
            ItemPool mainhand = new ItemPool(ReloadListener.parseItemEntries(GsonHelper.getAsJsonArray((JsonObject)root, (String)"mainhand")));
            ItemPool rangedMainhand = new ItemPool(ReloadListener.parseItemEntries(GsonHelper.getAsJsonArray((JsonObject)root, (String)"ranged_mainhand", null)));
            ChanceItemPool offhand = ReloadListener.parseChanceItemPool(GsonHelper.getAsJsonObject((JsonObject)root, (String)"offhand", (JsonObject)new JsonObject()));
            ItemPool inventory = new ItemPool(ReloadListener.parseItemEntries(GsonHelper.getAsJsonArray((JsonObject)root, (String)"inventory", null)));
            ChanceItemPool bonusMainhand = ReloadListener.parseChanceItemPool(GsonHelper.getAsJsonObject((JsonObject)root, (String)"bonus_mainhand", (JsonObject)new JsonObject()));
            ArmorSetPool armorSets = new ArmorSetPool(ReloadListener.parseArmorSets(GsonHelper.getAsJsonArray((JsonObject)root, (String)"armor_sets")));
            return new HumanLoadout(rules, mainhand, rangedMainhand, offhand, inventory, bonusMainhand, armorSets);
        }

        private static ChanceItemPool parseChanceItemPool(JsonObject object) {
            float chance = GsonHelper.getAsFloat((JsonObject)object, (String)"chance", (float)0.0f);
            return new ChanceItemPool(chance, ReloadListener.parseItemEntries(GsonHelper.getAsJsonArray((JsonObject)object, (String)"entries", null)));
        }

        private static List<ItemEntry> parseItemEntries(@Nullable JsonArray array) {
            ArrayList<ItemEntry> entries = new ArrayList<ItemEntry>();
            if (array == null) {
                return entries;
            }
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                entries.add(new ItemEntry(new ResourceLocation(GsonHelper.getAsString((JsonObject)object, (String)"item")), GsonHelper.getAsInt((JsonObject)object, (String)"weight", (int)1), GsonHelper.getAsString((JsonObject)object, (String)"requires_mod", null)));
            }
            return entries;
        }

        private static List<ArmorSetEntry> parseArmorSets(@Nullable JsonArray array) {
            ArrayList<ArmorSetEntry> entries = new ArrayList<ArmorSetEntry>();
            if (array == null) {
                return entries;
            }
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                entries.add(new ArmorSetEntry(GsonHelper.getAsInt((JsonObject)object, (String)"weight", (int)1), GsonHelper.getAsString((JsonObject)object, (String)"requires_mod", null), ReloadListener.parseOptionalId(object, "head"), ReloadListener.parseOptionalId(object, "chest"), ReloadListener.parseOptionalId(object, "legs"), ReloadListener.parseOptionalId(object, "feet")));
            }
            return entries;
        }

        @Nullable
        private static ResourceLocation parseOptionalId(JsonObject object, String key) {
            return object.has(key) ? new ResourceLocation(GsonHelper.getAsString((JsonObject)object, (String)key)) : null;
        }
    }

    public static final class HumanLoadout {
        public final Rules rules;
        public final ItemPool mainhand;
        public final ItemPool rangedMainhand;
        public final ChanceItemPool offhand;
        public final ItemPool inventory;
        public final ChanceItemPool bonusMainhand;
        public final ArmorSetPool armorSets;

        public HumanLoadout(Rules rules, ItemPool mainhand, ItemPool rangedMainhand, ChanceItemPool offhand, ItemPool inventory, ChanceItemPool bonusMainhand, ArmorSetPool armorSets) {
            this.rules = rules;
            this.mainhand = mainhand;
            this.rangedMainhand = rangedMainhand;
            this.offhand = offhand;
            this.inventory = inventory;
            this.bonusMainhand = bonusMainhand;
            this.armorSets = armorSets;
        }
    }

    public static final class ArmorSetEntry
    implements WeightedEntry {
        public final int weight;
        @Nullable
        public final String requiresMod;
        @Nullable
        public final ResourceLocation head;
        @Nullable
        public final ResourceLocation chest;
        @Nullable
        public final ResourceLocation legs;
        @Nullable
        public final ResourceLocation feet;

        public ArmorSetEntry(int weight, @Nullable String requiresMod, @Nullable ResourceLocation head, @Nullable ResourceLocation chest, @Nullable ResourceLocation legs, @Nullable ResourceLocation feet) {
            this.weight = weight;
            this.requiresMod = requiresMod;
            this.head = head;
            this.chest = chest;
            this.legs = legs;
            this.feet = feet;
        }

        @Override
        public int weight() {
            return this.weight;
        }

        @Override
        public boolean isAvailable() {
            return (this.requiresMod == null || ModList.get().isLoaded(this.requiresMod)) && ArmorSetEntry.isPresent(this.head) && ArmorSetEntry.isPresent(this.chest) && ArmorSetEntry.isPresent(this.legs) && ArmorSetEntry.isPresent(this.feet);
        }

        private static boolean isPresent(@Nullable ResourceLocation location) {
            return location == null || ForgeRegistries.ITEMS.containsKey(location);
        }
    }

    public static final class ItemEntry
    implements WeightedEntry {
        public final ResourceLocation itemId;
        public final int weight;
        @Nullable
        public final String requiresMod;

        public ItemEntry(ResourceLocation itemId, int weight, @Nullable String requiresMod) {
            this.itemId = itemId;
            this.weight = weight;
            this.requiresMod = requiresMod;
        }

        @Override
        public int weight() {
            return this.weight;
        }

        @Override
        public boolean isAvailable() {
            return (this.requiresMod == null || ModList.get().isLoaded(this.requiresMod)) && ForgeRegistries.ITEMS.containsKey(this.itemId);
        }
    }

    public static interface WeightedEntry {
        public int weight();

        public boolean isAvailable();
    }

    public static final class ArmorSetPool
    extends Pool<ArmorSetEntry> {
        public ArmorSetPool(List<ArmorSetEntry> entries) {
            super(entries);
        }
    }

    public static final class ChanceItemPool
    extends ItemPool {
        public final float chance;

        public ChanceItemPool(float chance, List<ItemEntry> entries) {
            super(entries);
            this.chance = chance;
        }
    }

    public static class ItemPool
    extends Pool<ItemEntry> {
        public ItemPool(List<ItemEntry> entries) {
            super(entries);
        }
    }

    public static class Pool<T extends WeightedEntry> {
        protected final List<T> entries;

        public Pool(List<T> entries) {
            this.entries = entries;
        }

        @Nullable
        public T roll(RandomSource random) {
            List<T> validEntries = this.entries.stream().filter(WeightedEntry::isAvailable).toList();
            if (validEntries.isEmpty()) {
                return null;
            }
            int totalWeight = 0;
            for (WeightedEntry entry : validEntries) {
                totalWeight += Math.max(1, entry.weight());
            }
            int roll = random.nextInt(totalWeight);
            for (WeightedEntry entry : validEntries) {
                if ((roll -= Math.max(1, entry.weight())) >= 0) continue;
                return (T)entry;
            }
            return (T)validEntries.get(validEntries.size() - 1);
        }

        public boolean isEmpty() {
            return this.entries.isEmpty();
        }
    }

    public static final class Rules {
        public final float enchantChance;
        public final float damagePercentMin;
        public final float damagePercentMax;

        public Rules(float enchantChance, float damagePercentMin, float damagePercentMax) {
            this.enchantChance = enchantChance;
            this.damagePercentMin = damagePercentMin;
            this.damagePercentMax = damagePercentMax;
        }
    }
}

