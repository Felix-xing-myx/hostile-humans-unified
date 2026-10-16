"""Window hints and identity handoff contracts, not gameplay or hit-rate proof."""
from pathlib import Path
import unittest,json
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/"src/main/java/club/someoneice/humangunner"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class AttackWindowContract(unittest.TestCase):
    def test_three_weapons_share_offensive_window_entry(self):
        for path in [PKG/"GunnerGoal.java",ROOT/"src/main/java/com/craftix/hostile_humans/entity/ai/goal/BowAttack.java",ROOT/"src/main/java/com/craftix/hostile_humans/entity/ai/goal/CrossbowGoal.java"]:
            self.assertIn("RangedFiringPosition.findAttackWindowPath(",path.read_text(encoding="utf-8"))
        wrapper=source("RangedFiringPosition").split("public static Path findAttackWindowPath",1)[1].split("public static Path findVisiblePath",1)[0]
        self.assertIn("HumanPerception.canTarget",wrapper)
        self.assertIn("Math.max(2,h.distanceTo(target)-2)",wrapper)
    def test_loaded_hints_have_bounded_inspection_and_exploration(self):
        code=source("RangedWindowCandidates")
        for guard in ("MAX_CANDIDATES=96","MAX_INSPECTIONS=192","hasChunkAt","noCollision","SoldierOrder.allowsCombatPosition","getCollisionShape","phase=Math.floorMod","seeds.inspectionLimit=112"):
            self.assertIn(guard,code)
        self.assertNotIn("createPath",code)
        self.assertNotIn("getChunk(",code)
    def test_budget_precedes_generation_and_pending_is_not_failure(self):
        code=source("RangedFiringPosition")
        self.assertLess(code.index("int budget = OptionalPathBudget.claim"),code.index("RangedWindowCandidates.collect"))
        self.assertIn("VISIBLE_WAITING.contains(human)",code)
        self.assertIn("bestPath == null && session.search().hasRemaining()",code)
        self.assertIn("candidateFeet.distanceToSqr(human.position()) < 0.25D",code)
        for guard in ("path.canReach()","HumanTerrainSafety.safePath","SoldierOrder.allowsCombatPath","var end=path.getEndNode()",
                      "HumanTerrainSafety.firingFeet(human,new BlockPos(end.x,end.y,end.z))"):
            self.assertIn(guard,code)
    def test_shot_uses_authorized_ballistics_without_eye_gate(self):
        code=source("RangedShotClearance")
        self.assertIn("!HumanPerception.canTarget(human,target)",code)
        self.assertNotIn("hasLineOfSight",code)
        self.assertIn("lane(human, target, feet, true)",code)
        self.assertIn("HumanPerception.canTarget(human,target) && lane",code)
        self.assertIn("clearArc",code)
    def test_reduced_batches_keep_full_sweep_semantics(self):
        root=json.loads((ROOT/"src/main/resources/defaults/hostile_humans_unified.json").read_text(encoding="utf-8"))
        self.assertEqual(root["perception"]["budgets"]["body_rays_per_target"],9)
        self.assertEqual(root["activity_budget"]["crowded_body_rays"],5)
        self.assertEqual(root["activity_budget"]["battle_body_rays"],3)
        self.assertIn("sweep.negative(samples.size(),start,count,sampleMask)",source("HumanPerception"))
        self.assertIn("CachedOcclusionProof.covers(entry.coveredMask,mask)",source("VisualOcclusionMemory"))
