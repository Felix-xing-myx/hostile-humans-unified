package dev.felix.hostilehumans.navigation;

/** Monotone final-attribute compression. Ordinary speeds remain exactly unchanged. */
public final class SoftSpeedLimit {
    public static final double PLAYER_SPRINT = .1 * 1.3;
    public static final double CEILING = PLAYER_SPRINT * 1.8;
    private SoftSpeedLimit() {}

    public static double apply(double raw) {
        if (Double.isNaN(raw) || raw <= 0) return 0;
        if (raw <= PLAYER_SPRINT) return raw;
        double span = CEILING - PLAYER_SPRINT;
        // This form remains finite even for extremely large external modifiers.
        return CEILING - span / (1 + (raw - PLAYER_SPRINT) / span);
    }
}
