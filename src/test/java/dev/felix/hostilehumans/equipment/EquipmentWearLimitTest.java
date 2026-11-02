package dev.felix.hostilehumans.equipment;

import java.util.ArrayDeque;
import java.util.Random;

/** Sliding-window reference checks: independent slots, bursts, Unbreaking, replacement and exact expiry. */
public final class EquipmentWearLimitTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        EquipmentWearLimit burst = new EquipmentWearLimit(4, 10, 20);
        burst.lostDurability(0, 19, 10);
        check(burst.remaining(0, 20) == 0, "a wall-clock second boundary does not reset quota");
        check(burst.remaining(0, 38) == 0 && burst.remaining(0, 39) == 10, "exact twenty-tick expiry");
        burst.lostDurability(0, 40, 3);
        burst.lostDurability(0, 50, 7);
        check(burst.remaining(0, 60) == 3 && burst.remaining(0, 70) == 10, "partial history expires independently");
        burst.lostDurability(1, 70, 0);
        check(burst.remaining(1, 70) == 10, "prevented wear consumes no quota");
        burst.lostDurability(0, 70, 10);
        check(burst.remaining(1, 70) == 10, "each armor slot has its own budget");
        check(burst.remaining(0, 71) == 0, "armor replacement cannot reset a slot budget");
        check(burst.remaining(0, 1) == 10, "clock rollback recovers safely");
        try { burst.lostDurability(0, 1, 11); throw new AssertionError("overdraw accepted"); }
        catch (IllegalArgumentException expected) { }
        Random random = new Random(14463);
        EquipmentWearLimit rolling = new EquipmentWearLimit(4, 10, 20);
        for (int slot = 0; slot < 4; slot++) {
            ArrayDeque<Long> reference = new ArrayDeque<>();
            for (long tick = 0; tick < 3000; tick++) {
                while (!reference.isEmpty() && reference.peekFirst() <= tick - 20) reference.removeFirst();
                check(rolling.remaining(slot, tick) == 10 - reference.size(), "reference capacity");
                for (int hit = 0; hit < 4; hit++) {
                    int admitted = Math.min(random.nextInt(50), rolling.remaining(slot, tick));
                    int actual = random.nextInt(admitted + 1); // Native Unbreaking or cancellation may prevent wear.
                    rolling.lostDurability(slot, tick, actual);
                    for (int i = 0; i < actual; i++) reference.addLast(tick);
                    check(reference.size() <= 10 && rolling.remaining(slot, tick) == 10 - reference.size(),
                            "arbitrary burst never exceeds ten actual durability per rolling second");
                }
            }
        }
        System.out.println("EquipmentWearLimitTest passed: rolling slot caps and native-wear accounting");
    }
}
