package dev.felix.hostilehumans.perception;

public final class BeaconReportPolicyTest {
    public static void main(String[] args) {
        check(BeaconReportPolicy.renewedUntil(100, 6000) == 6100, "five minutes from success");
        check(BeaconReportPolicy.nextAfterUse(120, 6100, 260) == 260, "active renewal keeps cadence");
        check(BeaconReportPolicy.nextAfterUse(6100, 6100, 6260) == 6100, "expired rebuild immediate");
        check(!BeaconReportPolicy.due(6100, 6100, 6000), "expiry tick cannot report");
        check(BeaconReportPolicy.due(260, 6100, 260), "scheduled tick reports");
        check(!BeaconReportPolicy.due(259, 6100, 260), "early tick cannot report");
        check(BeaconReportPolicy.nextAfterSample(260, 260, 160) == 420, "normal cadence");
        check(BeaconReportPolicy.nextAfterSample(1000, 260, 160) == 1060,
                "unloaded intervals skipped without replay or phase shift");
        check(BeaconReportPolicy.gridCenter(0, 8) == 4, "grid origin");
        check(BeaconReportPolicy.gridCenter(7.99, 8) == 4, "same cell immutable");
        check(BeaconReportPolicy.gridCenter(8, 8) == 12, "cell edge");
        check(BeaconReportPolicy.gridCenter(-.01, 8) == -4, "negative coordinates floor");
        check(BeaconReportPolicy.gridCenter(-8, 8) == -4, "negative edge");
        check(BeaconReportPolicy.remaining(200, 500) == 300, "late member gets remaining TTL");
        check(BeaconReportPolicy.remaining(500, 500) == 0, "strict TTL boundary");
        check(BeaconReportPolicy.shouldReceive(2, 1, 200, 500), "new sample delivered once");
        check(!BeaconReportPolicy.shouldReceive(2, 2, 201, 500), "repeat tick never renews clue");
        check(!BeaconReportPolicy.shouldReceive(2, 1, 500, 500), "expired report ignored");
        long next = 260;
        long until = 6100;
        for (int now = 140; now < 260; now += 40) {
            next = BeaconReportPolicy.nextAfterUse(now, until, next);
            until = BeaconReportPolicy.renewedUntil(now, 6000);
        }
        check(next == 260, "two-second repeat use neither advances nor postpones report");
        System.out.println("PASS: beacon cadence, strict deadlines, coarse grid, and delivery deduplication");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
