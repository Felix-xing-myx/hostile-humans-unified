package dev.felix.hostilehumans.behavior.combat.gun;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Server-thread, transient authorization: never serialized or transferable between guns. */
public final class ReloadPaymentLedger<K, W> {
    private record Cycle<W>(W weapon, long deadline) {}
    private static final class Attempt { boolean cancelled; }
    private final Map<K, Cycle<W>> cycles = new WeakHashMap<>();
    private final Map<K, Attempt> starting = new WeakHashMap<>();

    public boolean begin(K holder, W weapon, long deadline, BooleanSupplier canPay,
                  BooleanSupplier nativeStart, BooleanSupplier pay) {
        return begin(holder, weapon, deadline, () -> true, canPay, nativeStart, pay);
    }

    boolean begin(K holder, W weapon, long deadline, BooleanSupplier valid,
                  BooleanSupplier canPay, BooleanSupplier nativeStart, BooleanSupplier pay) {
        return begin(holder, weapon, deadline, valid, canPay, nativeStart, pay, () -> { });
    }

    public boolean begin(K holder, W weapon, long deadline, BooleanSupplier valid,
                  BooleanSupplier canPay, BooleanSupplier nativeStart, BooleanSupplier pay, Runnable abort) {
        if (cycles.containsKey(holder) || starting.containsKey(holder)) return false;
        Attempt attempt = new Attempt();
        starting.put(holder, attempt);
        boolean committed = false;
        try {
            if (!current(holder, attempt, valid) || !canPay.getAsBoolean()
                    || !current(holder, attempt, valid)) return false;
            if (!nativeStart.getAsBoolean() || !current(holder, attempt, valid)) return false;
            if (!pay.getAsBoolean() || !current(holder, attempt, valid)) return false;
            cycles.put(holder, new Cycle<>(weapon, deadline));
            committed = true;
            return true;
        } finally {
            // Keep the attempt present even after end() until its callback stack
            // unwinds. Cancel-then-begin cannot register a nested replacement.
            try { if (!committed) abort.run(); }
            finally { if (starting.get(holder) == attempt) starting.remove(holder); }
        }
    }

    private boolean current(K holder, Attempt attempt, BooleanSupplier valid) {
        return starting.get(holder) == attempt && !attempt.cancelled && valid.getAsBoolean()
                && starting.get(holder) == attempt && !attempt.cancelled;
    }

    public boolean authorized(K holder, W weapon, long now) {
        Cycle<W> cycle = cycles.get(holder);
        if (cycle == null) return false;
        if (cycle.weapon() != weapon || now >= cycle.deadline()) {
            cycles.remove(holder);
            return false;
        }
        return true;
    }

    public boolean complete(K holder, W weapon, long now, BooleanSupplier valid, Runnable commit) {
        if (starting.containsKey(holder) || !authorized(holder, weapon, now)) return false;
        Cycle<W> cycle = cycles.get(holder);
        Attempt attempt = new Attempt();
        starting.put(holder, attempt);
        try {
            if (!current(holder, attempt, valid) || cycles.get(holder) != cycle) return false;
            cycles.remove(holder); // Claim before an external callback, including a throwing one.
            if (!current(holder, attempt, valid)) return false;
            commit.run();
            return current(holder, attempt, valid);
        } finally {
            if (cycles.get(holder) == cycle) cycles.remove(holder);
            if (starting.get(holder) == attempt) starting.remove(holder);
        }
    }

    public boolean startingCancelled(K holder) {
        Attempt attempt=starting.get(holder);
        return attempt!=null&&attempt.cancelled;
    }

    public void end(K holder) {
        cycles.remove(holder);
        Attempt attempt = starting.get(holder);
        if (attempt != null) attempt.cancelled = true;
    }

    public W weapon(K holder, long now) {
        Cycle<W> cycle = cycles.get(holder);
        if (cycle == null) return null;
        if (now >= cycle.deadline()) {
            cycles.remove(holder);
            return null;
        }
        return cycle.weapon();
    }

    /** Inspection cannot expire/remove a cycle; expiry is settled by the execution path. */
    public W peekWeapon(K holder, long now) {
        Cycle<W> cycle = cycles.get(holder);
        return cycle != null && now < cycle.deadline() ? cycle.weapon() : null;
    }
}
