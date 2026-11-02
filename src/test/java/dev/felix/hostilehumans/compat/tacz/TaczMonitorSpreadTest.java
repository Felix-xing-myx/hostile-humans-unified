package dev.felix.hostilehumans.compat.tacz;

import java.util.Random;

/** Measures the real policy distribution and transition without a world or optional dependency. */
public final class TaczMonitorSpreadTest {
    private static void near(double actual, double expected, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance)
            throw new AssertionError(actual + " != " + expected);
    }
    public static void main(String[] args) {
        near(TaczMonitorSpread.standardDeviation(0, 0, 1), 3, 0);
        near(TaczMonitorSpread.standardDeviation(0, 4, 1), 5, 0);
        near(TaczMonitorSpread.standardDeviation(.5F, 0, 1), .75, 0);
        near(TaczMonitorSpread.standardDeviation(0, 0, .5), 1.5, 0);
        near(TaczMonitorSpread.standardDeviation(1, 4, 2), 0, 0);
        double previous = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 100; step++) {
            double next = TaczMonitorSpread.standardDeviation(step / 100F, .8, 1.1);
            if (next > previous || next < 0) throw new AssertionError("ADS must continuously reduce the penalty");
            previous = next;
        }
        var zero = TaczMonitorSpread.sample(0, () -> { throw new AssertionError("ADS must not draw extra randomness"); });
        near(zero.pitch(), 0, 0); near(zero.yaw(), 0, 0);
        var limit = TaczMonitorSpread.sample(3, () -> 100);
        near(limit.pitch(), 7.5, 0); near(limit.yaw(), 7.5, 0);
        Random random = new Random(90210);
        double pitch = 0, yaw = 0, squares = 0, product = 0;
        int count = 100_000;
        for (int i = 0; i < count; i++) {
            var sample = TaczMonitorSpread.sample(3, random::nextGaussian);
            if (Math.abs(sample.pitch()) > 7.5 || Math.abs(sample.yaw()) > 7.5)
                throw new AssertionError("Gaussian outlier exceeds the declared boundary");
            pitch += sample.pitch(); yaw += sample.yaw();
            squares += sample.pitch() * sample.pitch(); product += sample.pitch() * sample.yaw();
        }
        near(pitch / count, 0, .05); near(yaw / count, 0, .05);
        near(Math.sqrt(squares / count), 3, .08);
        near(product / count, 0, .1);
        System.out.println("TaczMonitorSpreadTest passed: ADS limits, independent axes and 100000 hip-fire samples");
    }
}
