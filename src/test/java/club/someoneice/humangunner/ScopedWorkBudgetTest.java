package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.ScopedWorkBudget;
import java.util.Arrays;

public final class ScopedWorkBudgetTest {
    public static void main(String[] args) {
        for (int dimensions = 2; dimensions <= 4; dimensions++) fairness(dimensions);
        batchesAndLimits();
        cancellation();
        expiryAndRollback();
        changedRequest();
        System.out.println("PASS: atomic scope/server budgets, 1000-tick fairness, cancellation, expiry and rollback");
    }
    private static void fairness(int dimensions) {
        ScopedWorkBudget<Integer, Integer> budget = new ScopedWorkBudget<>(2, 1, 1, 40);
        int[] count = new int[dimensions * 2];
        int[] last = new int[count.length];
        Arrays.fill(last, -1);
        int maximumWait = 0;
        for (int tick = 0; tick < 1000; tick++) {
            int total = 0;
            int[] scoped = new int[dimensions];
            for (int key = 0; key < count.length; key++) {
                int scope = key / 2;
                int grant = budget.claimFull(tick, key, scope, 1);
                total += grant;
                scoped[scope] += grant;
                if (grant > 0) {
                    maximumWait = Math.max(maximumWait, tick - last[key]);
                    last[key] = tick;
                    count[key]++;
                }
                check(budget.claimFull(tick, key, scope, 1) == 0, "same key gets only one batch");
            }
            check(total == 2, "busy eligible scopes keep full global throughput");
            for (int amount : scoped) check(amount <= 1, "scope limit");
        }
        int min = Arrays.stream(count).min().orElseThrow();
        int max = Arrays.stream(count).max().orElseThrow();
        check(min > 0 && max - min <= 1, "all dimensions and actors receive equal service");
        check(maximumWait <= dimensions * 2, "no long FIFO starvation");
        System.out.println(dimensions + " dimensions: " + Arrays.toString(count));
    }
    private static void batchesAndLimits() {
        ScopedWorkBudget<String, String> b = new ScopedWorkBudget<>(5, 3, 3, 40);
        check(b.claimFull(0, "a", "x", 2) == 2, "full batch");
        check(b.claimFull(0, "b", "x", 2) == 0, "never partial scope batch");
        check(b.claimFull(0, "c", "y", 3) == 3, "other scope uses remaining global capacity");
        check(b.claimFull(0, "d", "z", 1) == 0, "global cap");
        check(b.claimFull(0, "a", "x", 1) == 0, "smaller second batch blocked");
        boolean invalid = false;
        try { b.claimFull(1, "bad", "x", 4); } catch (IllegalArgumentException expected) { invalid = true; }
        check(invalid, "oversized requests rejected");
        check(b.claimFull(1, "zero", "x", 0) == 0, "zero request does no work");
    }
    private static void cancellation() {
        ScopedWorkBudget<String, String> b = new ScopedWorkBudget<>(1, 1, 1, 40);
        check(b.claimFull(0, "a", "x", 1) == 1, "initial grant");
        b.cancel("a");
        check(b.claimFull(0, "a", "x", 1) == 0, "cancel restart cannot bypass same-tick cap");
        check(b.claimFull(0, "b", "y", 1) == 0, "queue second caller");
        check(b.claimFull(1, "c", "z", 1) == 0, "older b reserved tick capacity");
        b.cancel("b");
        check(b.claimFull(1, "d", "z", 1) == 0, "cancel reserved batch never refunds");
        check(b.claimFull(2, "c", "z", 1) == 1, "oldest uncancelled caller eventually runs");
    }
    private static void expiryAndRollback() {
        ScopedWorkBudget<String, String> b = new ScopedWorkBudget<>(1, 1, 1, 1);
        b.claimFull(0, "a", "x", 1);
        b.claimFull(0, "b", "y", 1);
        check(b.claimFull(3, "c", "z", 1) == 1, "forgotten requests expire without ghost reservations");
        b.claimFull(3, "d", "y", 1);
        check(b.claimFull(1, "fresh", "z", 1) == 1, "time rollback drops future queue and capacity");
        check(b.claimFull(1, "other", "x", 1) == 0, "rollback epoch still enforces cap");
    }
    private static void changedRequest() {
        ScopedWorkBudget<String, String> b = new ScopedWorkBudget<>(2, 2, 2, 40);
        check(b.claimFull(0, "a", "x", 1) == 1, "scope x initial grant");
        check(b.claimFull(1, "a", "y", 1) == 0, "scope x reservation cannot be spent in y");
        check(b.claimFull(1, "b", "z", 2) == 0, "changed scope does not refund old reservation");
        check(b.claimFull(2, "a", "y", 1) == 0, "unserved full batch keeps priority over changed reservation");
        check(b.claimFull(2, "b", "z", 2) == 2, "older unserved full batch runs atomically");
        check(b.claimFull(3, "a", "y", 1) == 1, "new scope request runs after older full batch");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
