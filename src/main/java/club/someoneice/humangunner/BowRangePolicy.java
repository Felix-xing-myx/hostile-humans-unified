package club.someoneice.humangunner;

/** Pure distance-band decisions for bow movement and regression coverage. */
public final class BowRangePolicy {
    private static final double RETREAT_TRIGGER_SQR = 14.0D * 14.0D;
    private static final double ORBIT_BUFFER_SQR = 16.0D * 16.0D;
    private static final double ORBIT_OUTER_SQR = 22.0D * 22.0D;

    private BowRangePolicy() {
    }

    public static boolean shouldPathRetreat(double distanceSqr) {
        return distanceSqr < RETREAT_TRIGGER_SQR;
    }

    public static boolean shouldPursue(double distanceSqr, double attackRadiusSqr) {
        return distanceSqr > attackRadiusSqr;
    }

    public static boolean usefulRetreatDestination(double currentDistanceSqr, double candidateDistanceSqr) {
        return candidateDistanceSqr >= ORBIT_BUFFER_SQR
                && candidateDistanceSqr >= currentDistanceSqr + 9.0D;
    }

    public static float orbitForwardInput(double distanceSqr) {
        if (distanceSqr < ORBIT_BUFFER_SQR) return -0.55F;
        if (distanceSqr > ORBIT_OUTER_SQR) return 0.12F;
        return 0.0F;
    }

    public static float orbitSideInput(double distanceSqr) {
        return distanceSqr < ORBIT_BUFFER_SQR ? 0.80F : 0.95F;
    }
}
