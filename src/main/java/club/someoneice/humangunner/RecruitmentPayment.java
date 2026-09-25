package club.someoneice.humangunner;

import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;

/** Server-thread transaction: check all slots before changing any stack. */
final class RecruitmentPayment {
    private RecruitmentPayment() {}

    static boolean pay(boolean creative, int cost, int slots, IntUnaryOperator emeraldCount,
                       ObjIntConsumer<Integer> remove) {
        if (creative) return true;
        if (cost < 0) throw new IllegalArgumentException("Negative recruitment cost");
        long total = 0;
        for (int slot = 0; slot < slots; slot++) total += emeraldCount.applyAsInt(slot);
        if (total < cost) return false;
        for (int slot = 0; slot < slots && cost > 0; slot++) {
            int take = Math.min(cost, emeraldCount.applyAsInt(slot));
            if (take > 0) remove.accept(slot, take);
            cost -= take;
        }
        return true;
    }
}
