package dev.felix.hostilehumans.progression;

import dev.felix.hostilehumans.cultivation.CultivationPlanner;
import dev.felix.hostilehumans.cultivation.CultivationProfile;
import dev.felix.hostilehumans.cultivation.CultivationRules;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanFactions;

import net.minecraft.nbt.*;

import java.util.*;

/**
 * The only cultivation writer of progression allocations; birth and unknown payloads are preserved.
 */
public final class SpecialtyTrainingBridge {
    private SpecialtyTrainingBridge() {}

    public static CultivationProfile read(Human human) {
        var root = human.getPersistentData();
        var t = root.getCompound(HumanGrowth.STATE);
        if (t.getInt("allocation_schema") != 3
                || t.getList("multipliers", Tag.TAG_FLOAT).size() != 13)
            throw new IllegalStateException("Unknown allocation schema; training disabled");
        double[] values = new double[13];
        var list = t.getList("multipliers", Tag.TAG_FLOAT);
        for (int i = 0; i < 13; i++) values[i] = list.getFloat(i);
        for (int i = 0; i < 13; i++)
            if (!Double.isFinite(values[i])
                    || (i == 12 ? values[i] < 0 || values[i] > .45 : values[i] <= 0))
                throw new IllegalStateException(
                        "Invalid saved allocation retained; training disabled");
        long budget = CultivationRules.micro(t.getFloat("budget"));
        if (budget <= 0) throw new IllegalStateException("Missing allocation budget");
        if (!t.contains("cultivation_schema")) {
            var next = t.copy();
            long seed = CultivationRules.seed(human.getUUID(), t.getLong("allocation_seed"));
            next.putInt("cultivation_schema", 1);
            next.putDouble("budget_base", budget / 1e6);
            next.putDouble("growth_potential", CultivationRules.potential(seed) / 1e6);
            next.putDouble("growth_used", 0);
            next.putLong("cultivation_seed", seed);
            next.putLong("cultivation_sequence", 0);
            next.putLong("cultivation_revision", 0);
            root.put(HumanGrowth.STATE, next);
            t = next;
        }
        if (t.getInt("cultivation_schema") != 1)
            throw new IllegalStateException("Unknown cultivation schema retained");
        List<String> primary = new ArrayList<>();
        for (Tag name : t.getList("cultivation_primary", Tag.TAG_STRING))
            primary.add(name.getAsString());
        return new CultivationProfile(
                CultivationRules.micro(t.getDouble("budget_base")),
                budget,
                CultivationRules.micro(t.getDouble("growth_potential")),
                CultivationRules.micro(t.getDouble("growth_used")),
                t.getLong("cultivation_seed"),
                t.getLong("cultivation_sequence"),
                t.getLong("cultivation_revision"),
                t.getBoolean("breakthrough"),
                t.getBoolean("cultivation_expanded"),
                primary,
                values);
    }

    public static CultivationPlanner.Policy policy(Human human, boolean breakthrough) {
        var policy = SpecialtyPolicy.atStage(human, breakthrough);
        return new CultivationPlanner.Policy() {
            public SpecialtyBounds.Bounds bounds(double b) {
                return policy.bounds(b);
            }

            public SpecialtyAllocation.Result redistribute(double b, double[] v) {
                return policy.redistribute(b, v);
            }

            public double[] costs() {
                return policy.costs();
            }
        };
    }

    public static long phaseBoundary(Human human) {
        return CultivationRules.micro(
                HumanFactions.isBoss(human)
                        ? HumanFactions.bossBudget(human)
                        : SpecialtyConfig.get().budgetMax()
                                * SpecialtyConfig.get()
                                        .tierBudgetMultiplier(TierAttributes.key(human)));
    }

    public static void apply(Human human, CultivationProfile expected, CultivationProfile next) {
        if (human.level().isClientSide || !human.isAlive() || human.isRemoved())
            throw new IllegalStateException("Soldier unavailable");
        var current = read(human);
        if (current.revision() == next.revision()) {
            if (current.budget() != next.budget()
                    || current.baseBudget() != next.baseBudget()
                    || current.potential() != next.potential()
                    || current.used() != next.used()
                    || current.sequence() != next.sequence()
                    || current.seed() != next.seed()
                    || current.breakthrough() != next.breakthrough()
                    || current.expanded() != next.expanded()
                    || !current.primary().equals(next.primary())
                    || !close(current.values(), next.values()))
                throw new IllegalStateException("Conflicting committed cultivation revision");
            return;
        }
        if (current.revision() != expected.revision()
                || current.budget() != expected.budget()
                || !close(current.values(), expected.values()))
            throw new IllegalStateException("Stale cultivation allocation");
        // Parameter/rare-item updates preserve the old allocation, including any historical
        // out-of-cap dimension.
        // New numerical allocations always pass all merged per-dimension bounds before the write.
        if (next.budget() == current.budget() && close(next.values(), current.values()))
            CultivationPlanner.validateSource(next, policy(human, next.breakthrough()));
        else CultivationPlanner.validateCurrent(next, policy(human, next.breakthrough()));
        var root = human.getPersistentData();
        var t = root.getCompound(HumanGrowth.STATE).copy();
        var values = new ListTag();
        for (double v : next.values()) values.add(FloatTag.valueOf((float) v));
        t.put("multipliers", values);
        t.putFloat("budget", (float) (next.budget() / 1e6));
        t.putDouble("budget_base", next.baseBudget() / 1e6);
        t.putDouble("growth_potential", next.potential() / 1e6);
        t.putDouble("growth_used", next.used() / 1e6);
        t.putBoolean("breakthrough", next.breakthrough());
        t.putBoolean("cultivation_expanded", next.expanded());
        t.putInt("cultivation_schema", 1);
        t.putLong("cultivation_seed", next.seed());
        t.putLong("cultivation_sequence", next.sequence());
        t.putLong("cultivation_revision", next.revision());
        var primary = new ListTag();
        for (String id : next.primary()) primary.add(StringTag.valueOf(id));
        t.put("cultivation_primary", primary);
        float health = human.getHealth(), maximum = human.getMaxHealth();
        var original = root.getCompound(HumanGrowth.STATE).copy();
        try {
            root.put(HumanGrowth.STATE, t);
            SpecialtyAttributes.apply(human);
            if (maximum > 0 && health > 0)
                human.setHealth(
                        Math.min(human.getMaxHealth(), health / maximum * human.getMaxHealth()));
        } catch (RuntimeException failure) {
            root.put(HumanGrowth.STATE, original);
            try {
                SpecialtyAttributes.apply(human);
                if (health > 0) human.setHealth(Math.min(health, human.getMaxHealth()));
            } catch (RuntimeException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
    }

    private static boolean close(double[] a, double[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 2e-6) return false;
        return true;
    }
}
