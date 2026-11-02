package dev.felix.hostilehumans.behavior.combat.gun;

public final class GunOcclusionApproachTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(
                GunOcclusionApproach.mayClose(8, 30, true),
                "close cover must not wait forever behind a ten-block gate");
        check(
                GunOcclusionApproach.desiredDistance(8) == 6,
                "closer-angle request makes bounded progress");
        check(
                GunOcclusionApproach.desiredDistance(40) == 10,
                "far cover preserves original ten-block approach goal");
        check(!GunOcclusionApproach.mayClose(8, 29, true), "brief occlusion is not a chase order");
        check(
                !GunOcclusionApproach.mayClose(8, 80, false),
                "doctrine/engagement permission remains necessary");
        check(
                !GunOcclusionApproach.mayClose(2, 80, true),
                "avoid requesting an endless point-blank approach");
        check(
                !GunOcclusionApproach.mayClose(Double.NaN, 80, true),
                "invalid distance cannot authorize movement");
        System.out.println("GunOcclusionApproachTest passed");
    }
}
