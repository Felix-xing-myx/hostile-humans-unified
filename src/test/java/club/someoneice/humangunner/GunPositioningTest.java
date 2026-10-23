package club.someoneice.humangunner;

/** Distance/visibility transition contract through the command selector used by the combat hub. */
public final class GunPositioningTest {
    public static void main(String[] args) {
        plan(8, 36, 12, 3, false, false, GunPositioning.Mode.STRAFE, 12);
        plan(12, 36, 12, 3, false, false, GunPositioning.Mode.STRAFE, 12);
        plan(13, 36, 12, 3, false, false, GunPositioning.Mode.APPROACH, 12);
        plan(40, 36, 12, 3, false, false, GunPositioning.Mode.APPROACH, 12);
        plan(8, 36, 12, 3, true, false, GunPositioning.Mode.APPROACH, 3);
        plan(3, 36, 12, 3, true, false, GunPositioning.Mode.HOLD, 3);
        plan(2, 36, 12, 3, true, false, GunPositioning.Mode.HOLD, 3);
        plan(40, 36, 12, 3, true, false, GunPositioning.Mode.APPROACH, 12);
        plan(8, 36, 12, 3, false, true, GunPositioning.Mode.SEARCH_WINDOW, 12);
        plan(40, 36, 12, 3, true, true, GunPositioning.Mode.SEARCH_WINDOW, 12);
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            plan(invalid, 36, 12, 3, false, false, GunPositioning.Mode.HOLD, 0);
            plan(8, invalid, 12, 3, false, false, GunPositioning.Mode.HOLD, 0);
        }
        // No hidden phase/timer: returning to the same facts returns the same command.
        var original = GunPositioning.choose(8, 36, 12, 3, false, false);
        GunPositioning.choose(40, 36, 12, 3, true, true);
        check(original.equals(GunPositioning.choose(8, 36, 12, 3, false, false)), "no cross-target or cross-phase state");
        System.out.println("GunPositioningTest passed");
    }
    private static void plan(double distance, double firingReach, double maximum, double melee, boolean advance,
                             boolean obscured, GunPositioning.Mode expected, double desired) {
        var command = GunPositioning.choose(distance, firingReach, maximum, melee, advance, obscured);
        check(command.mode() == expected && command.desiredDistance() == desired, "command for " + distance + "/" + advance + "/" + obscured);
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
