package club.someoneice.humangunner;

import java.util.ArrayDeque;

/** Loaded-session intent. Samples contain final health loss, never attempted damage. */
final class RecoveryIntentPolicy {
    private record Loss(int tick, double amount) { }
    private final ArrayDeque<Loss> losses = new ArrayDeque<>();
    private int intentUntil = Integer.MIN_VALUE;
    private int lastDamage = Integer.MIN_VALUE;
    void record(int now, double amount, double maximum) {
        advance(now);
        if (!(amount > 0) || !Double.isFinite(amount) || !(maximum > 0)) return;
        lastDamage = now;
        // Merge a tick's losses; the 100-tick window has at most 100 entries.
        if (!losses.isEmpty() && losses.peekLast().tick() == now) amount += losses.removeLast().amount();
        losses.addLast(new Loss(now, amount));
        double total = losses.stream().mapToDouble(Loss::amount).sum();
        if (total > maximum * .25D) intentUntil = now + 600;
    }
    void advance(int now) {
        while (!losses.isEmpty() && now - losses.peekFirst().tick() >= 100) losses.removeFirst();
    }
    boolean wantsRegeneration(int now, double ratio) {
        advance(now);
        if (ratio < .30D) intentUntil = now + 600;
        return now < intentUntil;
    }
    boolean recentDamage(int now) { return lastDamage != Integer.MIN_VALUE && now >= lastDamage && now - lastDamage < 100; }
    void consumed() { intentUntil = Integer.MIN_VALUE; losses.clear(); }
}
