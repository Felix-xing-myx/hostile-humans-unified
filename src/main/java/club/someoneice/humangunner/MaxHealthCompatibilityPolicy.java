package club.someoneice.humangunner;

/** Tracks base-value ownership without ever adopting a coincidentally equal external value. */
final class MaxHealthCompatibilityPolicy {
    private static final double EPSILON = 0.001D;

    private MaxHealthCompatibilityPolicy() {
    }

    static boolean initialOwnership(double liveBase, double previousRoll, double originalBase) {
        return Math.abs(liveBase - originalBase) <= EPSILON
                || (previousRoll > 0 && Math.abs(liveBase - previousRoll) <= EPSILON);
    }

    static boolean retainsOwnership(boolean owned, double liveBase, double previousBase, double originalBase) {
        return owned && (Math.abs(liveBase - previousBase) <= EPSILON || Math.abs(liveBase - originalBase) <= EPSILON);
    }
}
