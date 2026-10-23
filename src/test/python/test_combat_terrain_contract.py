"""Static integration checks only; Java/runtime terrain validation requires an authorized build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2] / "main/java"
COMBAT = ROOT / "club/someoneice/humangunner"
ENTITY = ROOT / "com/craftix/hostile_humans/entity"


def read(path):
    return path.read_text(encoding="utf-8")


class CombatTerrainContract(unittest.TestCase):
    def test_weapon_and_defense_controllers_consume_shared_decision(self):
        for path in [ENTITY / "ai/goal/BowMovementExecution.java", ENTITY / "ai/goal/CrossbowGoal.java",
                     COMBAT / "GunMovementExecution.java", COMBAT / "TridentHybridGoal.java",
                     COMBAT / "PursuitNavigation.java", COMBAT / "AdaptiveCombatGoal.java"]:
            with self.subTest(path=path.name):
                source = read(path)
                self.assertIn("CombatTerrainNavigation", source)
                self.assertIn(".ownsMovement()", source)

    def test_no_parallel_goal_or_unbounded_path_budget(self):
        source = read(COMBAT / "CombatTerrainNavigation.java")
        self.assertNotIn("extends Goal", source)
        self.assertIn("Kind.COMBAT_TERRAIN, 2", source)
        self.assertIn("if (budget == 0) return mark(human, Decision.PENDING)", source)
        self.assertIn("state.cursor < state.candidates.size()", source)
        self.assertIn("while (state.visited.size() >= 16)", source)

    def test_partial_path_uses_real_endpoint(self):
        source = read(COMBAT / "CombatTerrainNavigation.java").split("static boolean usefulPath", 1)[1].split("public static Decision update", 1)[0]
        self.assertIn("getEndNode()", source)
        self.assertNotIn("getTarget()", source)
        self.assertIn("if (path.canReach()) return true", source)
        self.assertNotIn("replacement.getTarget()", read(COMBAT / "PursuitNavigation.java"))

    def test_safety_feedback_only_on_execution(self):
        source = read(COMBAT / "HumanTerrainSafety.java")
        assessments = source.split("public static Assessment assessPath", 1)[1].split("public static Vec3 guardTravel", 1)[0]
        self.assertNotIn("CombatTerrainNavigation.blocked", assessments)
        self.assertIn("CombatTerrainNavigation.blocked", source.split("public static Vec3 guardTravel", 1)[1])
        nav = read(ENTITY / "ai/HumanNavigation.java")
        self.assertIn("Deque<BlockedEdge>", nav)
        self.assertIn("edge.from().equals(from) && edge.to().equals(to)", nav)
        self.assertNotIn("temporarilyBlockedNode", nav)

    def test_no_unreachable_melee_switch_or_required_orbit(self):
        source = read(COMBAT / "RangedTerrainCombat.java")
        self.assertIn("!CombatTerrainNavigation.canApproachForMelee(human, target)", source)
        self.assertNotIn("openOrbit", source)
        blocked = source.split("public static void strafe", 1)[1]
        self.assertNotIn("beginTerrainMelee", blocked)

    def test_orders_lifecycle_and_repair_yield(self):
        self.assertIn("CombatTerrainNavigation.validate(this)", read(ENTITY / "entities/Human.java"))
        self.assertIn("CombatTerrainNavigation.ownsRoute(human)", read(COMBAT / "MovementContinuity.java"))
        source = read(COMBAT / "CombatTerrainNavigation.java")
        self.assertIn("SoldierOrder.allowsCombatPosition(human, point)", source)
        self.assertIn("MovementContinuity.ownsRoute(human, state.approach)", source)
        self.assertIn("!human.isFleeing", source)
        self.assertIn("!human.isInWater()", source)

    def test_cover_and_multilevel_candidate_checks(self):
        source = read(COMBAT / "CombatTerrainNavigation.java")
        self.assertIn("target.getEyePosition(), eye", source)
        self.assertIn("human.getBbHeight() * .45", source)
        self.assertIn("ClipContext.Block.COLLIDER", source)
        self.assertIn("if (!loadedSightRegion(human, target.getEyePosition(), feet)) return false", source)
        self.assertIn("positions.size() == 3", source)
        self.assertIn("isOutsideBuildHeight", source)
        self.assertIn("hasChunkAt(occupied)", source)
        self.assertIn("noCollision(human, bounds)", source)

    def test_goal_restart_and_strafe_cleanup_preserve_terrain_route(self):
        melee = read(ENTITY / "ai/goal/MeleeAttackGoal.java")
        start = melee.split("public void start()", 1)[1].split("public void stop()", 1)[0]
        self.assertIn("CombatTerrainNavigation.hasWork", start)
        gun = read(COMBAT / "GunMovementExecution.java")
        cleanup = gun.split("void stopLateralMovement()", 1)[1].split("void clearLateralInput", 1)[0]
        self.assertNotIn("getNavigation().stop()", cleanup)
        self.assertIn("if (!terrainRoute) mob.getMoveControl().strafe", cleanup)
        trident_stop = read(COMBAT / "TridentHybridGoal.java").split("public void stop()", 1)[1].split("public boolean requiresUpdateEveryTick", 1)[0]
        self.assertIn("if (!CombatTerrainNavigation.ownsRoute(human))", trident_stop)

    def test_gunner_local_strafe_is_direct_and_keeps_facing(self):
        gun = read(COMBAT / "GunMovementExecution.java")
        local = gun.split("private void strafeAround(", 1)[1].split("private void clearDirectStrafe", 1)[0]
        self.assertIn("RangedTerrainCombat.strafeFacingTarget", local)
        self.assertNotIn("BasicNavigationBudget", local)
        self.assertNotIn("findLateralPath", local)
        self.assertNotIn("moveTo(", local)
        self.assertIn("mob.tickCount + 32", local)
        self.assertNotIn("mob.tickCount + 12", local)
        self.assertIn("clearDirectStrafe();", local)
        self.assertIn("HumanAttention.movement", local)
        self.assertIn("MovementContinuity.cancelStep", local)
        clear = gun.split("private void clearDirectStrafe()",1)[1].split("private double strafeLegDisplacementSqr",1)[0]
        self.assertIn("mob.getMoveControl().strafe(0.0F, 0.0F)",clear)
        source = read(COMBAT / "RangedTerrainCombat.java")
        guard = source.split("public static boolean strafeFacingTarget", 1)[1].split("private static boolean tryStrafe", 1)[0]
        for requirement in ("HumanPerception.canTarget", "HumanTargetCoordinator.suspended", "isFleeing",
                            "SoldierOrder.isHoldingPosition", "isSeekingShore", "isShoreTransitionPending", "getNavigation().isDone"):
            self.assertIn(requirement, guard)
        step = source.split("private static boolean tryStrafe", 1)[1]
        for requirement in ("SoldierOrder.allowsCombatPosition", "HumanTerrainSafety.safeStep", "noCollision",
                            "human.setYBodyRot(facingYaw)", "HumanAttention.movement", "clearRangedStrafeMotion"):
            self.assertIn(requirement, step)
        self.assertNotIn("HumanAttention.retreatAim", source)

    def test_equivalent_path_install_tracks_actual_navigation_object(self):
        self.assertIn("state.route = nav.getPath()", read(COMBAT / "PursuitNavigation.java"))
        self.assertIn("state.route = human.getNavigation().getPath()", read(COMBAT / "CombatTerrainNavigation.java"))
        accepted = read(COMBAT / "CombatTerrainNavigation.java").split("static void accepted", 1)[1].split("private static boolean visited", 1)[0]
        self.assertIn("Path path = human.getNavigation().getPath()", accepted)
        self.assertIn("if (!eligible(human, target)) return", accepted)


if __name__ == "__main__":
    unittest.main()
