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
