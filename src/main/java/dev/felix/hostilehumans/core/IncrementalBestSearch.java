package dev.felix.hostilehumans.core;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Bounded exhaustive selection with an immediately usable provisional result. */
public final class IncrementalBestSearch<T, R> {
    private final Iterator<T> remaining;
    private R best;

    public IncrementalBestSearch(Iterator<T> remaining) { this.remaining = remaining; }

    public R advance(int budget, Function<? super T, ? extends R> evaluate,
                     ToDoubleFunction<? super R> score) {
        if (budget < 0) throw new IllegalArgumentException("negative budget");
        double bestScore = best == null ? Double.NEGATIVE_INFINITY : score.applyAsDouble(best);
        if (bestScore == Double.NEGATIVE_INFINITY) best = null;
        for (int i = 0; i < budget && remaining.hasNext(); i++) {
            R candidate = evaluate.apply(remaining.next());
            if (candidate == null) continue;
            double candidateScore = score.applyAsDouble(candidate);
            if (candidateScore > bestScore) {
                best = candidate;
                bestScore = candidateScore;
            }
        }
        return best;
    }

    public boolean hasRemaining() { return remaining.hasNext(); }
}
