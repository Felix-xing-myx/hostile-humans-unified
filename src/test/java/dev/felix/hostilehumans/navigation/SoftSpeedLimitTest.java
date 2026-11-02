package dev.felix.hostilehumans.navigation;

/** Verifies the actual compression, especially ordering and repeated raw projection invariants. */
public final class SoftSpeedLimitTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError("speed compression"); }
    public static void main(String[] args) {
        check(Math.abs(SoftSpeedLimit.CEILING - .234) < 1e-12);
        for (double raw : new double[]{0, .05, .1, .13}) check(SoftSpeedLimit.apply(raw) == raw);
        double last = .13;
        for (int i = 1; i <= 10000; i++) {
            double raw = .13 + i * .001;
            double mapped = SoftSpeedLimit.apply(raw);
            check(mapped > last && mapped < SoftSpeedLimit.CEILING && mapped < raw);
            last = mapped;
        }
        double epsilon = 1e-7;
        check(Math.abs((SoftSpeedLimit.apply(.13 + epsilon) - .13) / epsilon - 1) < 1e-5);
        check(Double.isFinite(SoftSpeedLimit.apply(Double.MAX_VALUE)));
        check(SoftSpeedLimit.apply(Double.MAX_VALUE) <= SoftSpeedLimit.CEILING);
        check(SoftSpeedLimit.apply(Double.NaN) == 0 && SoftSpeedLimit.apply(-1) == 0);
    }
}
