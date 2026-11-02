package dev.felix.hostilehumans.inventory;

/** A brief idle-only grace period between combat targets; runtime state, never persisted. */
public final class PickupCombatWindow {
    public static final int SETTLE_TICKS = 20;
    private int changedAt;
    private boolean recent;

    public void targetChanged(int tick, boolean hadTarget, boolean hasTarget) {
        if (hadTarget || hasTarget) { changedAt = tick; recent = true; }
    }

    public boolean mayCollect(int tick) {
        if (!recent) return true;
        long age = (long) tick - changedAt;
        if (age < 0 || age >= SETTLE_TICKS) { recent = false; return true; }
        return false;
    }
}
