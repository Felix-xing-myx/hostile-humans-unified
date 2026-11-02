package dev.felix.hostilehumans.soldier;

import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;

/** Server-thread transaction: check all slots before changing any stack. */
public final class RecruitmentPayment {
    private RecruitmentPayment() {}

    public static boolean pay(boolean creative, int cost, int slots, IntUnaryOperator matchingCount,
                       ObjIntConsumer<Integer> remove) {
        if (creative) return true;
        if (cost < 0) throw new IllegalArgumentException("Negative recruitment cost");
        long total = 0;
        for (int slot = 0; slot < slots; slot++) total += matchingCount.applyAsInt(slot);
        if (total < cost) return false;
        for (int slot = 0; slot < slots && cost > 0; slot++) {
            int take = Math.min(cost, matchingCount.applyAsInt(slot));
            if (take > 0) remove.accept(slot, take);
            cost -= take;
        }
        return true;
    }
}
