"""Read-only wiring guards; Java scenario tests and live-world movement are separate checks."""
from source_paths import SourcePath as Path
import unittest

JAVA = Path(__file__).resolve().parents[2] / "main/java"
CORE = JAVA / "dev/felix/hostilehumans/common"
COMBAT = JAVA / "dev/felix/hostilehumans"
ENTITY = JAVA / "dev/felix/hostilehumans/entity"


class NavigationHandoffContract(unittest.TestCase):
    def test_target_handoff_uses_the_authoritative_assignment(self):
        code = (ENTITY / "entities/Human.java").read_text(encoding="utf-8")
        setter = code.split("public void setTarget(", 1)[1].split("public boolean hhu$pickupCombatSettled", 1)[0]
        self.assertLess(setter.index("super.setTarget(livingEntity)"), setter.index("LivingEntity assigned = getTarget()"))
        self.assertIn("previous != assigned", setter)
        self.assertIn("SoldierPickupPolicy.interruptForCombat(this)", setter)
        self.assertIn("targetChanged(tickCount, previous != null, assigned != null)", setter)

    def test_idle_gap_and_start_race_share_existing_policy(self):
        policy = (COMBAT / "SoldierPickupPolicy.java").read_text(encoding="utf-8")
        goal = (COMBAT / "ValuableItemPickupGoal.java").read_text(encoding="utf-8")
        self.assertIn("!human.hhu$pickupCombatSettled()", policy)
        self.assertIn("pickup.interruptForCombat();", policy)
        self.assertIn("wrapped.stop();", policy)
        start = goal.split("public void start()", 1)[1].split("public void tick()", 1)[0]
        self.assertLess(start.index("!canContinueToUse()"), start.index("beginTarget()"))
        self.assertIn("MovementContinuity.ownsRoute(human, ownedPath)", goal)
        self.assertIn("if (ownedMovement)", goal)

    def test_crossing_is_local_and_excludes_sensitive_transitions(self):
        nav = (ENTITY / "ai/HumanNavigation.java").read_text(encoding="utf-8")
        method = nav.split("private void advancePassedCombatWaypoint", 1)[1].split("@Override protected PathFinder", 1)[0]
        for condition in ["path != sampledPath", "sampledTick != human.tickCount - 1",
                          "sampledNode != path.getNextNodeIndex()", "human.onGround() && !sampledAirborne",
                          "human.hurtTime > 0", "previous.y != node.y", "node.y != next.y",
                          "BlockTags.CLIMBABLE", "instanceof DoorBlock", "instanceof FenceGateBlock", "TrapDoorBlock"]:
            self.assertIn(condition, method)
        for guard in ["level.noCollision(human, swept)", "HumanTerrainSafety.safeStep(human, offset)",
                      "HumanTerrainSafety.safeTransition", "SoldierOrder.allowsCombatPosition"]:
            self.assertLess(method.index(guard), method.index("path.advance()"))
        self.assertEqual(method.count("path.advance()"), 1)
        for forbidden in ["createPath(", "teleport", "setPos(", "setDeltaMovement("]:
            self.assertNotIn(forbidden, method)

    def test_core_policies_do_not_load_world_or_persist(self):
        for name in ["PickupCombatWindow", "PassedWaypointPolicy"]:
            code = (CORE / (name + ".java")).read_text(encoding="utf-8")
            for forbidden in ["net.minecraft", "getPersistentData", "MinecraftServer"]:
                self.assertNotIn(forbidden, code)
