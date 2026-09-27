package club.someoneice.humangunner;

/** Distinguishes our health roll or the original entity base from external changes. */
final class MaxHealthCompatibilityPolicy {
    private static final double EPSILON = 0.001D;

    private MaxHealthCompatibilityPolicy() {
    }

    static boolean shouldApplyRoll(double liveBase, int previousRoll, double originalBase) {
        return Math.abs(liveBase - originalBase) <= EPSILON
                || (previousRoll > 0 && Math.abs(liveBase - previousRoll) <= EPSILON);
    }
}
