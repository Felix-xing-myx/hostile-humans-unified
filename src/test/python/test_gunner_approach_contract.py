"""Movement handoff contracts; navigation and combat still need game acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"


class GunnerApproachContract(unittest.TestCase):
    def test_distant_target_retains_goal_and_hard_reach_is_checked_before_firing(self):
        code = (PKG / "GunnerGoal.java").read_text(encoding="utf-8")
        entry = code.split("public boolean canUse()", 1)[1].split("public boolean canContinueToUse", 1)[0]
        self.assertNotIn("firingRangeSqr(", entry)
        self.assertIn("target.level() == mob.level() && mob.canAttack(target)", entry)
        tick = code.split("public void tick()", 1)[1].split("private void tickCloseMeleeDefense", 1)[0]
        gate = tick.index("if (distance * distance > fireRangeSqr)")
        self.assertLess(gate, tick.index("setAiming(true)"))
        self.assertLess(gate, tick.index("operator.shoot("))
        self.assertIn("enablePursuitSprint(human, target)", tick[gate:])

    def test_terrain_cannot_hold_fire_outside_preferred_band(self):
        code = (PKG / "GunnerGoal.java").read_text(encoding="utf-8")
        movement = code.split("private void moveForFiringPosition", 1)[1].split("private void approachTarget", 1)[0]
        gate = movement.split("CombatTerrainNavigation.update(", 1)[1].split("if (terrain.ownsMovement())", 1)[0]
        self.assertIn("GunRangePolicy.withinPreferredRange(distance, range)", gate)
        self.assertIn("HumanAmmo.canUseGun", gate)
        self.assertIn("if (distance > range.maximum())", movement)
        self.assertIn("PursuitNavigation.update(pursuingHuman, target, 1.0D)", code)

    def test_existing_order_and_environment_leases_remain(self):
        code = (PKG / "GunnerGoal.java").read_text(encoding="utf-8")
        self.assertIn("SoldierOrder.isHoldingPosition(human)", code)
        self.assertIn("if (terrain.ownsMovement())", code)
        self.assertIn("if (isShoreSeeking())", code)
        pursuit = (PKG / "PursuitNavigation.java").read_text(encoding="utf-8")
        self.assertIn("BasicNavigationBudget.tryAcquire(human, 1)", pursuit)
        self.assertIn("HumanTerrainSafety.safePath(human, replacement)", pursuit)
