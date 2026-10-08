package club.someoneice.humangunner;

/** Cheap moving-target refresh triggers, independent of A-star work. */
public final class PursuitRefreshPolicy {
    private PursuitRefreshPolicy() {}
    public static boolean due(int age, boolean hasRoute, boolean nearEnd, double movedSqr, boolean failed) {
        if (age < 4) return false;
        if (movedSqr >= 2.25D) return true;
        if (failed) return age >= 20;
        if (!hasRoute) return true;
        return movedSqr >= 0.25D && (nearEnd || age >= 20);
    }
}
