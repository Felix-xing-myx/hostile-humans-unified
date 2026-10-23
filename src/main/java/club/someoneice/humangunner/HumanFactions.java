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
    public static final float BOSS_BODY_SCALE = 1.2F;
    public static final String BOSS = "hostile_humans_unified:boss";
    private HumanFactions() {}
    private record Group(String faction, SpawnGroupData delegate) implements SpawnGroupData {}

    public static String id(Human human) { return human.level().isClientSide ? human.humanGunner$getFaction() : human.getPersistentData().getString(FACTION); }
    /** Network/display boundary only; never normalize or discard the stored identity on reads. */
    public static String wireId(String raw) {
        return raw != null && raw.length() <= 256 && (raw.isEmpty() || FactionDefinitions.id(raw) != null) ? raw : "";
    }
    public static String shortId(Human human) {
        if (human.getTier() == HumanTier.ROAMER) return "roamer";
        ResourceLocation key = FactionDefinitions.id(id(human));
        return key != null && key.getNamespace().equals(HumanGunner.MOD_ID) ? key.getPath() : id(human);
    }
    public static boolean enabled() { return UnifiedConfig.get().featureEnabled("factions", true); }
    public static boolean isBoss(Human human) { return human.level().isClientSide ? human.humanGunner$isBoss() : human.getPersistentData().getBoolean(BOSS); }
    public static boolean same(Human a, Human b) {
        if (a.getTier() == HumanTier.ROAMER || b.getTier() == HumanTier.ROAMER) return false;
        if (!enabled()) return TierAttributes.key(a).equals(TierAttributes.key(b));
        return FactionDefinitions.allies(id(a), id(b));
    }
    public static boolean enemies(Human a, Human b) {
        if (a == b) return false;
        if (a.getTier() == HumanTier.ROAMER || b.getTier() == HumanTier.ROAMER) return true;
        if (!enabled()) return !TierAttributes.key(a).equals(TierAttributes.key(b));
        return FactionDefinitions.enemies(id(a), id(b));
    }
    public static double[] preference(Human human, boolean boss) {
        FactionDefinitions.Definition definition = FactionDefinitions.find(id(human));
        if (definition == null) {
            double[] neutral = new double[FactionDefinitions.DIMENSIONS.size()]; Arrays.fill(neutral, 1);
            neutral[FactionDefinitions.DIMENSIONS.indexOf("armor_penetration")] = 0;
            return neutral;
        }
        return (boss ? definition.bossPreference() : definition.preference()).stream().mapToDouble(Double::doubleValue).toArray();
    }
    public static double bossBase(Human human, String dimension) {
        if (!isBoss(human)) return 1;
        return UnifiedConfig.get().factionRules().bossBases().getOrDefault(dimension, 1.0);
    }
    public static double bossBudget(Human human) {
        return UnifiedConfig.get().factionRules().bossBudget(SpecialtyConfig.get(), TierAttributes.key(human));
    }
    public static double allocationMaximum(Human human) {
        var specialty = SpecialtyConfig.get();
        return isBoss(human) ? UnifiedConfig.get().factionRules().allocationMaximum(specialty) : specialty.multiplierMax();
    }
    public static double bossSize(Human human) { return isBoss(human) ? BOSS_BODY_SCALE : 1; }
    public static double bossAdd(Human human, String dimension) {
        return isBoss(human) && dimension.equals("knockback_resistance") ? UnifiedConfig.get().factionRules().bossKnockbackAdd() : 0;
    }

    /** A natural pack shares one faction; placement may yield fewer members than the upper bound. */
    public static int naturalPackLimit(Human human) {
        if (human.getTier() == HumanTier.ROAMER) return 1;
        return enabled() && shortId(human).equals("ashen") ? 2 : 5;
    }

    public static SpawnGroupData vanillaSpawnData(SpawnGroupData group) {
        return group instanceof Group wrapped ? wrapped.delegate() : group;
    }
    public static SpawnGroupData completeSpawnData(SpawnGroupData group, SpawnGroupData vanilla) {
        return group instanceof Group wrapped ? new Group(wrapped.faction(), vanilla) : vanilla;
    }
    public static void set(Human human, String raw) {
        if (human instanceof FactionBossHuman boss) raw = boss.fixedFaction();
        ResourceLocation key = FactionDefinitions.id(raw);
        human.getPersistentData().putString(FACTION, key == null ? "" : key.toString());
        refreshTeam(human);
    }
    public static void initialize(Human human, boolean fromDisk) {
        if (human instanceof FactionBossHuman boss) boss.restoreIdentity();
        if (human.getTier() == HumanTier.ROAMER) { set(human, ""); return; }
        if (!human.getPersistentData().contains(FACTION, Tag.TAG_STRING)) {
            String legacy = fromDisk && UnifiedConfig.get().factionRules().legacyMapping() ? switch (human.team) {
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
        CompoundTag entityTag = spawnTag != null && spawnTag.contains("EntityTag", Tag.TAG_COMPOUND)
                ? spawnTag.getCompound("EntityTag") : spawnTag;
        if (entityTag != null && (entityTag.contains("HandItems") || entityTag.contains("ArmorItems")))
            HumanSpawnEquipment.preserveAuthored(human);
        if (entityTag != null && entityTag.contains("ForgeData", Tag.TAG_COMPOUND)) {
            CompoundTag explicit = entityTag.getCompound("ForgeData");
            if (explicit.contains(FACTION, Tag.TAG_STRING)) human.getPersistentData().putString(FACTION, explicit.getString(FACTION));
        }
        if (!human.getPersistentData().contains(FACTION, Tag.TAG_STRING)
                && human.getTier() != HumanTier.ROAMER
                && (source == MobSpawnType.NATURAL || source == MobSpawnType.CHUNK_GENERATION)) {
            set(human, group instanceof Group inherited ? inherited.faction() : chooseFaction(human.getRandom()));
        }
        initialize(human, false);
        return group instanceof Group ? group : new Group(id(human), group);
    }
    public static String[] enemyPair(net.minecraft.util.RandomSource random) {
        var definitions = FactionDefinitions.current().definitions().values().stream()
                .filter(d -> d.weight() > 0).sorted(java.util.Comparator.comparing(d -> d.id().toString())).toList();
        var firsts = definitions.stream().filter(a -> definitions.stream()
                .anyMatch(b -> FactionDefinitions.enemies(a.id().toString(), b.id().toString()))).toList();
        if (firsts.isEmpty()) return new String[0];
        FactionDefinitions.Definition first = weighted(firsts, random);
        var seconds = definitions.stream().filter(b -> FactionDefinitions.enemies(first.id().toString(), b.id().toString())).toList();
        return new String[]{first.id().toString(), weighted(seconds, random).id().toString()};
    }
    private static FactionDefinitions.Definition weighted(java.util.List<FactionDefinitions.Definition> eligible,
                                                         net.minecraft.util.RandomSource random) {
        long total = eligible.stream().mapToLong(FactionDefinitions.Definition::weight).sum();
        long roll = (long) (random.nextDouble() * total);
        for (var definition : eligible) { roll -= definition.weight(); if (roll < 0) return definition; }
        return eligible.get(eligible.size() - 1);
    }
    public static String chooseFaction(net.minecraft.util.RandomSource random) {
        var eligible = FactionDefinitions.current().definitions().values().stream()
                .filter(d -> d.weight() > 0).sorted(java.util.Comparator.comparing(d -> d.id().toString())).toList();
        return eligible.isEmpty() ? "crimson" : weighted(eligible, random).id().toString();
    }

    public static void refreshTeam(Human human) {
        if (human.level().isClientSide) return;
        human.humanGunner$syncIdentity(wireId(id(human)), isBoss(human));
        if (human.getTier() == HumanTier.ROAMER) human.team = "human_roamer_" + human.getUUID();
        else if (enabled()) human.team = id(human).isEmpty() ? "" : "faction:" + id(human);
        else human.team = TierThreeHuman.isTierThree(human) ? "human_level3"
                : human.getTier() == HumanTier.LEVEL2 ? "human_level2" : "human_level1";
    }
}
