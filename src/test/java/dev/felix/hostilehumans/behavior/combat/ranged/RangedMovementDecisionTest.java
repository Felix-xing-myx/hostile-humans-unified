package dev.felix.hostilehumans.behavior.combat.ranged;

import static dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision.Mode.*;

/** Behavioral boundaries of the shared combat-hub command, without world/path execution. */
public final class RangedMovementDecisionTest {
    public static void main(String[] args) {
        mode(20, true, 5, false, ORBIT);
        mode(24, true, 5, false, ORBIT);
        mode(24.01, true, 5, false, APPROACH);
        mode(20, false, -1, false, SEARCH_WINDOW);
        mode(30, false, -1, false, APPROACH);
        mode(13, true, 5, false, ORBIT);
        mode(13, true, 5, true, RETREAT);
        mode(16, true, 4, true, WAIT_AIM);
        mode(16, true, 5, true, ORBIT);
        mode(24, true, 5, true, ORBIT);
        mode(24.01, true, 5, true, APPROACH);
        mode(20, false, -1, true, SEARCH_WINDOW);
        mode(40, false, -1, true, APPROACH);

        var basic = facts(20, true, 5);
        for (boolean crossbow : new boolean[]{false, true}) {
            var advancing = flags(basic, true, false, true, false, false);
            check(RangedMovementDecision.choose(advancing, crossbow).mode() == ADVANCE, "explicit advance wins normal spacing");
            var holding = flags(basic, true, true, true, false, false);
            check(RangedMovementDecision.choose(holding, crossbow).mode() == HOLD, "station order blocks advance");
            check(!RangedMovementDecision.choose(holding, crossbow).orbitSpeed(), "holding does not apply orbit speed");
        }
        check(RangedMovementDecision.choose(flags(facts(13, true, 5), false, false, false, false, false), false).mode() == RETREAT,
                "legacy non-Human close bow keeps path-retreat fallback");
        var water = RangedMovementDecision.choose(flags(facts(23, true, 5), false, false, true, true, true), false);
        check(water.mode() == APPROACH && water.waterApproach(), "bow water approach starts above 22");
        check(RangedMovementDecision.choose(flags(facts(22, true, 5), false, false, true, true, true), false).mode() == ORBIT,
                "water approach boundary is strict");
        check(RangedMovementDecision.choose(flags(facts(30, false, -1), false, false, true, true, true), false).mode() == SEARCH_WINDOW,
                "obscured water target inside hard reach searches an angle rather than ordinary approach");
        check(!RangedMovementDecision.choose(facts(20, true, 4), true).canEngage(), "four visible ticks cannot engage crossbow");
        check(RangedMovementDecision.choose(facts(20, true, 5), true).canEngage(), "five visible ticks permit crossbow engagement");
        check(!RangedMovementDecision.choose(facts(33, true, 8), true).canEngage(), "engagement cannot exceed hard reach");
        check(RangedMovementDecision.choose(null, false).mode() == UNAVAILABLE, "absent facts cannot navigate");
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            var bad = new RangedMovementDecision.Facts(invalid, 1024, 16, 24, true, false, false, true, false, false, 5);
            check(RangedMovementDecision.choose(bad, false).mode() == UNAVAILABLE, "invalid distance cannot navigate");
        }
        var before = RangedMovementDecision.choose(basic, false);
        RangedMovementDecision.choose(facts(60, false, -1), true);
        check(before.equals(RangedMovementDecision.choose(basic, false)), "facts carry no hidden phase, target or cooldown");
        System.out.println("RangedMovementDecisionTest passed");
    }
    private static RangedMovementDecision.Facts facts(double distance, boolean visible, int ticks) {
        return new RangedMovementDecision.Facts(distance * distance, 32 * 32, 16, 24, visible, false, false, true, false, false, ticks);
    }
    private static RangedMovementDecision.Facts flags(RangedMovementDecision.Facts f, boolean advance, boolean hold,
                                                     boolean human, boolean inWater, boolean waterMovement) {
        return new RangedMovementDecision.Facts(f.distanceSqr(), f.firingRangeSqr(), f.minimumBand(), f.maximumBand(),
                f.visible(), advance, hold, human, inWater, waterMovement, f.seenTicks());
    }
    private static void mode(double distance, boolean visible, int ticks, boolean crossbow, RangedMovementDecision.Mode expected) {
        check(RangedMovementDecision.choose(facts(distance, visible, ticks), crossbow).mode() == expected,
                "mode at " + distance + "/" + visible + "/" + ticks + "/" + crossbow);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
