package dev.felix.hostilehumans.perception;

/** Minecraft-independent timing and precision rules for shared beacon reports. */
public final class BeaconReportPolicy {
    private BeaconReportPolicy() {}

    public static boolean active(long now, long until) { return now < until; }
    public static long renewedUntil(long now, int duration) { return now + duration; }
    public static long nextAfterUse(long now, long previousUntil, long nextReport) {
        return active(now, previousUntil) ? nextReport : now;
    }
    public static boolean due(long now, long until, long nextReport) {
        return active(now, until) && now >= nextReport;
    }
    /** Skip missed samples after unloading without replaying them or shifting cadence. */
    public static long nextAfterSample(long now, long nextReport, int interval) {
        return nextReport + ((now - nextReport) / interval + 1) * interval;
    }
    public static double gridCenter(double coordinate, double gridSize) {
        return Math.floor(coordinate / gridSize) * gridSize + gridSize / 2.0;
    }
    public static int remaining(long now, long reportUntil) {
        return (int)Math.max(0, Math.min(Integer.MAX_VALUE, reportUntil - now));
    }
    public static boolean shouldReceive(long sequence, long lastSequence, long now, long reportUntil) {
        return sequence > lastSequence && remaining(now, reportUntil) > 0;
    }
}
