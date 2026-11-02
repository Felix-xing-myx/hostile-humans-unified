"""Manual recovery ownership boundaries; runtime bytecode/lease checks complement these guards."""
import unittest
import re
from source_paths import SourcePath as Path

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "src/main/java/dev/felix/hostilehumans/behavior/combat/recovery"


def body(text, name):
    declaration = re.search(r"(?m)^    (?:public|private|protected|static)\s[^\n]*\b" + re.escape(name) + r"\(", text)
    if declaration is None:
        raise AssertionError("missing method " + name)
    start = text.index("{", declaration.start())
    depth = 1
    for end in range(start + 1, len(text)):
        depth += (text[end] == "{") - (text[end] == "}")
        if depth == 0:
            return text[start + 1:end]
    raise AssertionError("unterminated method " + name)


class ManualRecoveryContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.recovery = (SOURCE / "RecoverySupplies.java").read_text(encoding="utf-8")
        cls.food = (SOURCE / "OffhandFoodUse.java").read_text(encoding="utf-8")

    def test_manual_bypasses_selection_but_uses_current_real_offhand(self):
        begin = body(self.recovery, "beginManualOffhandUse")
        for required in ("HumanCombatAuthority.live(human)", "prepareManualSupply(human, manual.remainder)", "isRecoveryFood(human, actual)",
                         "actual.is(Items.POTION)", "beginHeldOffhandUse(human, actual, food, manual)"):
            self.assertIn(required, begin)
        for forbidden in ("findBest", "HumanEquipmentExchange", "CombatAiConfig", "shouldSuppressRecovery", "canUsePotionNow"):
            self.assertNotIn(forbidden, begin)

    def test_successful_food_rearms_but_cancel_rejection_and_drinks_remain_latched(self):
        begin = body(self.recovery, "beginManualOffhandUse")
        self.assertLess(begin.index("MANUAL_USES.put"), begin.index("HumanCombatAuthority.live"))
        self.assertIn("if (existing != null && !existing.repeatReady) return existing.session", begin)
        tick = body(self.recovery, "tickManualUse")
        self.assertIn("if (!held)", tick)
        self.assertIn("MANUAL_USES.remove(human)", tick)
        self.assertIn("if (pending == null || pending.repeatReady)", tick)
        cancel = body(self.recovery, "cancelManualUse")
        self.assertNotIn("MANUAL_USES.remove", cancel)
        self.assertNotIn("clear(", cancel)
        finish = body(self.recovery, "finishUse")
        self.assertIn("manual.session == session", finish)
        self.assertIn("manual.session = null", finish)
        self.assertIn("if (receipt && session.food())", finish)
        self.assertIn("manual.repeatReady = true", finish)
        self.assertIn("manual.repeatReady = false", cancel)

    def test_empty_hand_refill_uses_inventory_exchange_without_displacing_manual_items(self):
        refill = body(self.recovery, "prepareManualSupply")
        self.assertIn("held != previousRemainder || isRecoveryFood(human, held)", refill)
        self.assertIn("isRecoveryFood(human, candidate)", refill)
        self.assertIn("HumanEquipmentExchange.request", refill)
        self.assertIn("item -> item == candidate", refill)
        for forbidden in ("setItemInHand", "shrink(", "split(", "getMainHandItem"):
            self.assertNotIn(forbidden, refill)

    def test_shared_kernel_preserves_authority_and_atomic_native_completion(self):
        kernel = body(self.recovery, "beginHeldOffhandUse")
        for required in ("actualReferences(human, actual) != 1", "human.getOffhandItem() != actual", "actions.requestLease(",
                         "actions.acquire(token)", "ACTIVE_USES.put(human, session)", "manual.session = session",
                         "OffhandFoodUse.start(human, session)", "human.startUsingItem(InteractionHand.OFF_HAND)"):
            self.assertIn(required, kernel)
        self.assertIn("beginHeldOffhandUse(human, actual, parallelFood, null)", body(self.recovery, "beginInventoryUse"))
        self.assertIn("consumeCompletedPotion(human, hand, before)", body(self.recovery, "onUseCompleted"))

    def test_manual_tick_has_no_ai_duration_or_policy_gate(self):
        tick = body(self.recovery, "tickManualSession")
        for required in ("unavailable(human)", "HumanCombatAuthority.live(human)", "actions.running(session.token())",
                         "ItemStack.matches", "actualReferences", "actions.heartbeat", "if (advanceFood)"):
            self.assertIn(required, tick)
        for forbidden in ("expectedCompletionTick", "hasUrgentInstantHealing", "shouldSuppressRecovery", "CombatAiConfig", "clear("):
            self.assertNotIn(forbidden, tick)

    def test_food_queue_and_cancel_do_not_touch_potion_clock(self):
        pending = body(self.recovery, "pendingFoodHealing")
        self.assertIn("getFloat(PENDING_HEAL)", pending)
        self.assertNotIn("MedicalSupplies", pending)
        self.assertNotIn("put", pending)
        self.assertNotIn("REGENERATION_CLOCKS", body(self.recovery, "cancelManualUse"))

    def test_food_exec_rechecks_token_life_and_editor_before_finish(self):
        tick = body(self.food, "tick")
        for required in ("human.isRemoved()", "human.isNoAi()", "HumanInventorySession.isEditing(human)",
                         "running(state.session.token())", "state.lastTick == human.tickCount"):
            self.assertIn(required, tick)
        self.assertLess(tick.index("running(state.session.token())"), tick.index("stack.finishUsingItem"))


if __name__ == "__main__":
    unittest.main()
