package club.someoneice.humangunner;

/** Owner-centered geometry and threat priority, independent of attack animation. */
final class BodyguardPolicy {
    static final double CHASE_SQUARED = 12.0D * 12.0D;
    static final double RANGED_SQUARED = 48.0D * 48.0D;
    private BodyguardPolicy() { }

    static boolean attackInRange(double soldierOwner, double targetOwner, boolean ranged, boolean directed) {
        return soldierOwner <= CHASE_SQUARED && (targetOwner <= CHASE_SQUARED
                || (ranged && directed && targetOwner <= RANGED_SQUARED));
    }
    static double priority(double targetOwner, boolean directed, boolean current) {
        // A threat near the owner beats a distant attacker even before a hit.
        return targetOwner + (targetOwner > CHASE_SQUARED ? 500.0D : 0.0D)
                - (current ? 0.000001D : 0.0D);
    }
    static boolean shouldReturn(double soldierOwner) { return soldierOwner > CHASE_SQUARED; }
    static boolean needsRangedPosition(double horizontalOwner, double verticalOwner) {
        return horizontalOwner > 16.0D || Math.abs(verticalOwner) > 4.0D;
    }
    static double interceptSpacing(double threatDistance) {
        return Math.max(0.8D, Math.min(2.5D, threatDistance - 0.8D));
    }
}
