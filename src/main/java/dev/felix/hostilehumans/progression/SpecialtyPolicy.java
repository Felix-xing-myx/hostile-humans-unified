package dev.felix.hostilehumans.progression;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanFactions;

/**
 * Immutable effective allocation policy shared by birth, training and the server detail snapshot.
 */
public final class SpecialtyPolicy {
    private final double[] preference, costs, caps;
    private final double exponent;
    private final SpecialtyBounds.Limits limits;

    private SpecialtyPolicy(Human human, boolean breakthrough) {
        var config = SpecialtyConfig.get();
        var definitions = SpecialtyData.get();
        boolean boss = HumanFactions.isBoss(human);
        preference = SpecialtyAttributes.preference(human, boss);
        costs = definitions.costs();
        caps =
                definitions.caps(
                        config.deviationCap(),
                        UnifiedConfig.get().factionRules().bossDeviationCap(),
                        boss,
                        breakthrough);
        limits = definitions.limits();
        exponent = config.capBudgetExponent();
    }

    public static SpecialtyPolicy birth(Human human) {
        return new SpecialtyPolicy(human, false);
    }

    /** Read-only preview of a proposed stage; never writes the individual's breakthrough flag. */
    public static SpecialtyPolicy atStage(Human human, boolean breakthrough) {
        return new SpecialtyPolicy(human, breakthrough);
    }

    public static SpecialtyPolicy current(Human human) {
        return new SpecialtyPolicy(
                human,
                human.getPersistentData()
                        .getCompound(HumanGrowth.STATE)
                        .getBoolean("breakthrough"));
    }

    public SpecialtyBounds.Bounds bounds(double budget) {
        return SpecialtyBounds.at(
                budget, SpecialtyAllocation.center(preference, costs), caps, exponent, limits);
    }

    public SpecialtyAllocation.Result allocate(double budget, double[] tilts) {
        return SpecialtyAllocation.allocate(
                budget, preference, costs, caps, tilts, exponent, limits);
    }

    /**
     * Positive directions may originate from the current allocation; this does not reroll an
     * entity.
     */
    public SpecialtyAllocation.Result redistribute(double budget, double[] direction) {
        var bounds = bounds(budget);
        return SpecialtyAllocation.solve(
                direction,
                costs,
                bounds.lower(),
                bounds.upper(),
                budget * SpecialtyAllocation.baselineCost(costs));
    }

    public double[] costs() {
        return costs.clone();
    }

    public double[] caps() {
        return caps.clone();
    }
}
