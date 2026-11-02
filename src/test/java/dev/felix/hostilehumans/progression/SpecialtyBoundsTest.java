package dev.felix.hostilehumans.progression;

import java.util.Arrays;
import java.util.Random;

/**
 * Exercise the new per-dimension limits, feasibility and ownership through the public arithmetic
 * interface.
 */
public final class SpecialtyBoundsTest {
    private static final double[] COST = {
        1.2, 1.1, .5, .4, 1.2, .4, 1.25, 1.45, 1, .6, 1.1, 1.1, 1.2
    };
    private static final double[][] PREF = {
        {1, .9, 1, 1.5, 1.1, .6, 1.4, .8, 1.35, 1.1, 1.2, 1.1, .1},
        {1.3, 1.3, 1.3, 1.8, .9, .9, 1.1, .85, 1.25, 1.15, 1.5, 1, .05},
        {.8, 1, 1.2, 1.3, 1.15, 1.2, 1, 1.2, 1.5, 1.25, 1.35, 1.4, .12},
        {.7, 1.4, 1.1, 1.4, 1.2, 1.2, 1.1, .9, 1.65, 1.2, 1.4, 1.6, .21}
    };

    public static void main(String[] args) {
        var limits = limits();
        double[] copy = limits.maximum();
        copy[0] = .6;
        require(Double.isInfinite(limits.maximum()[0]), "absolute bound arrays are immutable");
        var widened =
                SpecialtyBounds.at(
                        2, SpecialtyAllocation.center(PREF[0], COST), caps(1), .6, limits);
        require(
                widened.upper()[6] > 1.8,
                "high melee preference no longer hits a shared scalar ceiling");
        require(
                widened.upper()[4] <= 1.8 && widened.upper()[12] <= .45,
                "speed and penetration limits remain");
        var impossible =
                SpecialtyBounds.at(
                        5.5, SpecialtyAllocation.center(PREF[0], COST), caps(0), .6, limits);
        require(
                !impossible.intersects(),
                "a lower bound above speed ceiling is explicitly an empty intersection");
        Random random = new Random(0xBA5E2026);
        int samples = 0;
        for (int faction = 0; faction < PREF.length; faction++)
            for (int kind = 0; kind < 3; kind++) {
                double budget = kind == 2 ? 1.848 : kind == 1 ? 2.0 : 1.32;
                for (int n = 0; n < 400; n++) {
                    double[] tilts = new double[13];
                    for (int i = 0; i < 13; i++) tilts[i] = Math.exp(random.nextGaussian() * .4);
                    var result =
                            SpecialtyAllocation.allocate(
                                    budget, PREF[faction], COST, caps(kind), tilts, .6, limits);
                    require(result.feasible(), "default training and boss budgets remain feasible");
                    double spent = 0;
                    for (int i = 0; i < 13; i++) {
                        double value = result.multipliers()[i];
                        require(
                                Double.isFinite(value)
                                        && value >= result.lower()[i] - 1e-9
                                        && value <= result.upper()[i] + 1e-9,
                                "each result respects its merged boundary");
                        spent += value * COST[i];
                    }
                    close(spent, budget * 11.3, 1e-7, "budget conserved");
                    samples++;
                }
            }
        double[] tilts = new double[13];
        Arrays.fill(tilts, 1);
        var red = SpecialtyAllocation.allocate(2.70, PREF[0], COST, caps(1), tilts, .6, limits);
        var blue = SpecialtyAllocation.allocate(2.70, PREF[1], COST, caps(1), tilts, .6, limits);
        require(
                !red.feasible() && red.residual() < 0,
                "red target above actual merged capacity is not reported as success");
        require(blue.feasible(), "the feasible ceiling is faction-dependent, not a global 3.05");
        rejects(() -> new SpecialtyBounds.Limits(new double[] {2}, new double[] {1}));
        rejects(
                () ->
                        SpecialtyBounds.at(
                                1,
                                new double[] {1},
                                new double[] {1.01},
                                .6,
                                new SpecialtyBounds.Limits(
                                        new double[] {.5},
                                        new double[] {Double.POSITIVE_INFINITY})));
        System.out.println(
                "SpecialtyBoundsTest: "
                        + samples
                        + " randomized allocations and feasibility/ownership checks passed");
    }

    private static SpecialtyBounds.Limits limits() {
        double[] lo = new double[13], hi = new double[13];
        Arrays.fill(lo, .5);
        Arrays.fill(hi, Double.POSITIVE_INFINITY);
        lo[12] = 0;
        hi[4] = 1.8;
        hi[12] = .45;
        return new SpecialtyBounds.Limits(lo, hi);
    }

    private static double[] caps(int kind) {
        double[] values = new double[13];
        Arrays.fill(values, kind == 2 ? .9 : kind == 1 ? .55 : .45);
        values[4] = kind == 2 ? .4 : kind == 1 ? .3 : .2;
        values[7] = kind == 2 ? .6 : kind == 1 ? .4 : .3;
        values[9] = kind == 2 ? 1 : kind == 1 ? .6 : .5;
        return values;
    }

    private static void close(double a, double b, double tolerance, String text) {
        require(Math.abs(a - b) < tolerance, text);
    }

    private static void require(boolean value, String text) {
        if (!value) throw new AssertionError(text);
    }

    private static void rejects(Runnable task) {
        try {
            task.run();
            throw new AssertionError("invalid limits accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
