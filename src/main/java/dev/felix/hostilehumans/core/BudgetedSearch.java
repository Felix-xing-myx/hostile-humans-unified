package dev.felix.hostilehumans.core;

import java.util.Iterator;
import java.util.function.Predicate;

/** Resumable ordered search: never drops candidates when a frame's budget is exhausted. */
public final class BudgetedSearch<T> {
    private final Iterator<T> remaining;

    public BudgetedSearch(Iterator<T> remaining) {
        this.remaining = remaining;
    }

    public T firstMatching(int budget, Predicate<? super T> accept) {
        if (budget < 0) throw new IllegalArgumentException("Negative search budget");
        for (int i = 0; i < budget && remaining.hasNext(); i++) {
            T candidate = remaining.next();
            if (accept.test(candidate)) return candidate;
        }
        return null;
    }

    public boolean hasRemaining() { return remaining.hasNext(); }
}
