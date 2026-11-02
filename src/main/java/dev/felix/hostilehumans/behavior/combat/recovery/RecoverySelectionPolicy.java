package dev.felix.hostilehumans.behavior.combat.recovery;

/** Ordering uses effective restoration, never multiplies the stack's stock value. */
final class RecoverySelectionPolicy {
    record Candidate(int slot, int rank, double healing, int duration, double quality) { }
    private RecoverySelectionPolicy() { }
    /** All currently valid non-slowing supplies precede food while escaping. Ordinary order stays unchanged. */
    static int retreatRank(int rank, boolean food, boolean fleeing) { return rank + (food && fleeing ? 8 : 0); }
    static boolean better(Candidate candidate, Candidate current) {
        if (current == null) return true;
        if (candidate.rank() != current.rank()) return candidate.rank() < current.rank();
        if (Double.isFinite(candidate.healing()) && Double.isFinite(current.healing())
                && Math.abs(candidate.healing() - current.healing()) > 1)
            return candidate.healing() > current.healing();
        if (candidate.duration() != current.duration()) return candidate.duration() < current.duration();
        if (candidate.quality() != current.quality()) return candidate.quality() < current.quality();
        return candidate.slot() < current.slot();
    }
}
