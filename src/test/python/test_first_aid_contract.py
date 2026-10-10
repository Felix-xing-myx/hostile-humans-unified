"""First Aid integration source contracts; not Minecraft runtime tests."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"
SETTINGS = (PKG / "FirstAidSettings.java").read_text(encoding="utf-8")
COMPAT = (PKG / "FirstAidCompat.java").read_text(encoding="utf-8")
BRIDGE = (PKG / "FirstAidApiBridge.java").read_text(encoding="utf-8")
RECOVERY = (PKG / "RecoverySupplies.java").read_text(encoding="utf-8")
LOOT = (PKG / "HumanLootManager.java").read_text(encoding="utf-8")


class FirstAidContract(unittest.TestCase):
    def test_public_api_isolated_from_core_and_players(self):
        self.assertIn('isLoaded("firstaid")', COMPAT)
        self.assertNotIn("import ichttt.", COMPAT)
        self.assertIn("api.healing.ItemHealing", BRIDGE)
        self.assertIn("api.damagesystem.AbstractPartHealer", BRIDGE)
        for forbidden in ("getDeclaredField", "setAccessible", "AbstractPlayerDamageModel", "applyMorphine"):
            self.assertNotIn(forbidden, COMPAT + BRIDGE)
        self.assertIn("instanceof Human human", COMPAT)
        self.assertIn("human.level().isClientSide", COMPAT)
        self.assertIn('compileOnly files(firstAidApiJar)', (ROOT / "build.gradle").read_text(encoding="utf-8"))

    def test_forge_finish_retains_real_consumption_and_custody(self):
        self.assertIn("LivingEntityUseItemEvent.Finish", COMPAT)
        self.assertIn("RecoverySupplies.ownsSupplyUse(human, event.getItem())", COMPAT)
        self.assertIn("event.getResultStack()", COMPAT)
        self.assertIn("PotionRecoveryPolicy.needsPotionCharge", COMPAT)
        self.assertEqual(COMPAT.count("after.shrink(1)"), 1)
        self.assertIn("session.itemAtStart().getCount() == stack.getCount()", RECOVERY)
        self.assertIn("human.getUsedItemHand() == session.hand()", RECOVERY)

    def test_timing_uses_milliseconds_and_native_healer_cadence(self):
        self.assertIn("healing.getApplyTime(stack) / 50.0D", BRIDGE)
        self.assertIn("healer.maxHeal.getAsInt()", BRIDGE)
        self.assertIn("healer.ticksPerHeal.getAsInt()", BRIDGE)
        self.assertIn("(long) pulses * interval > Integer.MAX_VALUE", BRIDGE)
        self.assertIn("if (FirstAidCompat.isSupply(stack)) return;", RECOVERY)
        self.assertIn("FirstAidCompat.useDuration(stack)", RECOVERY)

    def test_shared_course_and_morphine_block_reuse(self):
        self.assertIn('if (isMedicine(stack)) return state.getInt("pulses") <= 0', COMPAT)
        self.assertIn("!human.hasEffect(MobEffects.DAMAGE_RESISTANCE)", COMPAT)
        self.assertIn('"morphine_duration_seconds", 90', SETTINGS)
        self.assertIn("LivingEntityUseItemEvent.Tick", COMPAT)
        self.assertIn("event.setCanceled(true)", COMPAT)
        self.assertIn("settings.morphineTicks(), settings.resistanceAmplifier()", COMPAT)

    def test_course_persists_and_ticks_even_without_food_pending(self):
        tick = RECOVERY.split("static void tickEffects(Human human)", 1)[1].split("private static void consumeCompletedPotion", 1)[0]
        self.assertLess(tick.index("FirstAidCompat.tick(human)"), tick.index("tickGradualHealing(human)"))
        self.assertIn("human.getPersistentData().put(STATE, state)", COMPAT)
        self.assertIn('state.getInt("next_pulse") - 1', COMPAT)
        self.assertNotIn("getGameTime", COMPAT)
        self.assertNotIn("getHealth() >=", COMPAT)
        self.assertIn("FirstAidCompat.pendingHealing(human)", RECOVERY)

    def test_pickup_and_ai_keep_medical_and_tactical_roles(self):
        self.assertIn("FirstAidCompat.isSupply(stack) || isSupportedSupplyPotion(stack)", RECOVERY)
        self.assertIn("FirstAidCompat.isMorphine(stack) && FirstAidCompat.canUse(human, stack)", RECOVERY)
        self.assertIn("FirstAidCompat.isMedicine(stack)", LOOT)
        self.assertIn("FirstAidCompat.inventoryValue(stack)", LOOT)
        self.assertIn(", 60, 0, 1000)", SETTINGS)
        self.assertIn(", 65, 0, 1000)", SETTINGS)
        self.assertIn(", 94, 0, 1000)", SETTINGS)


if __name__ == "__main__":
    unittest.main()
