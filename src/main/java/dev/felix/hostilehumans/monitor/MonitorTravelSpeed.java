package dev.felix.hostilehumans.monitor;

/**
 * Neutralizes only vanilla sprint; all actual gun/effect/action restrictions stay in travel speed.
 */
public final class MonitorTravelSpeed {
    private MonitorTravelSpeed() {}

    public static float walking(float actual, boolean sprinting) {
        if (!Float.isFinite(actual) || actual < 0)
            throw new IllegalArgumentException("Invalid travel speed");
        return sprinting ? actual / 1.3F : actual;
    }
}
