package dev.felix.hostilehumans.performance;

import dev.felix.hostilehumans.performance.ScopedWorkBudget;
import java.util.Arrays;

public final class ScopedWorkBudgetTest {
    public static void main(String[] args) {
        for (int dimensions = 2; dimensions <= 4; dimensions++) fairness(dimensions);
        batchesAndLimits();
        cancellation();
        cancellationWithoutGrant();
        expiryAndRollback();
        changedRequest();
        reducedRequest();
        rotationAfterDirectGrant();
        factionRotation();
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
        b.cancel("b");
        check(b.claimFull(1, "b", "y", 1) == 0, "cancelled key cannot reclaim the reservation");
        check(b.claimFull(1, "d", "y", 1) == 1, "unused scope and global capacity are returned");
        b.cancel("d");
        check(b.claimFull(1, "e", "z", 1) == 0, "consumed capacity is not returned");
        check(b.claimFull(2, "c", "z", 1) == 1, "oldest uncancelled caller eventually runs");
    }
    private static void cancellationWithoutGrant() {
        var budget = new ScopedWorkBudget<String, String>(3, 1, 1, 40);
        check(budget.claimFull(0, "a", "x", 1) == 1, "initialize tick and use scope x");
        budget.cancel("new");
        check(budget.claimFull(0, "new", "y", 1) == 1, "cancelling an absent key created no grant");
        check(budget.claimFull(0, "waiting", "x", 1) == 0, "scope x is full");
        budget.cancel("waiting");
        check(budget.claimFull(0, "waiting", "z", 1) == 1, "cancelled wait did not use a grant");
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
        check(b.claimFull(1, "b", "x", 2) == 2, "both budgets recover the old scope reservation");
        b.cancel("b");
        check(b.claimFull(2, "a", "y", 1) == 1, "changed request retries in its new scope next tick");
    }
    private static void reducedRequest() {
        var budget = new ScopedWorkBudget<String, String>(4, 3, 3, 40);
        check(budget.claimFull(0, "a", "x", 3) == 3, "request three for next tick");
        check(budget.claimFull(1, "a", "x", 1) == 1, "consume one of the three reserved units");
        check(budget.claimFull(1, "b", "x", 2) == 2, "reuse the released scope capacity");
        check(budget.claimFull(1, "c", "x", 1) == 0, "scope cap remains in force");
        check(budget.claimFull(1, "d", "y", 1) == 1, "other scope can use the last global unit");
        check(budget.claimFull(1, "e", "y", 1) == 0, "global cap remains in force");
    }
    private static void rotationAfterDirectGrant() {
        var perception = new ScopedWorkBudget<Integer, String>(1, 1, 1, 40);
        var navigation = new FairWorkBudget<Integer>(1, 1, 40);
        check(perception.claimFull(0, 0, "arena", 1, "red") == 1, "red uses spare perception capacity");
        check(navigation.claimFull(0, 0, 1, "red") == 1, "red uses spare navigation capacity");
        perception.claimFull(0, 1, "arena", 1, "red");
        navigation.claimFull(0, 1, 1, "red");
        perception.claimFull(0, 2, "arena", 1, "blue");
        navigation.claimFull(0, 2, 1, "blue");
        check(perception.claimFull(1, 1, "arena", 1, "red") == 0, "blue gets the next perception turn");
        check(navigation.claimFull(1, 1, 1, "red") == 0, "blue gets the next navigation turn");
        check(perception.claimFull(1, 2, "arena", 1, "blue") == 1, "blue claims its perception reservation");
        check(navigation.claimFull(1, 2, 1, "blue") == 1, "blue claims its navigation reservation");
    }

    private static void factionRotation() {
        var perception = new ScopedWorkBudget<Integer, String>(1, 1, 1, 40);
        var navigation = new FairWorkBudget<Integer>(1, 1, 40);
        int bluePerception = 0, blueNavigation = 0;
        for (int tick = 0; tick < 100; tick++) {
            int totalPerception = 0, totalNavigation = 0;
            for (int actor = 0; actor < 25; actor++) {
                String faction = actor == 24 ? "blue" : "red";
                int visual = perception.claimFull(tick, actor, "arena", 1, faction);
                int route = navigation.claimFull(tick, actor, 1, faction);
                totalPerception += visual;
                totalNavigation += route;
                if (actor == 24) { bluePerception += visual; blueNavigation += route; }
            }
            check(totalPerception == 1 && totalNavigation == 1, "round-robin still uses the hard budget exactly once");
        }
        check(bluePerception >= 48 && blueNavigation >= 48, "a long red queue must not starve the blue faction");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
