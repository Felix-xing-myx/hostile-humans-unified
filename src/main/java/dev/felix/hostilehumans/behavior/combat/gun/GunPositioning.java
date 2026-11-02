package dev.felix.hostilehumans.behavior.combat.gun;

/** Pure combat positioning command; no entity, path, inventory, cooldown or movement permission. */
public final class GunPositioning {
    enum Mode { SEARCH_WINDOW, APPROACH, HOLD, STRAFE }
    public record Plan(Mode mode, double desiredDistance) { }
    private GunPositioning() { }

    public static Plan choose(double distance, double firingReach, double preferredMaximum,
                       double meleeReach, boolean advance, boolean obscured) {
        if (!Double.isFinite(distance) || distance < 0 || !Double.isFinite(firingReach) || firingReach <= 0
                || !Double.isFinite(preferredMaximum) || preferredMaximum < 0
                || !Double.isFinite(meleeReach) || meleeReach < 0) return new Plan(Mode.HOLD, 0);
        if (obscured) return new Plan(Mode.SEARCH_WINDOW, preferredMaximum);
        // Hard firing reach and preferred distance remain separate. The attack executor
        // independently checks reach before paying ammunition and submitting a shot.
        if (distance > firingReach) return new Plan(Mode.APPROACH, preferredMaximum);
        if (advance || distance > preferredMaximum) {
            double desired = advance ? meleeReach : preferredMaximum;
            return new Plan(distance > desired ? Mode.APPROACH : Mode.HOLD, desired);
        }
        return new Plan(Mode.STRAFE, preferredMaximum);
    }
}
