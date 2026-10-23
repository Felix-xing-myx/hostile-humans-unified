package dev.felix.hostilehumans.core;

/** A combat-hub decision, not a path, action permission, target or second execution clock. */
public record RangedMovementDecision(Mode mode, boolean orbitSpeed, boolean waterApproach, boolean canEngage) {
    public enum Mode { UNAVAILABLE, HOLD, ADVANCE, APPROACH, RETREAT, SEARCH_WINDOW, WAIT_AIM, ORBIT }
    public record Facts(double distanceSqr, double firingRangeSqr, double minimumBand, double maximumBand,
                        boolean visible, boolean advance, boolean holdingPosition, boolean human,
                        boolean inWater, boolean waterMovement, int seenTicks) { }

    public static RangedMovementDecision choose(Facts facts, boolean crossbow) {
        if (!valid(facts)) return new RangedMovementDecision(Mode.UNAVAILABLE, false, false, false);
        boolean canEngage = facts.distanceSqr <= facts.firingRangeSqr && (!crossbow || facts.seenTicks >= 5);
        if (facts.advance && facts.human && !facts.holdingPosition)
            return new RangedMovementDecision(Mode.ADVANCE, false, false, canEngage);
        return crossbow ? crossbow(facts, canEngage) : bow(facts, canEngage);
    }

    private static RangedMovementDecision bow(Facts f, boolean canEngage) {
        boolean waterApproach = f.human && f.waterMovement && f.distanceSqr > 22 * 22;
        boolean pursuing = f.distanceSqr > f.maximumBand * f.maximumBand || waterApproach;
        boolean retreating = !f.human && !f.holdingPosition && f.distanceSqr < 14 * 14 && f.visible;
        boolean blocked = !f.visible && !retreating && !f.holdingPosition
                && (!pursuing || f.human && f.inWater && f.distanceSqr <= f.firingRangeSqr);
        Mode mode = f.holdingPosition ? Mode.HOLD : retreating ? Mode.RETREAT : blocked ? Mode.SEARCH_WINDOW
                : pursuing ? Mode.APPROACH : Mode.ORBIT;
        return new RangedMovementDecision(mode, mode == Mode.ORBIT, waterApproach, canEngage);
    }

    private static RangedMovementDecision crossbow(Facts f, boolean canEngage) {
        boolean retreating = !f.holdingPosition && f.distanceSqr < f.minimumBand * f.minimumBand && f.visible;
        boolean needsAngle = f.seenTicks < 5;
        boolean orbitSpeed = !f.holdingPosition && !needsAngle && f.visible
                && f.distanceSqr <= f.maximumBand * f.maximumBand && f.distanceSqr <= f.firingRangeSqr;
        Mode mode = f.holdingPosition ? Mode.HOLD
                : !f.visible && !retreating && f.distanceSqr <= f.firingRangeSqr ? Mode.SEARCH_WINDOW
                : retreating ? Mode.RETREAT : f.distanceSqr > f.maximumBand * f.maximumBand ? Mode.APPROACH
                : needsAngle ? Mode.WAIT_AIM : Mode.ORBIT;
        return new RangedMovementDecision(mode, orbitSpeed, false, canEngage);
    }

    private static boolean valid(Facts f) {
        return f != null && Double.isFinite(f.distanceSqr) && f.distanceSqr >= 0
                && Double.isFinite(f.firingRangeSqr) && f.firingRangeSqr > 0
                && Double.isFinite(f.minimumBand) && f.minimumBand >= 0
                && Double.isFinite(f.maximumBand) && f.maximumBand >= f.minimumBand;
    }
}
