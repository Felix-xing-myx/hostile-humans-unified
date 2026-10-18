package dev.felix.hostilehumans.core;

import java.util.ArrayDeque;

/** Source-local health accounting. Callers merge only child receipts for the same entity. */
public final class HealthLossReceipt {
    private static final class Write {
        final double before;
        double nested;
        Write(double before) { this.before = before; }
    }
    private final double before;
    private double nested, observedLoss;
    private boolean observed;
    private final ArrayDeque<Write> writes = new ArrayDeque<>();
    public HealthLossReceipt(double before) { this.before = before; }
    public void beginWrite(double health) { writes.push(new Write(health)); }
    public double endWrite(double health) {
        if (writes.isEmpty()) return 0;
        Write write = writes.pop();
        double total = loss(write.before, health);
        if (!writes.isEmpty()) writes.peek().nested += total;
        double own = Math.max(0, total - write.nested);
        observed = true; observedLoss += own;
        return own;
    }
    /** Propagate the complete same-entity subtree, including grandchildren, exactly once. */
    public void childDamage(double subtree) {
        if (!Double.isFinite(subtree) || subtree <= 0) return;
        nested += subtree;
        if (!writes.isEmpty()) writes.peek().nested += subtree;
    }
    public double ownLoss(double health) { return observed ? observedLoss : Math.max(0, loss(before,health)-nested); }
    public double subtreeLoss(double health) { return ownLoss(health) + nested; }
    private static double loss(double before, double after) {
        return Double.isFinite(before) && Double.isFinite(after) ? Math.max(0, Math.min(before,before-after)) : 0;
    }
}
