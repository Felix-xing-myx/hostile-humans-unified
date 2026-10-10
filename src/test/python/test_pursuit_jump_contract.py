"""Source integration checks, not compilation or in-game acceptance."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java"
PKG = JAVA / "club/someoneice/humangunner"
JUMP = (PKG / "PursuitJumpAttack.java").read_text(encoding="utf-8")
GOAL = (JAVA / "com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal.java").read_text(encoding="utf-8")
HUMAN = (JAVA / "com/craftix/hostile_humans/entity/entities/Human.java").read_text(encoding="utf-8")

class PursuitJumpContract(unittest.TestCase):
    def test_distance_and_time_share_weapon_budget(self):
        for condition in ("attackCooldown > 2", "12 - upswing", "closing * approachTicks",
                          "reach - 0.5D", "closing < 0.04D", "Math.abs(target.getY() - human.getY()) > 0.75D"):
            self.assertIn(condition, JUMP)
        self.assertIn("BetterCombatMeleeCombat.upswingTicks(human)", JUMP)
        self.assertIn("combatJumpChance()", JUMP)
        self.assertIn("combatJumpIntervalMin()", JUMP)

    def test_only_safe_intentional_pursuit(self):
        for condition in ("human.onGround()", "human.isFleeing", "human.isUsingItem()",
                          "SoldierOrder.isHoldingPosition", "HumanTerrainSafety.safeCombatJump",
                          "CombatTerrainNavigation.hasWork", "route.dot(direction) < 0.80D"):
            self.assertIn(condition, JUMP)
        movement = (PKG / "PlayerLikeMovementGoal.java").read_text(encoding="utf-8")
        self.assertIn("if (isRetreatingFrom(target) &&", movement)
        self.assertNotIn("if (movingWithCombatIntent && human.getRandom()", movement)

    def test_single_actual_impact_hook_and_cleanup(self):
        self.assertEqual(HUMAN.count("PursuitJumpAttack.strike(this,"), 1)
        self.assertIn("() -> super.doHurtTarget(entityIn)", HUMAN)
        self.assertIn('"Pursuit jump critical", 0.5D', JUMP)
        self.assertIn("AttributeModifier.Operation.MULTIPLY_TOTAL", JUMP)
        self.assertIn("finally { damage.removeModifier(CRITICAL); }", JUMP)
        self.assertIn("UnifiedConfig.get().vanillaMode()", JUMP)
        self.assertNotIn("fallDistance", JUMP)
        self.assertNotIn("getDeltaMovement().y", JUMP)

    def test_airborne_lifecycle_and_target_weapon_identity(self):
        for condition in ("state.airborne = true", "else if (state.airborne", "state.started > 3",
                          "state.started > 30", "state.target.equals(target.getUUID())",
                          "ItemStack.isSameItemSameTags(state.weapon", "active(human) && human.onGround()"):
            self.assertIn(condition, JUMP)
        self.assertIn("PursuitJumpAttack.awaitingTakeoff(human)", GOAL)
        self.assertIn("!club.someoneice.humangunner.PursuitJumpAttack.active(human)", GOAL)

if __name__ == "__main__": unittest.main()
