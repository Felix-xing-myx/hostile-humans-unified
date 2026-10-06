package club.someoneice.humangunner;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Server-thread, transient authorization: never serialized or transferable between guns. */
final class ReloadPaymentLedger<K, W> {
    private record Cycle<W>(W weapon, long deadline) {}
    private final Map<K, Cycle<W>> cycles = new WeakHashMap<>();

    boolean begin(K holder, W weapon, long deadline, BooleanSupplier canPay,
                  BooleanSupplier nativeStart, BooleanSupplier pay) {
        if (cycles.containsKey(holder) || !canPay.getAsBoolean()) return false;
        if (!nativeStart.getAsBoolean() || !pay.getAsBoolean()) return false;
        cycles.put(holder, new Cycle<>(weapon, deadline));
        return true;
    }

    boolean authorized(K holder, W weapon, long now) {
        Cycle<W> cycle = cycles.get(holder);
        if (cycle == null) return false;
        if (cycle.weapon() != weapon || now >= cycle.deadline()) {
            cycles.remove(holder);
            return false;
        }
        return true;
    }

    void end(K holder) { cycles.remove(holder); }
}
