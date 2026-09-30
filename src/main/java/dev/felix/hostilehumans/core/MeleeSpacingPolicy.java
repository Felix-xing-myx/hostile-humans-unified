package dev.felix.hostilehumans.core;

/** Keep a positive hit margin, including short-reach weapons and small targets. */
public final class MeleeSpacingPolicy {
    private MeleeSpacingPolicy() {}
    public static double preferred(double reach) {
        return Math.max(0.35D, Math.min(reach * 0.78D, reach - 0.5D));
    }
    public static double tooClose(double reach) {
        return Math.max(0.15D, Math.min(preferred(reach) - 0.25D, reach * 0.5D));
    }
}
