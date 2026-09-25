package club.someoneice.humangunner;

import java.util.Arrays;
import java.util.UUID;

public final class RecruitmentCombatTest {
    private static int checks;
    public static void main(String[] args) {
        check(RecruitmentPayment.pay(true, 216, 36, i -> { throw new AssertionError("Creative inventory read"); },
                (i, n) -> { throw new AssertionError("Creative charged"); }), "creative skips inventory entirely");
        check(RecruitmentPolicy.hasContractClearance(true, false, true),
                "creative contract bypasses badge and retaliation clearance");
        check(RecruitmentPolicy.hasContractClearance(false, true, false),
                "survival contract accepts a neutral non-retaliating human");
        check(!RecruitmentPolicy.hasContractClearance(false, false, false),
                "survival contract still requires matching badge clearance");
        check(!RecruitmentPolicy.hasContractClearance(false, true, true),
                "survival contract cannot recruit a retaliating human");
        for (int tier = 0; tier < 4; tier++) {
            int cost = RecruitmentPolicy.cost(tier);
            for (int total = 0; total <= cost + 80; total++) {
                int[] slots = new int[36];
                int left = total;
                for (int i = 0; i < slots.length && left > 0; i++) {
                    slots[i] = Math.min(left, 16); left -= slots[i];
                }
                int[] before = slots.clone();
                boolean paid = RecruitmentPayment.pay(false, cost, slots.length, i -> slots[i],
                        (i, n) -> slots[i] -= n);
                check(paid == (total >= cost), "survival/adventure balance check");
                check(Arrays.stream(slots).sum() == (paid ? total - cost : total), "exact split-stack payment");
                check(paid || Arrays.equals(before, slots), "insufficient payment atomic");
                check(Arrays.stream(slots).allMatch(n -> n >= 0), "no stack underflow");
            }
        }
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        SoldierCombatMemory memory = new SoldierCombatMemory();
        check(!memory.timedOut(first, 0), "start target clock");
        check(!memory.timedOut(first, 599), "extended pursuit resists short kiting attempts");
        check(memory.timedOut(first, 600), "blocked target expires after 30 seconds without combat");
        memory.forget(first, 600);
        check(!memory.allowed(first, 659), "prevent immediate reacquisition");
        check(memory.allowed(second, 601), "other targets remain available");
        check(memory.allowed(first, 660), "retry after a short three-second reset");
        check(!memory.timedOut(first, 660), "new acquisition starts fresh");
        memory.hit(second, 1259);
        check(memory.timedOut(first, 1260), "unrelated damage cannot extend pursuit");
        memory.hit(first, 1260);
        check(!memory.timedOut(first, 1859), "effective hit extends combat");
        check(memory.timedOut(first, 1860), "stale combat still expires");
        memory.forget(first, 1860);
        check(!memory.allowed(first, 1861), "discarded target has a short re-lock delay");
        check(!memory.timedOut(second, 1861), "switch target starts fresh");

        SoldierCombatMemory pressureMemory = new SoldierCombatMemory();
        check(!pressureMemory.timedOut(first, 0), "pressure target starts fresh");
        pressureMemory.threatened(first, 500);
        check(!pressureMemory.timedOut(first, 1099), "incoming hostile action refreshes pursuit");
        check(pressureMemory.timedOut(first, 1100), "refreshed pursuit still has a finite limit");
        System.out.println("PASS: " + checks + " recruitment payment and combat-memory checks");
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
