package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.HumanFactions;
import dev.felix.hostilehumans.progression.DefenseAttributes;
import dev.felix.hostilehumans.progression.SpecialtyAllocation;
import dev.felix.hostilehumans.progression.SpecialtyAttributes;
import dev.felix.hostilehumans.progression.SpecialtyConfig;
import dev.felix.hostilehumans.progression.SpecialtyData;
import dev.felix.hostilehumans.progression.TierAttributes;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import java.util.Arrays;

/** Read-only panel comparison: current faction/rank/identity centre, never a population mean.
 * Uses the actual constrained allocator once per requested snapshot, without random draws. */
public final class AttributeReference {
    public record Centre(double[] allocation, boolean known) { }
    private AttributeReference() { }

    public static Centre of(Human human) {
        var config = SpecialtyConfig.get();
        if (!config.enabled()) return new Centre(SpecialtyAttributes.baseline(), true);
        double budget = HumanFactions.isBoss(human) ? HumanFactions.bossBudget(human)
                : ordinaryBudget(config, TierAttributes.key(human));
        var data = SpecialtyData.get();
        try {
            double[] tilts = new double[data.costs().length];
            Arrays.fill(tilts, 1);
            var result = dev.felix.hostilehumans.progression.SpecialtyPolicy.birth(human).allocate(budget, tilts);
            return new Centre(result.multipliers(), result.feasible());
        } catch (IllegalArgumentException invalid) {
            // Bad custom boundaries must not crash inspection or manufacture a meaningful comparison.
            return new Centre(SpecialtyAttributes.baseline(), false);
        }
    }

    static double ordinaryBudget(SpecialtyConfig config, String tier) {
        return config.budgetMean() * config.tierBudgetMultiplier(tier);
    }

    static double[] allocate(double budget, double[] preferences, double[] costs, double[] caps,
            SpecialtyConfig config, double maximum) {
        double[] neutralTilts = new double[costs.length];
        Arrays.fill(neutralTilts, 1);
        return SpecialtyAllocation.allocate(budget, preferences, costs, caps, neutralTilts,
                config.capBudgetExponent(), config.multiplierMin(), maximum).multipliers();
    }

    /** Same native modifiers and sanitization, but replace the owned allocation's base input.
     * Never temporarily mutate the live attribute to obtain a hypothetical value. */
    public static double physicalValue(AttributeInstance attribute, double replacementBase) {
        if (attribute == null) return replacementBase;
        return attribute.getAttribute().sanitizeValue(DefenseAttributes.projectRaw(attribute, replacementBase, null));
    }
}
