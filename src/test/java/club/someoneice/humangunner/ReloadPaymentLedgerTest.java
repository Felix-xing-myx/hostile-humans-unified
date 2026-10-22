package club.someoneice.humangunner;

/** Deterministic cycle/payment checks; native TaCZ integration still requires live acceptance. */
public final class ReloadPaymentLedgerTest {
    private static int checks;
    public static void main(String[] args) {
        var ledger = new ReloadPaymentLedger<Object, Object>();
        Object holder = new Object(), gun = new Object();
        check(GunReloadPolicy.shouldIdleTopOff(37, 150, false, false, false, false)
                && !GunReloadPolicy.shouldIdleTopOff(38, 150, false, false, false, false),
                "150-round virtual magazine uses the existing strict idle threshold");
        check(!GunReloadPolicy.reloadThresholdSatisfied(1, 150, true)
                && GunReloadPolicy.reloadThresholdSatisfied(0, 150, true),
                "virtual magazine must empty before a combat reload");
        check(GunReloadPolicy.shouldIdleTopOff(7, 30, false, false, false, false)
                && !GunReloadPolicy.shouldIdleTopOff(8, 30, false, false, false, false),
                "30-round idle gun reloads below25 percent only");
        check(GunReloadPolicy.reloadThresholdSatisfied(9, 40, false)
                && !GunReloadPolicy.reloadThresholdSatisfied(10, 40, false),
                "exactly25 percent is not below the threshold");
        check(GunReloadPolicy.reloadThresholdSatisfied(0, 30, true)
                && !GunReloadPolicy.reloadThresholdSatisfied(1, 30, true),
                "combat waits for the last real round");
        check(!GunReloadPolicy.reloadThresholdSatisfied(0, 0, false)
                && !GunReloadPolicy.reloadThresholdSatisfied(-1, 30, true),
                "invalid counts/capacity cannot authorize a reload");
        int fullInventoryGun = GunReloadPolicy.capacityCheckedRounds(29, true, true, false);
        check(fullInventoryGun == 30 && !GunReloadPolicy.shouldIdleTopOff(fullInventoryGun, 30,
                false, false, false, false), "chambered inventory-feed round prevents repeated full-gun top-offs");
        check(GunReloadPolicy.capacityCheckedRounds(29, true, false, false) == 29,
                "ordinary magazine keeps its native capacity semantics");
        check(GunReloadPolicy.capacityCheckedRounds(30, true, true, true) == 30,
                "open bolts do not invent chamber capacity");
        check(GunReloadPolicy.inventoryFeedDurationTicks(0.5D, 0.5D) == 20,
                "inventory feeding uses pack duration, not a stall recovery timeout");
        check(GunReloadPolicy.inventoryFeedDurationTicks(Double.NaN, -2.0D) == 1,
                "invalid inventory-feed timing remains bounded");
        var observed = new ReloadPaymentLedger<Object, Object>();
        check(observed.begin(holder, gun, 100, () -> true, () -> true, () -> true), "observation fixture starts paid cycle");
        check(observed.peekWeapon(holder, 99) == gun && observed.peekWeapon(holder, 100) == null, "peek respects deadline");
        check(observed.peekWeapon(holder, 99) == gun, "peek beyond expiry does not mutate execution ledger");
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
        check(ledger.weapon(holder, 10) == gun, "background lookup retains the original paid gun");
        Object mainHandSword = new Object();
        check(ledger.weapon(holder, 11) != mainHandSword && ledger.authorized(holder, gun, 11),
                "temporary melee hand does not replace the bound background weapon or pay again");
        check(!ledger.authorized(holder, new Object(), 10), "replacement gun cannot reuse payment");
        check(!ledger.authorized(holder, gun, 11), "switching back does not revive payment");
        check(ledger.begin(holder, gun, 100, () -> true, () -> true,
                () -> { magazines[0]--; return true; }), "new cycle requires new payment");
        check(magazines[0] == 0 && ledger.authorized(holder, gun, 99),
                "last paid magazine retains active cycle");
        check(!ledger.authorized(holder, gun, 100), "hard deadline removes authorization");
        check(ledger.weapon(holder, 100) == null, "expired background cycle has no retrievable weapon");
        check(!ledger.begin(holder, gun, 200, () -> magazines[0] > 0, () -> true, () -> true),
                "timeout never gives a free retry");
        magazines[0] = 1;
        check(ledger.begin(holder, gun, 200, () -> true, () -> true,
                () -> { magazines[0]--; return true; }), "resupply permits new cycle");
        ledger.end(holder);
        check(!ledger.authorized(holder, gun, 101), "cancel discards payment without refund");
        check(!new ReloadPaymentLedger<Object, Object>().authorized(holder, gun, 101),
                "fresh runtime cannot restore persisted authorization");
        callbackTransactions();
        System.out.println("ReloadPaymentLedgerTest: " + checks + " checks passed");
    }

    private static void callbackTransactions() {
        var ledger = new ReloadPaymentLedger<Object, Object>();
        Object owner = new Object(), original = new Object();
        int[] payments = {0}, starts = {0}, commits = {0}, aborts = {0};
        check(ledger.begin(owner, original, 100, () -> true, () -> true, () -> {
            starts[0]++;
            check(!ledger.begin(owner, original, 100, () -> true,
                    () -> { throw new AssertionError("nativeStart re-entered"); },
                    () -> { throw new AssertionError("nested payment"); }), "nativeStart cannot re-enter begin");
            return true;
        }, () -> { payments[0]++; return true; }), "outer callback starts one cycle");
        check(starts[0] == 1 && payments[0] == 1, "native re-entry has one payment");
        ledger.end(owner);

        check(!ledger.begin(owner, original, 100, () -> true, () -> true, () -> {
            ledger.end(owner);
            check(!ledger.begin(owner, original, 100, () -> true, () -> true,
                    () -> { payments[0]++; return true; }), "cancel does not unlock nested begin");
            return true;
        }, () -> { payments[0]++; return true; }), "cancelled nativeStart cannot register afterwards");
        check(payments[0] == 1 && !ledger.authorized(owner, original, 1), "cancel before payment has no debit");

        long[] session = {7};
        Object[] actualWeapon = {original}, state = {new Object()}, inventory = {new Object()};
        Object expectedState = state[0], expectedInventory = inventory[0];
        java.util.function.BooleanSupplier valid = () -> session[0] == 7 && actualWeapon[0] == original
                && state[0] == expectedState && inventory[0] == expectedInventory;
        check(!ledger.begin(owner, original, 100, valid, () -> true,
                () -> { session[0]++; return true; }, () -> { payments[0]++; return true; }),
                "native callback cannot adopt a new behavior session");
        check(payments[0] == 1, "session invalidation checked before debit");
        session[0] = 7;
        check(!ledger.begin(owner, original, 100, valid, () -> true,
                () -> { actualWeapon[0] = new Object(); return true; }, () -> { payments[0]++; return true; }),
                "same-model replacement cannot inherit the original request");
        actualWeapon[0] = original;
        check(!ledger.begin(owner, original, 100, valid, () -> true,
                () -> { inventory[0] = new Object(); return true; }, () -> { payments[0]++; return true; }),
                "native callback cannot debit an owner-edited inventory");
        inventory[0] = expectedInventory;
        check(!ledger.begin(owner, original, 100, valid, () -> true,
                () -> { state[0] = new Object(); return true; }, () -> { payments[0]++; return true; }),
                "native callback cannot publish over a different state object");
        state[0] = expectedState;
        check(payments[0] == 1, "all rejected source changes occurred before payment");

        check(!ledger.begin(owner, original, 100, valid, () -> true, () -> true, () -> {
            payments[0]++;
            ledger.end(owner);
            return true;
        }), "cancellation during debit cannot re-register authorization");
        check(payments[0] == 2 && !ledger.authorized(owner, original, 1),
                "opaque partial debit is not refunded or retried using stale authority");

        check(!ledger.begin(owner, original, 100, valid, () -> true, () -> false, () -> true, () -> {
            aborts[0]++;
            ledger.end(owner);
            check(!ledger.begin(owner, original, 100, () -> true, () -> true, () -> true),
                    "abort rollback cannot start another cycle on the same callback stack");
        }), "native refusal runs guarded abort");
        check(aborts[0] == 1, "abort executes once");
        boolean threw = false;
        try {
            ledger.begin(owner, original, 100, valid, () -> true,
                    () -> { throw new IllegalStateException("native start failed"); },
                    () -> { payments[0]++; return true; }, () -> aborts[0]++);
        } catch (IllegalStateException expected) { threw = true; }
        check(threw && aborts[0] == 2 && payments[0] == 2 && !ledger.authorized(owner, original, 1),
                "throwing native start releases attempt and never pays");

        check(ledger.begin(owner, original, 100, valid, () -> true, () -> true,
                () -> { payments[0]++; return true; }), "fresh request starts after callback unwind");
        session[0]++;
        check(!ledger.complete(owner, original, 1, valid, () -> commits[0]++),
                "completion revalidates captured session immediately before commit");
        check(commits[0] == 0 && !ledger.authorized(owner, original, 1), "invalid completion retires old payment");
        session[0] = 7;
        check(ledger.begin(owner, original, 100, valid, () -> true, () -> true, () -> true), "completion setup");
        check(ledger.complete(owner, original, 1, valid, () -> {
            commits[0]++;
            check(!ledger.complete(owner, original, 1, valid, () -> commits[0]++), "completion cannot re-enter");
            check(!ledger.begin(owner, original, 100, () -> true, () -> true, () -> true),
                    "claimed completion cannot register nested replacement");
        }), "valid completion commits once");
        check(commits[0] == 1 && !ledger.complete(owner, original, 1, valid, () -> commits[0]++),
                "old completion never runs twice");
        check(ledger.begin(owner, original, 100, valid, () -> true, () -> true, () -> true), "throwing completion setup");
        threw = false;
        try {
            ledger.complete(owner, original, 1, valid, () -> {
                commits[0]++;
                throw new IllegalStateException("opaque partial energy write");
            });
        } catch (IllegalStateException expected) { threw = true; }
        check(threw && commits[0] == 2 && !ledger.complete(owner, original, 1, valid, () -> commits[0]++),
                "throwing completion remains claimed and cannot repeat its partial write");
        check(ledger.begin(owner, original, 100, valid, () -> true, () -> true, () -> true), "cancelled completion setup");
        check(!ledger.complete(owner, original, 1, valid, () -> {
            commits[0]++;
            session[0]++;
            ledger.end(owner);
            check(!ledger.begin(owner, original, 100, () -> true, () -> true, () -> true),
                    "completion cancellation retains its inflight barrier");
        }), "completion checks the captured session again after its opaque callback");
        check(commits[0] == 3 && !ledger.complete(owner, original, 1, () -> true, () -> commits[0]++),
                "a cancelled partial completion cannot replay even under a new valid request");
        check(!ledger.begin(owner, original, 100, () -> {
            ledger.end(owner);
            return true;
        }, () -> { throw new AssertionError("cancelled validity reached payment check"); },
                () -> true, () -> true), "validation callback cannot cancel then return stale success");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
