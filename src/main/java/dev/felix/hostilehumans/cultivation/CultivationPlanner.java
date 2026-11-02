package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.progression.SpecialtyAllocation;
import dev.felix.hostilehumans.progression.SpecialtyBounds;

import java.util.*;

/** Thirteen-dimensional candidates project current allocation onto the existing shared solver. */
public final class CultivationPlanner {
    public enum Mode {
        GROW,
        REDISTRIBUTE
    }

    public interface Policy {
        SpecialtyBounds.Bounds bounds(double budget);

        SpecialtyAllocation.Result redistribute(double budget, double[] direction);

        double[] costs();
    }

    public record Candidate(
            int index,
            String style,
            long nominalStep,
            long potentialCost,
            long materialMicro,
            long durationMillis,
            long successBudget,
            long failureBudget,
            double[] successValues,
            double[] failureValues,
            double[] lower,
            double[] upper) {
        public Candidate {
            successValues = successValues.clone();
            failureValues = failureValues.clone();
            lower = lower.clone();
            upper = upper.clone();
        }

        @Override
        public double[] successValues() {
            return successValues.clone();
        }

        @Override
        public double[] failureValues() {
            return failureValues.clone();
        }

        @Override
        public double[] lower() {
            return lower.clone();
        }

        @Override
        public double[] upper() {
            return upper.clone();
        }
    }

    private CultivationPlanner() {}

    public static List<Candidate> preview(
            CultivationProfile profile, Policy policy, long phaseBoundary, Mode mode) {
        if (!profile.knownPrimary())
            throw new IllegalStateException(
                    "Unknown primary dimension retained; training disabled");
        validateSource(profile, policy);
        long d =
                mode == Mode.REDISTRIBUTE
                        ? 0
                        : Math.min(
                                CultivationRules.STEP,
                                profile.remaining()
                                        * (profile.breakthrough() ? 2 : 1)
                                        / (profile.breakthrough() ? 3 : 1));
        if (mode == Mode.GROW && !profile.breakthrough())
            d = Math.min(d, Math.max(0, phaseBoundary - profile.budget()));
        if (mode == Mode.GROW) {
            if (d <= 0)
                throw new IllegalStateException(
                        profile.budget() >= phaseBoundary && !profile.breakthrough()
                                ? "Confirm breakthrough"
                                : "No growth space");
            long low = 0, high = d;
            while (low < high) {
                long mid = (low + high + 1) / 2;
                if (feasible(policy, profile.budget() + mid)) low = mid;
                else high = mid - 1;
            }
            d = low;
            if (d <= 0) throw new IllegalStateException("No feasible budget increment");
        }
        long successBudget = profile.budget() + d, failureBudget = profile.budget() + d * 4 / 10;
        long space = profile.breakthrough() ? (d * 3 + 1) / 2 : d;
        long material =
                mode == Mode.REDISTRIBUTE
                        ? CultivationRules.UNIT
                        : d * CultivationRules.UNIT / CultivationRules.STEP;
        long duration =
                mode == Mode.REDISTRIBUTE
                        ? CultivationRules.PERIOD_MILLIS
                        : Math.max(1, CultivationRules.PERIOD_MILLIS * d / CultivationRules.STEP);
        List<Candidate> result = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            double[] direction = direction(profile, index, mode, policy.costs());
            var success =
                    policy.redistribute((double) successBudget / CultivationRules.UNIT, direction);
            var failure =
                    mode == Mode.REDISTRIBUTE
                            ? success
                            : policy.redistribute(
                                    (double) failureBudget / CultivationRules.UNIT, direction);
            requireFeasible(success);
            requireFeasible(failure);
            result.add(
                    new Candidate(
                            index,
                            List.of("steady", "balanced", "focused").get(index),
                            d,
                            space,
                            material,
                            duration,
                            successBudget,
                            failureBudget,
                            success.multipliers(),
                            failure.multipliers(),
                            success.lower(),
                            success.upper()));
        }
        return List.copyOf(result);
    }

    public static long signature(Candidate c) {
        return CultivationRules.mix(
                c.successBudget()
                        ^ c.failureBudget()
                        ^ c.materialMicro()
                        ^ c.potentialCost()
                        ^ c.durationMillis()
                        ^ c.index()
                        ^ Arrays.hashCode(c.successValues())
                        ^ ((long) Arrays.hashCode(c.failureValues()) << 32)
                        ^ Arrays.hashCode(c.lower())
                        ^ ((long) Arrays.hashCode(c.upper()) << 16));
    }

    public static boolean succeeds(CultivationProfile profile) {
        return new Random(
                                CultivationRules.mix(
                                        profile.seed() ^ profile.sequence() ^ 0x524553554c545345L))
                        .nextDouble()
                < CultivationRules.SUCCESS;
    }

    private static boolean feasible(Policy policy, long budget) {
        try {
            var b = policy.bounds((double) budget / CultivationRules.UNIT);
            if (!b.intersects()) return false;
            double min = 0, max = 0;
            double[] costs = policy.costs();
            for (int i = 0; i < 13; i++) {
                min += costs[i] * b.lower()[i];
                max += costs[i] * b.upper()[i];
            }
            double target =
                    (double) budget
                            / CultivationRules.UNIT
                            * SpecialtyAllocation.baselineCost(costs);
            return target >= min - 1e-8 && target <= max + 1e-8;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    public static void validateCurrent(CultivationProfile p, Policy policy) {
        if (!feasible(policy, p.budget()))
            throw new IllegalStateException("Current allocation cannot enter current policy");
        validateSource(p, policy);
        var b = policy.bounds((double) p.budget() / CultivationRules.UNIT);
        double[] v = p.values(), lo = b.lower(), hi = b.upper();
        for (int i = 0; i < 13; i++)
            if (v[i] < lo[i] - 2e-6 || v[i] > hi[i] + 2e-6)
                throw new IllegalStateException("Allocation outside current bounds: " + i);
    }

    /**
     * Old individual values may exceed a newly narrowed cap; only an explicit paid preview projects
     * them.
     */
    public static void validateSource(CultivationProfile p, Policy policy) {
        if (!feasible(policy, p.budget()))
            throw new IllegalStateException("Current total budget cannot enter current policy");
        double sum = 0;
        double[] v = p.values(), c = policy.costs();
        for (int i = 0; i < 13; i++) sum += v[i] * c[i];
        double wanted =
                (double) p.budget() / CultivationRules.UNIT * SpecialtyAllocation.baselineCost(c);
        if (Math.abs(sum - wanted) > 2e-6 * Math.max(1, wanted))
            throw new IllegalStateException("Existing allocation cost mismatch");
    }

    private static void requireFeasible(SpecialtyAllocation.Result r) {
        if (!r.feasible() || Math.abs(r.residual()) > 1e-7)
            throw new IllegalStateException("Candidate infeasible");
    }

    private static double[] direction(CultivationProfile p, int index, Mode mode, double[] costs) {
        var random =
                new Random(
                        CultivationRules.mix(
                                p.seed() ^ p.sequence() ^ ((long) index << 32) ^ mode.ordinal()));
        double[] weights = new double[13], raw = p.values();
        boolean[] primary = new boolean[13];
        int n = 0;
        for (String id : p.primary()) {
            int i = CultivationRules.DIMENSIONS.indexOf(id);
            if (i >= 0) {
                primary[i] = true;
                n++;
            }
        }
        double mainSum = 0, otherSum = 0;
        for (int i = 0; i < 13; i++) {
            weights[i] = .25 + random.nextDouble();
            if (primary[i]) mainSum += weights[i];
            else otherSum += weights[i];
        }
        double mainShare = n == 0 ? 0 : new double[] {.60, .725, .85}[index];
        for (int i = 0; i < 13; i++) {
            double weight =
                    n == 0
                            ? 1D / 13
                            : weights[i]
                                    * (primary[i]
                                            ? mainShare / mainSum
                                            : (1 - mainShare) / otherSum);
            double addition = weight * (.10 + .10 * index) / costs[i];
            raw[i] = Math.max(1e-9, raw[i] + addition);
        }
        return raw;
    }
}
