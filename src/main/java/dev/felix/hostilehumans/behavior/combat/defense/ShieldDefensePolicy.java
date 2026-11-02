package dev.felix.hostilehumans.behavior.combat.defense;

import java.util.ArrayList;
import java.util.List;

/** Geometry and per-raise melee limits; has no world access or target acquisition. */
public final class ShieldDefensePolicy {
    public static final int MELEE_REENTRY_TICKS = 40, MELEE_GUARD_MAX_TICKS = 20;
    /** Ordinary blocking spends an attack opportunity; chip damage is handled by pressure instead. */
    static boolean worthMeleeGuard(double damage, double maximumHealth) {
        return Double.isFinite(damage) && Double.isFinite(maximumHealth) && maximumHealth > 0
                && damage >= Math.max(4, maximumHealth * .04);
    }
    private ShieldDefensePolicy() { }
    static boolean meleeCandidate(double x, double y, double z, double enemyFacingDot, double ownFacingDot,
                                   boolean meleeIntent) {
        return meleeIntent && x*x + z*z <= 16 && Math.abs(y) <= 3
                && enemyFacingDot >= Math.cos(Math.toRadians(45))
                && ownFacingDot >= Math.cos(Math.toRadians(50));
    }
    static float wrap(float yaw) { float r = yaw % 360; return r >= 180 ? r - 360 : r < -180 ? r + 360 : r; }
    /** Include sector edges: optimal coverage may lie between two enemies. */
    static float coverageYaw(List<Float> bearings, float current, boolean stableTie, double random) {
        if (bearings.isEmpty()) return current;
        List<Float> options = new ArrayList<>();
        if (stableTie) options.add(current);
        for (float yaw : bearings) { options.add(yaw); options.add(wrap(yaw-45)); options.add(wrap(yaw+45)); }
        List<Float> best = new ArrayList<>();
        int most = -1; float leastTurn = Float.MAX_VALUE;
        for (float yaw : options) {
            int count = 0;
            for (float target : bearings) if (Math.abs(wrap(target-yaw)) <= 45.0001F) count++;
            float turn = Math.abs(wrap(yaw-current));
            if (count > most || count == most && stableTie && turn < leastTurn-.0001F) {
                best.clear(); most = count; leastTurn = turn;
            }
            if (count == most && (!stableTie || Math.abs(turn-leastTurn) < .0001F) && !best.contains(yaw)) best.add(yaw);
        }
        return best.get(Math.min(best.size()-1, (int)(Math.max(0, random)*best.size())));
    }
}
