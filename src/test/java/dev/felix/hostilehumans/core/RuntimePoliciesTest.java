package dev.felix.hostilehumans.core;

import java.util.*;

/** Pure-JVM contract tests: no game, client or dedicated server is started. */
public final class RuntimePoliciesTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("check " + checks);
    }
    public static void main(String[] args) {
        for (int i = 100; i <= 1800; i++) {
            double reach = i / 100.0D;
            check(MeleeSpacingPolicy.preferred(reach) < reach);
            check(reach - MeleeSpacingPolicy.preferred(reach) >= 0.5D - 1.0E-9D);
            check(MeleeSpacingPolicy.tooClose(reach) < MeleeSpacingPolicy.preferred(reach));
        }
        EquipmentWearCooldown wear = new EquipmentWearCooldown(4, 60);
        for (int slot = 0; slot < 4; slot++) check(wear.ready(slot, 0));
        wear.lostDurability(0, 0);
        check(!wear.ready(0, 59));
        for (int slot = 1; slot < 4; slot++) check(wear.ready(slot, 0));
        wear.lostDurability(1, 10);
        check(wear.ready(0, 60));
        check(!wear.ready(1, 69));
        check(wear.ready(1, 70));
        for (int tick = 0; tick < 600; tick++) {
            for (int slot = 0; slot < 4; slot++) {
                if (wear.ready(slot, tick)) {
                    wear.lostDurability(slot, tick);
                    check(!wear.ready(slot, tick + 59));
                    check(wear.ready(slot, tick + 60));
                }
            }
        }
        UUID human = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        OwnerIndex index = new OwnerIndex();
        check(index.assign(human, first) == null);
        check(index.members(first).equals(Set.of(human)));
        Set<UUID> snapshot = index.members(first);
        check(index.assign(human, second).equals(first));
        check(index.members(first).isEmpty());
        check(index.members(second).equals(Set.of(human)));
        check(snapshot.equals(Set.of(human)));
        check(index.assign(human, second).equals(second));
        check(index.members(second).size() == 1);
        for (int i = 0; i < 1000; i++) {
            check(index.assign(human, second).equals(second));
            check(index.owner(human).equals(second));
        }
        check(index.assign(human, null).equals(second));
        check(index.members(second).isEmpty());
        check(index.owner(human) == null);
        check(index.assign(human, null) == null);
        check(new OwnerIndex().members(first).isEmpty());
        try { snapshot.clear(); throw new AssertionError("mutable ownership snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        BudgetedUpdates<Integer> queue = new BudgetedUpdates<>();
        for (int repeat = 0; repeat < 10; repeat++)
            for (int n = 0; n < 10_000; n++) queue.offer(n);
        check(queue.size() == 10_000);
        check(queue.drain(0).isEmpty());
        int expected = 0;
        while (queue.size() > 0) {
            List<Integer> batch = queue.drain(32);
            check(batch.size() <= 32);
            for (int value : batch) check(value == expected++);
        }
        check(expected == 10_000);
        queue.offer(1);
        List<Integer> detached = queue.drain(1);
        queue.offer(1);
        check(detached.equals(List.of(1)) && queue.size() == 1);
        try { queue.drain(-1); throw new AssertionError("negative budget"); }
        catch (IllegalArgumentException expectedError) { checks++; }
        System.out.println("RuntimePoliciesTest: " + checks + " checks passed");
    }
}
