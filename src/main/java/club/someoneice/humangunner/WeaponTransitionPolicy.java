package club.someoneice.humangunner;

/** Distance hysteresis belongs to weapon selection, not the attack or navigation executors. */
final class WeaponTransitionPolicy {
    static final double GUN_ENTER = 4.5, GUN_LEAVE = 6;
    static final double BOW_ENTER = 6, BOW_LEAVE = 7.5;
    private WeaponTransitionPolicy() { }
    static boolean holdsMelee(double distanceSqr, boolean active, boolean forced) {
        double radius = active ? GUN_LEAVE : GUN_ENTER;
        return forced && Double.isFinite(distanceSqr) && distanceSqr <= radius * radius;
    }
    static boolean holdsBowMelee(double distanceSqr, boolean active) {
        double radius = active ? BOW_LEAVE : BOW_ENTER;
        return Double.isFinite(distanceSqr) && distanceSqr <= radius * radius;
    }
}
