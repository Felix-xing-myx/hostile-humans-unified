package dev.felix.hostilehumans.behavior.combat.defense;

/** Stable yaw lease from impact direction only; no entity identity or position. */
final class PressureGuardFacing {
    static final float FOLLOW_ANGLE = 30, TURN_STEP = 12;
    static final int HOLD_TICKS = 12;
    private Float incomingYaw, heldYaw;
    private int reconsiderAt;
    void record(float yaw) { if (Float.isFinite(yaw)) incomingYaw = wrap(yaw); }
    float step(int now, float bodyYaw) {
        if (!Float.isFinite(bodyYaw)) return 0;
        // No artificial hold without an actual arrival direction. A first hit responds immediately.
        if (incomingYaw == null) return bodyYaw;
        if (heldYaw == null || now >= reconsiderAt) {
            heldYaw = incomingYaw;
            reconsiderAt = now + HOLD_TICKS;
        } else if (Math.abs(wrap(incomingYaw - heldYaw)) <= FOLLOW_ANGLE) {
            // Follow a slowly changing single direction; distant sides wait for the switch interval.
            heldYaw = incomingYaw;
        }
        return wrap(bodyYaw + Math.max(-TURN_STEP, Math.min(TURN_STEP, wrap(heldYaw - bodyYaw))));
    }
    void clear() { incomingYaw = heldYaw = null; reconsiderAt = 0; }
    private static float wrap(float yaw) { float r = yaw % 360; return r >= 180 ? r - 360 : r < -180 ? r + 360 : r; }
}
