package club.someoneice.humangunner;

/** Pure bounds shared by surface combat steering and its progress watchdog. */
public final class WaterCombatPolicy {
    private WaterCombatPolicy() {}

    public static boolean closingProgress(double previousDistance, double currentDistance) {
        return Double.isFinite(previousDistance) && Double.isFinite(currentDistance)
                && currentDistance >= 0 && currentDistance <= previousDistance - .5D;
    }

    public static boolean directApproach(boolean targetInWater, double distanceSqr,
                                         double heightDifference, boolean visible) {
        return targetInWater && visible && Double.isFinite(distanceSqr) && distanceSqr >= 0
                && distanceSqr <= 64.0D && Double.isFinite(heightDifference)
                && Math.abs(heightDifference) <= 1.5D;
    }

    public static boolean chasingMovingTarget(double targetMotionSqr, double forwardAdvance, double sideAdvance) {
        return Double.isFinite(targetMotionSqr) && Double.isFinite(forwardAdvance) && Double.isFinite(sideAdvance)
                && targetMotionSqr >= .25D && forwardAdvance >= .5D
                && Math.abs(sideAdvance) <= forwardAdvance * .75D;
    }
}
