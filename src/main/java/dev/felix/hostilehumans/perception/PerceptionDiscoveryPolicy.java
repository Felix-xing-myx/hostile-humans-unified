package dev.felix.hostilehumans.perception;

/**
 * Shares the existing visual budget between maintaining evidence and discovering nearby threats.
 */
public final class PerceptionDiscoveryPolicy {
    private PerceptionDiscoveryPolicy() {}

    public static boolean reserveDiscovery(long round, int rays, boolean urgent) {
        // Single-slot battles rotate discovery, recognition and existing-target maintenance.
        return !urgent && rays > 0 && (rays > 2 || Math.floorMod(round, 3) == 1);
    }

    public static boolean maintainMain(long round, int rays) {
        return rays > 2 || Math.floorMod(round, 3) == 0;
    }

    public static boolean preemptsRecognition(
            double visibleDistance, double existingDistance, double nearRadius, boolean hurtFocus) {
        return !hurtFocus
                && Double.isFinite(visibleDistance)
                && Double.isFinite(existingDistance)
                && visibleDistance <= nearRadius
                && existingDistance > nearRadius
                && existingDistance > visibleDistance;
    }

    public static int evidenceInterval(int interval, int rays) {
        return Math.max(1, interval) * (rays <= 2 ? 3 : 1);
    }
}
