package dev.felix.hostilehumans.compat.tacz;

import java.util.function.DoubleSupplier;

/** Additional manual hip-fire error only. Native gun/attachment spread remains upstream. */
final class TaczMonitorSpread {
    static final double HIP_STANDARD_DEVIATION_DEGREES = 3.0;
    private static final double GAUSSIAN_LIMIT = 2.5;
    record Offset(float pitch, float yaw) {
        static final Offset ZERO = new Offset(0, 0);
    }
    private TaczMonitorSpread() {}

    static double standardDeviation(float aimingProgress, double tierSpread, double accuracyMultiplier) {
        double aim = Float.isFinite(aimingProgress) ? Math.max(0, Math.min(1, aimingProgress)) : 0;
        double base = Double.isFinite(tierSpread) ? Math.max(0, tierSpread) : 0;
        double multiplier = Double.isFinite(accuracyMultiplier) && accuracyMultiplier >= 0 ? accuracyMultiplier : 1;
        return Math.hypot(HIP_STANDARD_DEVIATION_DEGREES, base) * multiplier * (1 - aim) * (1 - aim);
    }

    /** One paired draw per native shoot request, never rerolled by polling the direction suppliers. */
    static Offset sample(double standardDeviation, DoubleSupplier gaussian) {
        if (!Double.isFinite(standardDeviation) || standardDeviation <= 0) return Offset.ZERO;
        return new Offset(component(standardDeviation, gaussian.getAsDouble()),
                component(standardDeviation, gaussian.getAsDouble()));
    }
    private static float component(double deviation, double gaussian) {
        return Double.isFinite(gaussian)
                ? (float) (deviation * Math.max(-GAUSSIAN_LIMIT, Math.min(GAUSSIAN_LIMIT, gaussian))) : 0;
    }
}
