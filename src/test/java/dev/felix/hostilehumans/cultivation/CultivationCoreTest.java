package dev.felix.hostilehumans.cultivation;

import dev.felix.hostilehumans.progression.SpecialtyAllocation;
import dev.felix.hostilehumans.progression.SpecialtyBounds;

import java.util.*;

/**
 * Real solver, partial periods, resource conservation, deterministic retry and runtime pause
 * boundaries.
 */
public final class CultivationCoreTest {
    private static void check(boolean b, String m) {
        if (!b) throw new AssertionError(m);
    }

    private static void near(double a, double b) {
        check(Math.abs(a - b) < 1e-6, a + " != " + b);
    }

    private static final double[] COSTS = {
        1.2, 1.1, .5, .4, 1.2, .4, 1.25, 1.45, 1, .6, 1.1, 1.1, 1.2
    };

    static CultivationPlanner.Policy policy(boolean breakthrough) {
        double[] p = new double[13],
                caps = new double[13],
                min = new double[13],
                max = new double[13];
        Arrays.fill(p, 1);
        p[12] = .01;
        Arrays.fill(caps, breakthrough ? .55 : .45);
        caps[4] = breakthrough ? .30 : .20;
        caps[7] = breakthrough ? .40 : .30;
        caps[9] = breakthrough ? .60 : .50;
        Arrays.fill(min, .5);
        min[12] = 0;
        Arrays.fill(max, Double.POSITIVE_INFINITY);
        max[4] = 1.8;
        max[12] = .45;
        var limits = new SpecialtyBounds.Limits(min, max);
        double[] centre = SpecialtyAllocation.center(p, COSTS);
        return new CultivationPlanner.Policy() {
            public SpecialtyBounds.Bounds bounds(double b) {
                return SpecialtyBounds.at(b, centre, caps, .6, limits);
            }

            public double[] costs() {
                return COSTS.clone();
            }

            public SpecialtyAllocation.Result redistribute(double b, double[] direction) {
                var x = bounds(b);
                return SpecialtyAllocation.solve(
                        direction,
                        COSTS,
                        x.lower(),
                        x.upper(),
                        b * SpecialtyAllocation.baselineCost(COSTS));
            }
        };
    }

    static CultivationProfile profile(long budget, long space, boolean breakthrough) {
        var policy = policy(breakthrough);
        double[] raw = new double[13];
        Arrays.fill(raw, 1);
        raw[12] = .01;
        var allocation = policy.redistribute(budget / 1e6, raw);
        return new CultivationProfile(
                budget,
                budget,
                space,
                0,
                551,
                0,
                0,
                breakthrough,
                false,
                List.of("health", "gun"),
                allocation.multipliers());
    }

    public static void main(String[] args) {
        double[] legacyValues = new double[13];
        Arrays.fill(legacyValues, 1);
        legacyValues[12] = 0;
        var legacy =
                new CultivationProfile(
                        1_000_000,
                        1_000_000,
                        600_000,
                        0,
                        42,
                        0,
                        0,
                        false,
                        false,
                        List.of(),
                        legacyValues);
        var corrected =
                CultivationPlanner.preview(
                                legacy,
                                policy(false),
                                1_400_000,
                                CultivationPlanner.Mode.REDISTRIBUTE)
                        .get(0);
        check(
                legacy.values()[12] == 0 && corrected.successValues()[12] > 0,
                "Legacy zero penetration remains untouched until explicit paid redistribution");
        CultivationPlanner.validateCurrent(
                legacy.result(legacy.budget(), 0, corrected.successValues()), policy(false));
        var retryCredit = new TrainingCredit(1, 0, 0);
        for (int i = 0; i < 100; i++) retryCredit = retryCredit.reserve(300000).refund(300000);
        check(
                retryCredit.stock() == 0
                        && retryCredit.available() == 1_000_000
                        && retryCredit.reserved() == 0,
                "Repeated cancel does not mint stock or credit");
        var p = profile(1_370_000, 600_000, false);
        var candidates =
                CultivationPlanner.preview(
                        p, policy(false), 1_400_000, CultivationPlanner.Mode.GROW);
        check(candidates.size() == 3, "Three candidates");
        var c = candidates.get(0);
        check(
                c.nominalStep() == 30_000
                        && c.potentialCost() == 30_000
                        && c.materialMicro() == 300_000
                        && c.durationMillis() == 180_000,
                "Phase remainder scales all costs/time");
        for (var candidate : candidates) {
            near(
                    Arrays.stream(candidate.successValues()).sum(),
                    Arrays.stream(
                                    CultivationPlanner.preview(
                                                    p,
                                                    policy(false),
                                                    1_400_000,
                                                    CultivationPlanner.Mode.GROW)
                                            .get(candidate.index())
                                            .successValues())
                            .sum());
            double total = 0;
            for (int i = 0; i < 13; i++) {
                total += candidate.successValues()[i] * COSTS[i];
                check(
                        candidate.successValues()[i] >= candidate.lower()[i] - 1e-8
                                && candidate.successValues()[i] <= candidate.upper()[i] + 1e-8,
                        "Bounds");
            }
            near(total, 1.4 * SpecialtyAllocation.baselineCost(COSTS));
        }
        var ledger = new TrainingCredit(1, 0, 0).reserve(c.materialMicro());
        check(
                ledger.stock() == 0
                        && ledger.available() == 700_000
                        && ledger.reserved() == 300_000,
                "One physical item converts into owner credit");
        var refunded = ledger.refund(300_000);
        check(
                refunded.stock() == 0 && refunded.available() == 1_000_000,
                "Cancel never manufactures physical items");
        try {
            new TrainingCredit(0, 0, 0).reserve(1);
            throw new AssertionError();
        } catch (IllegalStateException expected) {
        }
        var cycle =
                CultivationCycle.create(
                        UUID.randomUUID(), p, CultivationPlanner.Mode.GROW, c, 100, true);
        check(cycle.accrue(100_000).elapsed() == 0, "Moving is not training");
        cycle = cycle.arrived(100);
        cycle = cycle.accrue(60_100).pause(60_100, "attacked");
        check(
                cycle.status() == CultivationCycle.Status.PAUSED && cycle.elapsed() == 60_000,
                "Attack freezes progress");
        check(cycle.accrue(1_000_000).elapsed() == 60_000, "Paused catchup forbidden");
        cycle = cycle.resume(1_000_000).arrived(1_000_000).accrue(1_120_000);
        check(
                cycle.status() == CultivationCycle.Status.READY,
                "Only current confirmed period finishes");
        var result = cycle.result();
        check(
                result.sequence() == 1 && result.used() == 30_000,
                "Exactly one period charges nominal potential");
        check(
                result.budget() == (cycle.success() ? 1_400_000 : 1_382_000),
                "Success vs40% failure");
        check(
                CultivationCycle.create(
                                        UUID.randomUUID(),
                                        p,
                                        CultivationPlanner.Mode.GROW,
                                        c,
                                        7,
                                        false)
                                .success()
                        == cycle.success(),
                "Cancellation/retry cannot reroll same sequence");
        var r = profile(1_400_000, 150_001, true);
        var b =
                CultivationPlanner.preview(r, policy(true), 1_400_000, CultivationPlanner.Mode.GROW)
                        .get(0);
        check(
                b.nominalStep() == 100_000 && b.potentialCost() == 150_000,
                "Stage2 costs1.5, not output1.5");
        var redistribution =
                CultivationPlanner.preview(
                                p, policy(false), 1_400_000, CultivationPlanner.Mode.REDISTRIBUTE)
                        .get(0);
        check(
                redistribution.potentialCost() == 0
                        && redistribution.successBudget() == p.budget()
                        && redistribution.materialMicro() == 1_000_000,
                "No free reroll");
        var clock = new ActiveRuntimeClock(500);
        clock.advance(1_000_000_000L);
        near(clock.advance(1_800_000_000L), 1300);
        clock.paused(2_000_000_000L, true);
        clock.advance(20_000_000_000L);
        clock.paused(21_000_000_000L, false);
        near(clock.advance(21_100_000_000L), 1600);
        clock.advance(20_000_000_000L);
        near(clock.advance(21_200_000_000L), 1700);
        clock.stop();
        clock.advance(100_000_000_000L);
        near(clock.current(), 1700);
        // Long active ticks count actual runtime; no threshold is mistaken for pause.
        near(clock.advance(105_000_000_000L), 6700);
        for (int i = 0; i < 1000; i++) {
            long v = CultivationRules.potential(i);
            check(v > 0 && v <= 6_000_000, "Conditional truncated space");
            check(v == CultivationRules.potential(i), "Stable migration seed");
        }
        System.out.println("Cultivation core solver/resources/period/clock passed");
    }
}
