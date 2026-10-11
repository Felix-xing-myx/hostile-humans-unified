"""Shot/movement wiring and native origin contracts; not in-game trajectory tests."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / "src/main/java/club/someoneice/humangunner"
GOALS = ROOT / "src/main/java/com/craftix/hostile_humans/entity/ai/goal"


class RangedShotClearanceContract(unittest.TestCase):
    def test_blocked_shots_and_repositioning_share_geometry(self):
        for name in ("GunFireAuthorization", "HumanArrowCombat", "GunnerGoal", "AdaptiveCombatGoal"):
            self.assertIn("RangedShotClearance.canShoot(", (PKG / (name + ".java")).read_text(encoding="utf-8"))
        for name in ("BowAttack", "CrossbowGoal"):
            self.assertIn("RangedShotClearance.canShoot(", (GOALS / (name + ".java")).read_text(encoding="utf-8"))
        candidates = (PKG / "RangedFiringPosition.java").read_text(encoding="utf-8")
        self.assertEqual(candidates.count("RangedShotClearance.fromPosition("), 4)
        self.assertIn("OptionalPathBudget.claim", candidates)
        hybrid = (PKG / "RangedHybridMeleeGoal.java").read_text(encoding="utf-8")
        self.assertIn("RangedShotClearance.fromPosition(human, feet, target)", hybrid)
        self.assertNotIn("RangedFiringPosition.hasClearShot", hybrid)
        self.assertIn("RangedWeaponCustody.preferredForClearance(human)",
                      (PKG / "RangedShotClearance.java").read_text(encoding="utf-8"))

    def test_prediction_reuses_solver_without_mutating_combat_or_random(self):
        code = (PKG / "RangedShotClearance.java").read_text(encoding="utf-8")
        self.assertIn("HumanGunner.solveArrowBallistics(", code)
        self.assertIn("sample.tick() == human.tickCount", code)
        self.assertIn("sample.weapon() == weapon", code)
        self.assertIn("ClipContext.Block.COLLIDER", code)
        self.assertIn("tick < 60", code)
        self.assertIn("if (reached) next = position.add", code)
        for mutation in ("getRandom()", "consumeArrow", "stopUsingItem", "createPath", "getEntitiesOfClass"):
            self.assertNotIn(mutation, code)
        for name, origin in (("GunnerGoal", "mob"), ("AdaptiveCombatGoal", "human")):
            source = (PKG / (name + ".java")).read_text(encoding="utf-8")
            self.assertIn("RangedShotClearance.gunOrigin(" + origin + ")", source)
            self.assertIn("RangedShotClearance.gunAim(", source)

    def test_scguns_aim_adapter_matches_public_native_rule(self):
        jar = os.environ.get("SCGUNS_API_JAR")
        if not jar:
            self.skipTest("Scguns API jar not supplied")
        code = subprocess.check_output(["D:/Java/17/bin/javap.exe", "-classpath", jar, "-p", "-c",
                                        "top.ribs.scguns.entity.ai.AIGunEvent"], text=True)
        leading = code.split("public static net.minecraft.world.phys.Vec3 getLeadingDirection(", 1)[1]
        leading = leading.split("public static void performGunAttack", 1)[0]
        self.assertIn("double 0.8d", leading)
        # Native shot position interpolates previous/current shooter coordinates.
        shot = code.split("public static void performGunAttack", 1)[1].split("public static", 1)[0]
        for field in ("f_19790_", "f_19791_", "f_19792_"):
            self.assertIn(field, shot)
        self.assertIn("double 2.0d", shot)
        provider = (PKG / "ScgunsIntegration.java").read_text(encoding="utf-8")
        self.assertIn("target.getEyeHeight() * 0.8D", provider)
