package dev.felix.hostilehumans.monitor;

/**
 * Native gun weight, ADS and reload are already in actual travel speed and must survive snapshots.
 */
public final class MonitorTravelSpeedTest {
    public static void main(String[] args) {
        for (float actual : new float[] {0, .08F, .12F, .23F, .3F, .39F})
            for (boolean sprint : new boolean[] {false, true}) {
                float base = MonitorTravelSpeed.walking(actual, sprint);
                float predicted = base * (sprint ? 1.3F : 1);
                if (Math.abs(actual - predicted) > 1e-6)
                    throw new AssertionError("Gun/effect speed lost or sprint applied twice");
            }
        try {
            MonitorTravelSpeed.walking(Float.NaN, false);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
        System.out.println("Monitor actual travel speed passed");
    }
}
