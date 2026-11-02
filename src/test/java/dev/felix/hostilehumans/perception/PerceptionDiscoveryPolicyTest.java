package dev.felix.hostilehumans.perception;

public final class PerceptionDiscoveryPolicyTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        int discovery = 0, recognition = 0, maintenance = 0;
        for (int round = 0; round < 30; round++) {
            if (PerceptionDiscoveryPolicy.reserveDiscovery(round, 1, false)) discovery++;
            else if (PerceptionDiscoveryPolicy.maintainMain(round, 1)) maintenance++;
            else recognition++;
        }
        check(
                discovery == 10 && recognition == 10 && maintenance == 10,
                "single-ray battle cannot starve any lane");
        check(
                !PerceptionDiscoveryPolicy.reserveDiscovery(1, 1, true),
                "real incoming-hit response owns its urgent slot");
        check(
                !PerceptionDiscoveryPolicy.reserveDiscovery(1, 0, false),
                "no visual work without capacity");
        check(
                !PerceptionDiscoveryPolicy.preemptsRecognition(3, 30, 6, true),
                "do not pause a real hurt source");
        check(
                PerceptionDiscoveryPolicy.preemptsRecognition(3, 30, 6, false),
                "verified near threat may replace distant recognition");
        check(
                !PerceptionDiscoveryPolicy.preemptsRecognition(3, 4, 6, false),
                "do not churn between near threats");
        check(
                !PerceptionDiscoveryPolicy.preemptsRecognition(8, 30, 6, false),
                "ordinary distant discovery does not preempt");
        check(
                !PerceptionDiscoveryPolicy.preemptsRecognition(Double.NaN, 30, 6, false),
                "reject non-finite geometry");
        RecognitionEvidence evidence = new RecognitionEvidence();
        for (int tick = 0; tick <= 60; tick += 30)
            evidence.visible(tick, PerceptionDiscoveryPolicy.evidenceInterval(10, 1), 40, 40);
        check(
                evidence.progress() == 1,
                "scheduled lane spacing is credited, not mistaken for budget starvation");
        System.out.println("PerceptionDiscoveryPolicyTest passed");
    }
}
