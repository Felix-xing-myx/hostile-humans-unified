package dev.felix.hostilehumans.core;

import java.util.Arrays;

/** Independent, transient wear gates; only an actual durability loss commits a gate. */
public final class EquipmentWearCooldown {
    private final long[] lastLoss;
    private final long interval;

    public EquipmentWearCooldown(int slots, long interval) {
        this.lastLoss = new long[slots];
        Arrays.fill(lastLoss, Long.MIN_VALUE);
        this.interval = interval;
    }

    public boolean ready(int slot, long tick) {
        long last = lastLoss[slot];
        return last == Long.MIN_VALUE || tick < last || tick - last >= interval;
    }

    public void lostDurability(int slot, long tick) { lastLoss[slot] = tick; }
}
