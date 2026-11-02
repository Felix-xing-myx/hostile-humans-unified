package dev.felix.hostilehumans.client.render.tacz;

import java.util.function.DoubleUnaryOperator;

/**
 * Calls TaCZ's public native spline without linking its optional nested math library in HHU
 * descriptors.
 */
final class NativeRecoilCurve {
    private NativeRecoilCurve() {}

    static DoubleUnaryOperator create(Object recoil, String method, float factor)
            throws ReflectiveOperationException {
        Object spline = recoil.getClass().getMethod(method, float.class).invoke(recoil, factor);
        if (spline == null) return ms -> 0;
        var value = spline.getClass().getMethod("value", double.class);
        double[] knots = (double[]) spline.getClass().getMethod("getKnots").invoke(spline);
        double first = knots[0], last = knots[knots.length - 1];
        return ms -> {
            try {
                return ((Number) value.invoke(spline, Math.max(first, Math.min(last, ms))))
                        .doubleValue();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Native recoil curve changed", failure);
            }
        };
    }
}
