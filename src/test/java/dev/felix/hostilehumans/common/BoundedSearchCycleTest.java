package dev.felix.hostilehumans.common;

/** Actual candidate work, restart and timeout boundaries; no game-world claim. */
public final class BoundedSearchCycleTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        var cycle = new BoundedSearchCycle(); cycle.begin(10);
        check(cycle.outcome(169) == BoundedSearchCycle.Outcome.OPEN);
        check(cycle.outcome(170) == BoundedSearchCycle.Outcome.DEFERRED);
        check(cycle.remainingWork() == 128);
        cycle.work(32); check(cycle.outcome(170) == BoundedSearchCycle.Outcome.EXHAUSTED);
        cycle.reset(); cycle.begin(0); cycle.work(31); cycle.begin(159);
        check(cycle.outcome(160) == BoundedSearchCycle.Outcome.DEFERRED);
        check(cycle.remainingWork() == 97); cycle.work(1); check(cycle.outcome(160) == BoundedSearchCycle.Outcome.EXHAUSTED);
        cycle.reset(); cycle.begin(100); cycle.work(128);
        check(cycle.outcome(101) == BoundedSearchCycle.Outcome.EXHAUSTED);
        check(cycle.remainingWork() == 0);
        cycle.begin(0); check(cycle.outcome(1) == BoundedSearchCycle.Outcome.OPEN);
        cycle.work(-1); cycle.work(Integer.MAX_VALUE);
        check(cycle.outcome(1) == BoundedSearchCycle.Outcome.EXHAUSTED);
    }
}
