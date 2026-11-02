package dev.felix.hostilehumans.inventory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** Exhaustive, bounded selection: finish a committed pile before considering another. */
public final class PileFirstSelection<T, K> {
    private Iterator<T> remaining;
    private final Supplier<Iterator<T>> broad;
    private final Function<T, K> key;
    private final Predicate<T> inPile;
    private final HashSet<K> seen = new HashSet<>();
    private boolean pileOnly;
    private boolean done;
    private T best;
    private T deferred;

    public PileFirstSelection(Iterator<T> initial, boolean pileOnly, Supplier<Iterator<T>> broad,
                              Function<T, K> key, Predicate<T> inPile) {
        this.remaining = initial;
        this.pileOnly = pileOnly;
        this.broad = broad;
        this.key = key;
        this.inPile = inPile;
    }

    public boolean isDone() { return done; }
    public boolean isPileOnly() { return pileOnly; }

    public T advance(int budget, ToDoubleFunction<T> score) {
        if (budget < 0) throw new IllegalArgumentException("negative selection budget");
        if (done) return best;
        if (budget == 0) return null;
        double bestScore = best == null ? Double.NEGATIVE_INFINITY : score.applyAsDouble(best);
        if (Double.isNaN(bestScore)) return null; // Unknown/deferred work must retain its candidate.
        if (bestScore == Double.NEGATIVE_INFINITY) best = null;
        int examined = 0;
        while (examined < budget) {
            if (deferred == null && !remaining.hasNext()) {
                if (pileOnly && best == null) {
                    Iterator<T> expanded = broad.get();
                    if (expanded == null) return null; // Deferred broad-phase query.
                    pileOnly = false;
                    remaining = expanded;
                    continue;
                }
                done = true;
                break;
            }
            T item = deferred == null ? remaining.next() : deferred;
            deferred = null;
            examined++;
            // A box's corners outside the pile sphere must remain available
            // to the broad stage; do not put those keys into the seen set yet.
            if (pileOnly && !inPile.test(item)) continue;
            if (seen.contains(key.apply(item))) continue;
            double candidateScore = score.applyAsDouble(item);
            if (Double.isNaN(candidateScore)) { deferred = item; return null; }
            seen.add(key.apply(item));
            if (candidateScore > bestScore) {
                best = item;
                bestScore = candidateScore;
            }
        }
        return done ? best : null;
    }
}
