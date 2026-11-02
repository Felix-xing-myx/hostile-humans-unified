package dev.felix.hostilehumans.progression;

import java.util.Arrays;

/**
 * One finite merged boundary model for birth, cultivation and inspection. No entity or registry
 * access.
 */
public final class SpecialtyBounds {
    private SpecialtyBounds() {}

    public record Limits(double[] minimum, double[] maximum) {
        public Limits {
            minimum = minimum.clone();
            maximum = maximum.clone();
            if (minimum.length == 0 || minimum.length != maximum.length)
                throw new IllegalArgumentException("limits dimensions");
            for (int i = 0; i < minimum.length; i++) {
                if (!Double.isFinite(minimum[i])
                        || minimum[i] < 0
                        || Double.isNaN(maximum[i])
                        || maximum[i] <= 0
                        || maximum[i] < minimum[i])
                    throw new IllegalArgumentException("invalid absolute limits at " + i);
            }
        }

        @Override
        public double[] minimum() {
            return minimum.clone();
        }

        @Override
        public double[] maximum() {
            return maximum.clone();
        }
    }

    public record Bounds(double[] lower, double[] upper) {
        public Bounds {
            lower = lower.clone();
            upper = upper.clone();
        }

        @Override
        public double[] lower() {
            return lower.clone();
        }

        @Override
        public double[] upper() {
            return upper.clone();
        }

        public boolean intersects() {
            for (int i = 0; i < lower.length; i++) if (lower[i] > upper[i]) return false;
            return true;
        }
    }

    /** Historical scalar helper retained for callers explicitly modelling an older policy. */
    public static Limits uniform(int dimensions, double minimum, double maximum) {
        SpecialtyAllocation.positive(minimum);
        SpecialtyAllocation.positive(maximum);
        double[] lo = new double[dimensions], hi = new double[dimensions];
        Arrays.fill(lo, minimum);
        Arrays.fill(hi, maximum);
        if (dimensions == 13) {
            lo[12] = 0;
            hi[12] = .45;
        }
        return new Limits(lo, hi);
    }

    public static Bounds at(
            double budget, double[] centre, double[] caps, double exponent, Limits limits) {
        SpecialtyAllocation.positive(budget);
        if (!Double.isFinite(exponent) || exponent < 0 || exponent > 1)
            throw new IllegalArgumentException("exponent");
        double[] lo = limits.minimum(), hi = limits.maximum();
        if (centre.length != caps.length || centre.length != lo.length)
            throw new IllegalArgumentException("bounds dimensions");
        double scale = Math.pow(budget, exponent);
        for (int i = 0; i < centre.length; i++) {
            SpecialtyAllocation.positive(centre[i]);
            if (!Double.isFinite(caps[i]) || caps[i] < 0 || caps[i] > 1)
                throw new IllegalArgumentException("cap");
            lo[i] = Math.max(lo[i], centre[i] * (1 - caps[i]) * scale);
            hi[i] = Math.min(hi[i], centre[i] * (1 + caps[i]) * scale);
            if (!Double.isFinite(lo[i]) || !Double.isFinite(hi[i]))
                throw new IllegalArgumentException("nonfinite merged bounds");
        }
        return new Bounds(lo, hi);
    }
}
