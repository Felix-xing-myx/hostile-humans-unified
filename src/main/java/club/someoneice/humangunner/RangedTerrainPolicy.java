package club.someoneice.humangunner;

/** Distinguish transient occlusion from a terrain failure and avoid rapid weapon oscillation. */
final class RangedTerrainPolicy {
    private RangedTerrainPolicy() {}
    static boolean switchAfterFailures(int blockedTicks, int searches) { return blockedTicks >= 40 && searches >= 2; }
    static boolean switchAfterBlockedSteps(int blockedSteps) { return blockedSteps >= 20; }
    static boolean keepMelee(int age, int clearTicks) { return age < 80 || (age < 400 && clearTicks < 20); }
}
