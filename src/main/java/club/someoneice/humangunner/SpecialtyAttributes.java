package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

/** Stored allocation and effective utility. Reads never initialize or reroll an entity. */
public final class SpecialtyAttributes {
    public enum Dimension {
        HEALTH("health"), ARMOR("armor"), TOUGHNESS("toughness"), KNOCKBACK_RESISTANCE("knockback_resistance"),
        SPEED("speed"), DETECTION("detection"), MELEE("melee"), GUN("gun"), PROJECTILE("projectile"),
        ACCURACY("accuracy"), REGENERATION("regeneration"), ATTACK_SPEED("attack_speed"), ARMOR_PENETRATION("armor_penetration");
        private final String key;
        Dimension(String key) { this.key = key; }
        public String key() { return key; }
    }
    public static final int DIMENSIONS = 13;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private SpecialtyAttributes() { }

    /** Call after faction/boss identity is fixed and ordinary growth birth/load has completed. */
    public static void initialize(Human human, boolean newEntity) {
        if (human.level().isClientSide) return;
        CompoundTag state = human.getPersistentData().getCompound(HumanGrowth.STATE);
        if (!human.getPersistentData().contains(HumanGrowth.STATE, Tag.TAG_COMPOUND))
            human.getPersistentData().put(HumanGrowth.STATE, state);
        if (retainSaved(state)) return;
        if (!newEntity) {
            store(state, 1, baseline(), state.getLong("allocation_seed"));
            return;
        }
        long seed = human.getRandom().nextLong();
        RandomSource random = RandomSource.create(seed);
        SpecialtyConfig config = SpecialtyConfig.get();
        boolean boss = HumanFactions.isBoss(human);
        double budget = boss ? HumanFactions.bossBudget(human) : sampleBudget(random, config, TierAttributes.key(human));
        SpecialtyData.Definitions data = SpecialtyData.get();
        double[] tilts = ones();
        for (int i = 0; i < DIMENSIONS; i++) tilts[i] = Math.exp(random.nextGaussian() * config.deviationSigma());
        try {
            SpecialtyAllocation.Result result = SpecialtyAllocation.allocate(budget,
                    preference(human, boss), data.costs(), data.caps(config.deviationCap()), tilts,
                    config.capBudgetExponent(), config.multiplierMin(), HumanFactions.allocationMaximum(human));
            if (!result.feasible()) warn("budget", "Specialty total is outside merged boundaries; keeping boundaries, budget and explicit residual");
            store(state, budget, result.multipliers(), seed);
            // Float persistence is intentionally checked separately from the double solver.
            double total = 0, costs = 0;
            ListTag floats = state.getList("multipliers", Tag.TAG_FLOAT);
            double[] c = data.costs();
            costs = SpecialtyAllocation.baselineCost(c);
            for (int i = 0; i < DIMENSIONS; i++) { total += c[i] * floats.getFloat(i); }
            if (result.feasible() && Math.abs(total - budget * costs) > 2e-6 * Math.max(1, budget * costs))
                warn("float", "Specialty float persistence exceeded cost tolerance");
        } catch (IllegalArgumentException invalid) {
            // Empty intersections cannot preserve both boundary sets. Explicit neutral migration is safe.
            warn("bounds", "Invalid specialty parameters (" + invalid.getMessage() + "); using baseline allocation");
            store(state, 1, baseline(), seed);
        }
    }

    static double sampleBudget(RandomSource random, SpecialtyConfig config) {
        return SpecialtyBudgetSampler.sample(random.nextDouble(), config.budgetMean(), config.budgetSigma(),
                config.budgetMin(), config.budgetMax());
    }

    /** Truncate the base draw first; never clamp away the tier's additional budget afterwards. */
    static double sampleBudget(RandomSource random, SpecialtyConfig config, String tier) {
        return sampleBudget(random, config) * config.tierBudgetMultiplier(tier);
    }

    /** Returns true when a payload exists, including unknown future schemas which must be retained. */
    static boolean retainSaved(CompoundTag state) {
        ListTag saved = state.getList("multipliers", Tag.TAG_FLOAT);
        int schema = state.getInt("allocation_schema");
        if ((schema == 3 && saved.size() == DIMENSIONS)
                || (schema == 2 && (saved.size() == 10 || saved.size() == 11)) || schema == 1 && saved.size() == 10) {
            float budget = state.getFloat("budget");
            boolean valid = Float.isFinite(budget) && budget > 0;
            for (int i = 0; i < saved.size(); i++) {
                float value = saved.getFloat(i);
                valid &= Float.isFinite(value) && (i == Dimension.ARMOR_PENETRATION.ordinal() ? value >= 0 && value <= .45 : value > 0);
            }
            float accuracy = schema == 1 ? (float) (1.0 / saved.getFloat(9)) : saved.getFloat(9);
            valid &= Float.isFinite(accuracy) && accuracy > 0;
            if (valid) {
                if (schema == 1) saved.set(9, FloatTag.valueOf(accuracy));
                if (saved.size() == 10) saved.add(FloatTag.valueOf(1));
                if (saved.size() == 11) { saved.add(FloatTag.valueOf(1)); saved.add(FloatTag.valueOf(0)); }
                state.put("multipliers", saved);
                state.putInt("allocation_schema", 3);
                return true;
            }
        }
        if (state.contains("multipliers") || state.contains("allocation_schema")) {
            warn("migration", "Unknown/invalid specialty schema; preserving payload and using effective baseline");
            return true;
        }
        return false;
    }

    static void store(CompoundTag state, double budget, double[] values, long seed) {
        ListTag list = new ListTag();
        for (double value : values) list.add(FloatTag.valueOf((float) value));
        state.putFloat("budget", (float) budget);
        state.put("multipliers", list);
        state.putLong("allocation_seed", seed);
        state.putInt("allocation_schema", 3);
    }
    public static double storedMultiplier(Human human, Dimension dimension) {
        return storedMultiplier(human.getPersistentData().getCompound(HumanGrowth.STATE), dimension);
    }
    static double storedMultiplier(CompoundTag state, Dimension dimension) {
        double fallback = dimension == Dimension.ARMOR_PENETRATION ? 0 : 1;
        if (state.getInt("allocation_schema") != 3) return fallback;
        float budget = state.getFloat("budget");
        if (!Float.isFinite(budget) || budget <= 0) return fallback;
        ListTag list = state.getList("multipliers", Tag.TAG_FLOAT);
        if (list.size() != DIMENSIONS) return fallback;
        for (int i = 0; i < DIMENSIONS; i++) {
            float candidate = list.getFloat(i);
            if (!Float.isFinite(candidate) || (i == Dimension.ARMOR_PENETRATION.ordinal() ? candidate < 0 || candidate > .45 : candidate <= 0)) return fallback;
        }
        return list.getFloat(dimension.ordinal());
    }
    public static double multiplier(Human human, Dimension dimension) {
        return SpecialtyConfig.get().enabled() ? storedMultiplier(human, dimension) : (dimension == Dimension.ARMOR_PENETRATION ? 0 : 1);
    }
    public static double budget(Human human) {
        float value = human.getPersistentData().getCompound(HumanGrowth.STATE).getFloat("budget");
        return Float.isFinite(value) && value > 0 ? value : 1;
    }
    public static void apply(Human human) {
        if (!human.level().isClientSide) {
            TierAttributes.apply(human);
            GrowthHealth.apply(human);
        }
    }
    static double[] preference(Human human, boolean boss) {
        double[] original = HumanFactions.preference(human, boss);
        double[] values = baseline();
        System.arraycopy(original, 0, values, 0, Math.min(original.length, DIMENSIONS));
        values[Dimension.ARMOR_PENETRATION.ordinal()] = Math.max(values[Dimension.ARMOR_PENETRATION.ordinal()], SpecialtyConfig.get().penetrationSeed());
        return values;
    }
    static double[] baseline() { double[] values = ones(); values[Dimension.ARMOR_PENETRATION.ordinal()] = 0; return values; }
    static double[] ones() { double[] values = new double[DIMENSIONS]; java.util.Arrays.fill(values, 1); return values; }
    static void warn(String key, String text) { if (WARNED.add(key)) LOGGER.warn(text); }
}
