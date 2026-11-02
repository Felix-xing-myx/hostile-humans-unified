package dev.felix.hostilehumans.equipment;

/** Per-slot rolling durability loss cap. Stores at most limit timestamps per slot, without per-hit allocation. */
public final class EquipmentWearLimit {
    private final long[][] losses;
    private final int[] first, size;
    private final long window;

    public EquipmentWearLimit(int slots, int limit, long window) {
        if (slots <= 0 || limit <= 0 || window <= 0) throw new IllegalArgumentException("positive wear bounds required");
        this.losses = new long[slots][limit];
        this.first = new int[slots];
        this.size = new int[slots];
        this.window = window;
    }

    public int remaining(int slot, long tick) {
        if (size[slot] > 0 && tick < losses[slot][(first[slot] + size[slot] - 1) % losses[slot].length])
            size[slot] = 0; // A reset clock cannot strand a live object's quota.
        while (size[slot] > 0 && tick - losses[slot][first[slot]] >= window) {
            first[slot] = (first[slot] + 1) % losses[slot].length;
            size[slot]--;
        }
        return losses[slot].length - size[slot];
    }

    /** Only confirmed durability loss is charged; Unbreaking, cancellation and repair do not consume quota. */
    public void lostDurability(int slot, long tick, int amount) {
        int allowed = remaining(slot, tick);
        if (amount < 0 || amount > allowed) throw new IllegalArgumentException("wear exceeds remaining quota");
        for (int i = 0; i < amount; i++) {
            losses[slot][(first[slot] + size[slot]) % losses[slot].length] = tick;
            size[slot]++;
        }
    }
}
