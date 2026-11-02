package dev.felix.hostilehumans.navigation;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Entity-local exact-route ownership. Search trials never enter this state machine. */
public final class RouteExecutionLease<P, T> {
    private P installed;
    private T owner;
    private long revision;
    public T owner() { return owner; }
    public boolean owns(P actual, Predicate<T> live) {
        return actual != null && actual == installed && owner != null && live.test(owner);
    }
    public boolean install(T source, boolean gated, Predicate<T> live, Supplier<P> current,
                           BooleanSupplier operation, Runnable discard) {
        if (gated && (source == null || !live.test(source))) return false;
        long generation = revision;
        boolean accepted = operation.getAsBoolean();
        if (revision != generation) return false; // A nested replacement has already claimed ownership.
        if (gated && !live.test(source)) {
            try { discard.run(); } finally { if (revision == generation) stopped(); }
            return false;
        }
        P actual = current.get();
        if (accepted && actual != null) { installed = actual; owner = source; revision++; }
        else if (actual != installed) stopped();
        return accepted;
    }
    public boolean mayStop(P actual, T caller, boolean gated, Predicate<T> live) {
        if (!gated) return true;
        if (caller != null && caller.equals(owner) && (actual == installed || actual == null)) return true;
        return actual == null || actual == installed && (owner == null || !live.test(owner));
    }
    public void recomputed(P before, P after, T caller, Predicate<T> live) {
        if (before == installed && after != before && owner != null && owner.equals(caller) && live.test(owner)) {
            installed = after; revision++;
            if (after == null) owner = null;
        }
    }
    public void stopped() { installed = null; owner = null; revision++; }
}
