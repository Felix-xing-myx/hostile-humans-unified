package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import java.util.Arrays;

/** Identity lifecycle. Read access never initializes or rerolls a human. */
public final class HumanFactions {
    public static final String FACTION = "hostile_humans_unified:faction";
    public static final String BOSS = "hostile_humans_unified:boss";
    private HumanFactions() {}
    private record Group(String faction, SpawnGroupData delegate) implements SpawnGroupData {}

    public static String id(Human human) { return human.getPersistentData().getString(FACTION); }
    public static String shortId(Human human) {
        if (human.getTier() == HumanTier.ROAMER) return "roamer";
        ResourceLocation key = FactionDefinitions.id(id(human));
        return key != null && key.getNamespace().equals(HumanGunner.MOD_ID) ? key.getPath() : id(human);
    }
    public static boolean enabled() { return UnifiedConfig.get().featureEnabled("factions", true); }
    public static boolean isBoss(Human human) { return human.getPersistentData().getBoolean(BOSS); }
    public static boolean same(Human a, Human b) {
        if (a.getTier() == HumanTier.ROAMER || b.getTier() == HumanTier.ROAMER) return false;
        if (!enabled()) return !a.team.isEmpty() && a.team.equals(b.team);
        return FactionDefinitions.allies(id(a), id(b));
    }
    public static boolean enemies(Human a, Human b) {
        if (a == b) return false;
        if (a.getTier() == HumanTier.ROAMER || b.getTier() == HumanTier.ROAMER) return true;
        if (!enabled()) return !a.team.equals(b.team);
        return FactionDefinitions.enemies(id(a), id(b));
    }
    public static double[] preference(Human human, boolean boss) {
        FactionDefinitions.Definition definition = FactionDefinitions.find(id(human));
        if (definition == null) { double[] neutral = new double[11]; Arrays.fill(neutral, 1); return neutral; }
        return (boss ? definition.bossPreference() : definition.preference()).stream().mapToDouble(Double::doubleValue).toArray();
    }
    public static double bossBase(Human human, String dimension) {
        if (!isBoss(human)) return 1;
        return switch (dimension) {
            case "health" -> 2.4;
            case "armor", "toughness" -> 1.6;
            case "speed" -> .95;
            case "detection" -> 1.15;
            case "melee" -> 1.3;
            case "gun" -> 1.2;
            case "accuracy" -> 1 / .9;
            default -> 1;
        };
    }
    public static double bossAdd(Human human, String dimension) {
        return isBoss(human) && dimension.equals("knockback_resistance") ? .30 : 0;
    }
    public static SpawnGroupData vanillaSpawnData(SpawnGroupData group) {
        return group instanceof Group wrapped ? wrapped.delegate() : group;
    }
    public static SpawnGroupData completeSpawnData(SpawnGroupData group, SpawnGroupData vanilla) {
        return group instanceof Group wrapped ? new Group(wrapped.faction(), vanilla) : vanilla;
    }
    public static void set(Human human, String raw) {
        ResourceLocation key = FactionDefinitions.id(raw);
        human.getPersistentData().putString(FACTION, key == null ? "" : key.toString());
        refreshTeam(human);
    }
    public static void initialize(Human human, boolean fromDisk) {
        if (human.getTier() == HumanTier.ROAMER) { set(human, ""); return; }
        if (!human.getPersistentData().contains(FACTION, Tag.TAG_STRING)) {
            String legacy = fromDisk ? switch (human.team) {
                case "human_level2", "team2" -> "azure";
                case "human_level3" -> "verdant";
                default -> "crimson";
            } : "crimson";
            set(human, legacy);
        } else {
            // Preserve removed/unknown valid IDs; relationship lookup safely becomes neutral.
            String value = id(human);
            ResourceLocation key = FactionDefinitions.id(value);
            if (key != null) human.getPersistentData().putString(FACTION, key.toString());
            refreshTeam(human);
        }
    }
    public static SpawnGroupData onSpawn(Human human, MobSpawnType source, SpawnGroupData group, CompoundTag spawnTag) {
        if (spawnTag != null && spawnTag.contains("ForgeData", Tag.TAG_COMPOUND)) {
            CompoundTag explicit = spawnTag.getCompound("ForgeData");
            if (explicit.contains(FACTION, Tag.TAG_STRING)) human.getPersistentData().putString(FACTION, explicit.getString(FACTION));
        }
        if (!human.getPersistentData().contains(FACTION, Tag.TAG_STRING)
                && human.getTier() != HumanTier.ROAMER
                && (source == MobSpawnType.NATURAL || source == MobSpawnType.CHUNK_GENERATION)) {
            set(human, group instanceof Group inherited ? inherited.faction() : choose(human));
        }
        initialize(human, false);
        return group instanceof Group ? group : new Group(id(human), group);
    }
    private static String choose(Human human) {
        var eligible = FactionDefinitions.current().definitions().values().stream()
                .filter(d -> d.weight() > 0).sorted(java.util.Comparator.comparing(d -> d.id().toString())).toList();
        long total = eligible.stream().mapToLong(FactionDefinitions.Definition::weight).sum();
        if (total == 0) return "crimson";
        long roll = (long) (human.getRandom().nextDouble() * total);
        for (var definition : eligible) { roll -= definition.weight(); if (roll < 0) return definition.id().toString(); }
        return "crimson";
    }
    public static void refreshTeam(Human human) {
        if (human.getTier() == HumanTier.ROAMER) human.team = "human_roamer_" + human.getUUID();
        else if (enabled()) human.team = id(human).isEmpty() ? "" : "faction:" + id(human);
        else human.team = TierThreeHuman.isTierThree(human) ? "human_level3"
                : human.getTier() == HumanTier.LEVEL2 ? "human_level2" : "human_level1";
    }
}
