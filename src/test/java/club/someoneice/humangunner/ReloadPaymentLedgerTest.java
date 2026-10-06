package club.someoneice.humangunner;

/** Deterministic cycle/payment checks; native TaCZ integration still requires live acceptance. */
public final class ReloadPaymentLedgerTest {
    private static int checks;
    public static void main(String[] args) {
        var ledger = new ReloadPaymentLedger<Object, Object>();
        Object holder = new Object(), gun = new Object();
        int[] magazines = {2}, starts = {0};
        check(!ledger.begin(holder, gun, 100, () -> false,
                () -> { starts[0]++; return true; }, () -> { magazines[0]--; return true; }),
                "insufficient inventory rejects before native start");
        check(starts[0] == 0 && magazines[0] == 2, "insufficient inventory has no side effects");
        check(!ledger.begin(holder, gun, 100, () -> true,
                () -> false, () -> { magazines[0]--; return true; }), "native refusal rejected");
        check(magazines[0] == 2, "native refusal costs nothing");
        check(!ledger.begin(holder, gun, 100, () -> true, () -> true, () -> false),
                "inventory changed during native start cannot authorize unpaid feeding");
        check(!ledger.authorized(holder, gun, 1), "failed payment creates no transaction");
        check(ledger.begin(holder, gun, 100, () -> true,
                () -> true, () -> { magazines[0]--; return true; }), "valid cycle starts");
        check(!ledger.begin(holder, gun, 100, () -> true,
                () -> { throw new AssertionError("duplicate entered native start"); },
                () -> { throw new AssertionError("duplicate paid"); }), "duplicate start rejected");
        for (int shell = 1; shell < 10; shell++)
            check(ledger.authorized(holder, gun, shell), "one paid manual cycle covers later shells");
        check(magazines[0] == 1, "manual cycle paid once");
        check(!ledger.authorized(holder, new Object(), 10), "replacement gun cannot reuse payment");
        check(!ledger.authorized(holder, gun, 11), "switching back does not revive payment");
        check(ledger.begin(holder, gun, 100, () -> true, () -> true,
                () -> { magazines[0]--; return true; }), "new cycle requires new payment");
        check(magazines[0] == 0 && ledger.authorized(holder, gun, 99),
                "last paid magazine retains active cycle");
        check(!ledger.authorized(holder, gun, 100), "hard deadline removes authorization");
        check(!ledger.begin(holder, gun, 200, () -> magazines[0] > 0, () -> true, () -> true),
                "timeout never gives a free retry");
        magazines[0] = 1;
        check(ledger.begin(holder, gun, 200, () -> true, () -> true,
                () -> { magazines[0]--; return true; }), "resupply permits new cycle");
        ledger.end(holder);
        check(!ledger.authorized(holder, gun, 101), "cancel discards payment without refund");
        check(!new ReloadPaymentLedger<Object, Object>().authorized(holder, gun, 101),
                "fresh runtime cannot restore persisted authorization");
        System.out.println("ReloadPaymentLedgerTest: " + checks + " checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
