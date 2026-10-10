"""Module ownership/phase contracts. No Minecraft execution or damage simulation."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


def source(name):
    return (PKG / name).read_text(encoding="utf-8")


class ModuleInterfaces(unittest.TestCase):
    def test_healing_effects_continue_under_menu_lock_without_equipment_writes(self):
        hook = source("HumanGunner.java").split("private static void onLivingTick", 1)[1]
        self.assertLess(hook.index("RecoverySupplies.tickEffects(human)"), hook.index("HumanInventorySession.isEditing(human)"))
        self.assertLess(hook.index("HumanInventorySession.isEditing(human)"), hook.index("RecoverySupplies.tickHandCustody(human)"))
        self.assertEqual(hook.count("RecoverySupplies.tickEffects(human)"), 1)
        effects = source("RecoverySupplies.java").split("static void tickEffects(Human human)", 1)[1].split("private static void consumeCompletedPotion", 1)[0]
        for name in ("tickRegenerationHealing", "FirstAidCompat.tick", "tickGradualHealing"):
            self.assertIn(name + "(human)", effects)
        for forbidden in ("tickHandCustody", "setItemInHand", "setInventoryItem", "restoreShield"):
            self.assertNotIn(forbidden, effects)

    def test_food_course_does_not_persist_transient_entity_clock(self):
        recovery = source("RecoverySupplies.java")
        timer = recovery.split("private static void tickGradualHealing", 1)[1].split("private static float foodHealingValue", 1)[0]
        self.assertNotIn("human.tickCount", timer)
        self.assertNotIn("getInt(NEXT_HEAL_TICK)", timer)
        self.assertIn("getInt(FOOD_HEAL_DELAY) - 1", timer)

    def test_potion_entity_hook_does_not_own_item_specific_healing(self):
        human = (ROOT / "src/main/java/com/craftix/hostile_humans/entity/entities/Human.java").read_text(encoding="utf-8")
        finish = human.split("protected void completeUsingItem()", 1)[1].split("public void tick()", 1)[0]
        self.assertLess(finish.index("super.completeUsingItem()"), finish.index("RecoverySupplies.onUseCompleted"))
        self.assertNotIn("heal(", finish)
        self.assertNotIn("isStrongInstantHealingPotion", finish)

    def test_weapon_classification_has_one_implementation(self):
        policy = source("CombatWeaponPolicy.java")
        self.assertIn("instanceof BowItem", policy)
        self.assertIn("instanceof CrossbowItem", policy)
        self.assertIn("SpartanEquipmentCompat.isSpartanRangedWeapon", policy)
        for name in ("AdaptiveCombatGoal.java", "ShieldDefenseController.java"):
            text = source(name)
            for method in ("isRangedThreat(target)", "usesTacticalRangedMovement(human)"):
                self.assertIn("CombatWeaponPolicy." + method, text)
            self.assertNotIn("instanceof ProjectileWeaponItem", text)
        custody = source("RangedWeaponCustody.java")
        self.assertIn("CombatWeaponPolicy.isBowOrCrossbow(stack)", custody)
        util = (ROOT / "src/main/java/com/craftix/hostile_humans/HumanUtil.java").read_text(encoding="utf-8")
        self.assertIn("CombatWeaponPolicy.isRangedWeapon(value)", util)

    def test_damage_scaling_has_one_phase_and_does_not_scale_bullets_as_melee(self):
        scaling = source("HumanDamageScaling.java")
        self.assertLess(scaling.index("source.is(TACZ_BULLET_DAMAGE)"), scaling.index("source.getDirectEntity() == attacker"))
        self.assertEqual(scaling.count("HumanGrowth.multiplier(attacker)"), 1)
        self.assertIn("UnifiedConfig.get().vanillaMode()", scaling)
        hook = source("HumanGunner.java")
        self.assertEqual(hook.count("HumanDamageScaling.outgoing("), 1)
        self.assertEqual(hook.count("HumanDamageScaling.incoming("), 1)
        self.assertNotIn("jump_attack_until", hook)
        self.assertNotIn("HumanGrowth.multiplier(", hook)

    def test_first_aid_cannot_grant_effect_for_increased_result_stack(self):
        compat = source("FirstAidCompat.java")
        result = compat.split("ItemStack after = event.getResultStack();", 1)[1]
        self.assertLess(result.index("after.getCount() > before.getCount()"), result.index("state.putInt"))
        self.assertIn("before.getItem() == after.getItem()", result)


if __name__ == "__main__":
    unittest.main()
