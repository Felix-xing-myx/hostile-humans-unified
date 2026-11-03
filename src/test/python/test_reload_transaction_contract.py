"""Source-level transaction boundaries only; Java state tests are not executed here."""
from source_paths import SourcePath as Path
import unittest

CORE = Path(__file__).resolve().parents[3] / "src/main/java/dev/felix/hostilehumans"


def source(name):
    return (CORE / (name + ".java")).read_text(encoding="utf-8")


class ReloadTransactionContract(unittest.TestCase):
    def test_ledger_cancellation_does_not_remove_the_inflight_barrier(self):
        code = source("ReloadPaymentLedger")
        begin = code.split("Runnable abort) {", 1)[1].split("private boolean current", 1)[0]
        self.assertLess(begin.index("starting.put(holder, attempt)"), begin.index("nativeStart.getAsBoolean()"))
        self.assertIn("!nativeStart.getAsBoolean() || !current(holder, attempt, valid)", begin)
        self.assertIn("!pay.getAsBoolean() || !current(holder, attempt, valid)", begin)
        end = code.split("void end(K holder)", 1)[1].split("W weapon(", 1)[0]
        self.assertIn("attempt.cancelled = true", end)
        self.assertNotIn("starting.remove", end)
        self.assertLess(begin.index("abort.run()"), begin.index("starting.remove(holder)"))

    def test_completion_claims_exact_cycle_before_external_callback(self):
        code = source("ReloadPaymentLedger").split("boolean complete(", 1)[1].split("void end(", 1)[0]
        self.assertIn("cycles.get(holder) != cycle", code)
        self.assertLess(code.index("cycles.remove(holder)"), code.index("commit.run()"))
        self.assertIn("return current(holder, attempt, valid)", code)
        self.assertIn("finally", code)

    def test_payment_captures_actual_sources_and_only_locks_inventory(self):
        code = source("GunReloadAuthority")
        self.assertIn("human.getData() == inventory", code)
        self.assertIn("actual != expected.actual()", code)
        self.assertIn("!ItemStack.matches(actual, expected.expected())", code)
        self.assertIn("HumanEquipmentExchange.locate(human, handle) == weapon", code)
        self.assertIn('EnumSet.of(ActionCoordinator.Resource.INVENTORY)', code)
        self.assertNotIn("Resource.MAIN_HAND", code)
        self.assertNotIn("Resource.OFF_HAND", code)
        self.assertIn("if (attempted) return paid && allowed(permitted)", code)
        pay = code.split("boolean pay(java.util.function.BooleanSupplier permitted)", 1)[1].split("static Capture capture", 1)[0]
        self.assertLess(pay.index("HumanAmmo.magazineCost"), pay.index("actions.acquire(token)"))
        self.assertLess(pay.index("actions.acquire(token)"), pay.index("inventory.setInventoryItem(slot, next)"))
        self.assertIn("sources.set(slot, new Source(next, next.copy()))", pay)
        self.assertIn("finally", pay)
        self.assertIn("actions.finish(token", pay)
        self.assertNotIn("HumanAmmo.consumeMagazines", code)

    def test_start_keeps_original_session_and_state_instead_of_adopting_reentry(self):
        code = source("GunReloadSession")
        begin = code.split("private static CompoundTag beginPaid", 1)[1].split("static boolean matches", 1)[0]
        self.assertLess(begin.index("GunReloadAuthority.capture"), begin.index("authority.pay(unchanged)"))
        self.assertIn("human.getPersistentData().get(key) == previousState", begin)
        self.assertIn('state.putLong("session", authority.session)', begin)
        self.assertIn("!authority.pay(unchanged) || !unchanged.getAsBoolean()", begin)
        self.assertIn("matches(human,activeGun,key,activeState)", begin)

    def test_complete_requires_same_finished_state_and_rechecks_identity(self):
        code = source("GunReloadSession")
        complete = code.split("static boolean complete", 1)[1].split("static void cancel", 1)[0]
        self.assertIn("!matches(human, gun, key, state)", complete)
        self.assertIn('state.getDouble("progress") < 1 - 1e-8', complete)
        self.assertIn('state.getInt("step") < steps.size()', complete)
        self.assertLess(complete.index("cancelState(human, gun, key, state)"), complete.index("commit.run()"))
        self.assertEqual(complete.count("GunReloadAuthority.validIdentity(human, gun, handle, session)"), 2)
        cancel = code.split("private static void cancelState", 1)[1].split("private static boolean transaction", 1)[0]
        self.assertIn("human.getPersistentData().get(key) != state", cancel)
        self.assertIn("CompoundTag expectedState", code)
        self.assertIn("attempt.state==expectedState", code)
        self.assertIn("if (matches(human, gun, key, state)) gun.getTag().remove(key)", cancel)

    def test_tacz_callbacks_validate_before_payment_and_guard_rollback(self):
        code = source("TaczHumanReloadTransactions")
        begin = code.split("public static boolean start", 1)[1].split("public static ReloadState tick", 1)[0]
        self.assertLess(begin.index("GunReloadAuthority.capture"), begin.index("PAYMENTS.begin"))
        self.assertIn("validStart,", begin)
        self.assertNotIn("authority.pay", begin)
        self.assertIn("HumanAmmo.canReload(human, weapon)", begin)
        tick = code.split("public static ReloadState tick", 1)[1].split("private static boolean validCycle", 1)[0]
        self.assertEqual(tick.count("clock.authority.payOnCompletion("), 2)
        self.assertIn("gun.setCurrentAmmoCount(weapon, previousRounds)", tick)
        self.assertIn("if(nativeInvoked[0] && validStart.getAsBoolean())", begin)
        self.assertIn("CLOCKS.put(data,clock)", begin)
        clock = code.split("private static final class Clock", 1)[1].split("private TaczHumanReloadTransactions", 1)[0]
        self.assertIn("session=authority.session", clock)
        self.assertNotIn("HumanBehaviorControl.actions", clock)
        self.assertIn("if (TICKING.contains(data)||!STARTING.add(data)) return false", begin)

    def test_native_steps_cannot_reenter_or_inherit_a_replaced_inventory(self):
        code = source("GunReloadSession")
        self.assertIn("cycle.state() == state && cycle.inventory().get() == human.getData()", code)
        tick = code.split("static void runTick", 1)[1].split("static double advance", 1)[0]
        self.assertLess(tick.index("TICKING.add(human)"), tick.index("advance(human, gun, key)"))
        self.assertIn("finally { TICKING.remove(human); }", tick)
        complete = code.split("static boolean complete", 1)[1].split("static void cancel", 1)[0]
        self.assertIn("human.getData() != inventory", complete)
        self.assertIn("human.getData() == inventory", complete)
        for backend in ("ScgunsIntegration", "JegIntegration"):
            steps = source(backend).split("private void tickReloadSteps", 1)[1].split("public void tickIdleReload", 1)[0]
            self.assertGreaterEqual(steps.count("GunReloadSession.matches(human,stack,RELOAD,state)"), 3)
            self.assertIn("GunReloadSession.complete(human,stack,RELOAD,state,", steps)
            self.assertIn("GunReloadSession.cancel(human,stack,RELOAD,state)", steps)

    def test_tacz_completion_and_server_work_have_one_authority(self):
        code = source("TaczHumanReloadTransactions")
        tick = code.split("public static ReloadState tick", 1)[1].split("public static boolean isPaidReloading", 1)[0]
        self.assertIn("PAYMENTS.complete(data,weapon", tick)
        self.assertIn("if(!validCycle(human,data,weapon,clock))", tick)
        self.assertIn("finally { TICKING.remove(data); }", tick)
        self.assertIn("TaczReloadVisualTimeline.work(human,0,rate)", code)
        self.assertIn("TaczReloadVisualTimeline.work(human,clock.work,rate)", code)


if __name__ == "__main__":
    unittest.main()
