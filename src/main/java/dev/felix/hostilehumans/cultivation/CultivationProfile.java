package dev.felix.hostilehumans.cultivation;

import java.util.List;

/**
 * Immutable committed identity-independent training data; allocation_schema remains owned by
 * progression.
 */
public record CultivationProfile(
        long baseBudget,
        long budget,
        long potential,
        long used,
        long seed,
        long sequence,
        long revision,
        boolean breakthrough,
        boolean expanded,
        List<String> primary,
        double[] values) {
    public CultivationProfile {
        primary = List.copyOf(primary);
        values = values.clone();
        if (baseBudget <= 0
                || budget <= 0
                || potential < 0
                || potential > CultivationRules.MAX_POTENTIAL
                || used < 0
                || used > potential
                || sequence < 0
                || revision < 0
                || values.length != 13
                || primary.size() > (expanded ? 5 : 3)
                || new java.util.HashSet<>(primary).size() != primary.size())
            throw new IllegalArgumentException("Invalid cultivation profile");
        for (int i = 0; i < values.length; i++)
            if (!Double.isFinite(values[i])
                    || (i == 12 ? values[i] < 0 || values[i] > .45 : values[i] <= 0))
                throw new IllegalArgumentException("Invalid allocation");
    }

    @Override
    public double[] values() {
        return values.clone();
    }

    public long remaining() {
        return potential - used;
    }

    public boolean knownPrimary() {
        return CultivationRules.DIMENSIONS.containsAll(primary);
    }

    public CultivationProfile result(long nextBudget, long space, double[] allocation) {
        return new CultivationProfile(
                baseBudget,
                nextBudget,
                potential,
                Math.addExact(used, space),
                seed,
                sequence + 1,
                revision + 1,
                breakthrough,
                expanded,
                primary,
                allocation);
    }

    public CultivationProfile primary(List<String> next) {
        return new CultivationProfile(
                baseBudget,
                budget,
                potential,
                used,
                seed,
                sequence,
                revision + 1,
                breakthrough,
                expanded,
                next,
                values);
    }

    public CultivationProfile breakthrough(boolean next) {
        return new CultivationProfile(
                baseBudget,
                budget,
                potential,
                used,
                seed,
                sequence,
                revision + 1,
                next,
                expanded,
                primary,
                values);
    }

    public CultivationProfile extendPotential() {
        if (potential + CultivationRules.POTENTIAL_ITEM > CultivationRules.MAX_POTENTIAL)
            throw new IllegalStateException("Growth space limit");
        return new CultivationProfile(
                baseBudget,
                budget,
                potential + CultivationRules.POTENTIAL_ITEM,
                used,
                seed,
                sequence,
                revision + 1,
                breakthrough,
                expanded,
                primary,
                values);
    }

    public CultivationProfile expand() {
        if (expanded) throw new IllegalStateException("Already expanded");
        return new CultivationProfile(
                baseBudget,
                budget,
                potential,
                used,
                seed,
                sequence,
                revision + 1,
                breakthrough,
                true,
                primary,
                values);
    }
}
