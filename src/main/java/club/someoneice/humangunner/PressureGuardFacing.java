package club.someoneice.humangunner;

/** Stable yaw lease from impact direction only; no entity identity or position. */
final class PressureGuardFacing {
    static final float SAFE_OFFSET = 60, TURN_STEP = 30;
    static final int HOLD_TICKS = 20;
    private Float incomingYaw, heldYaw;
    private int reconsiderAt;
    void record(float yaw) { if (Float.isFinite(yaw)) incomingYaw = wrap(yaw); }
    float step(int now, float bodyYaw) {
        if (!Float.isFinite(bodyYaw)) return 0;
        if (heldYaw == null || now >= reconsiderAt) {
            float delta = incomingYaw == null ? 0 : wrap(incomingYaw - bodyYaw);
            // Keep already-protected sources inside the front hemisphere without tracking them exactly.
            heldYaw = wrap(bodyYaw + Math.copySign(Math.max(0, Math.abs(delta) - SAFE_OFFSET), delta));
            reconsiderAt = now + HOLD_TICKS;
        }
        return wrap(bodyYaw + Math.max(-TURN_STEP, Math.min(TURN_STEP, wrap(heldYaw - bodyYaw))));
    }
    void clear() { incomingYaw = heldYaw = null; reconsiderAt = 0; }
    private static float wrap(float yaw) { float r = yaw % 360; return r >= 180 ? r - 360 : r < -180 ? r + 360 : r; }
}
