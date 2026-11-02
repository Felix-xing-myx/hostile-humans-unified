package dev.felix.hostilehumans.monitor.client;

/** Movement-only interpretation of intent, matching the authoritative control lease. */
final class MonitorPredictionIntent {
    private MonitorPredictionIntent() {}

    static boolean sprinting(boolean active, boolean sprint, float forward, boolean sneak,
            boolean attack, boolean use) {
        return active && sprint && forward > 0 && !sneak && !attack && !use;
    }
}
