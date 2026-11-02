package dev.felix.hostilehumans.behavior.combat.gun;

/**
 * Bounded closer-angle request after sustained occlusion, not permission to move or choose enemies.
 */
public final class GunOcclusionApproach {
    private GunOcclusionApproach() {}

    public static double desiredDistance(double distance) {
        if (!Double.isFinite(distance) || distance <= 2) return 2;
        return Math.max(2, Math.min(10, distance - 2));
    }

    public static boolean mayClose(double distance, int unseenTicks, boolean doctrineAllows) {
        return doctrineAllows && unseenTicks >= 30 && Double.isFinite(distance) && distance > 2;
    }
}
