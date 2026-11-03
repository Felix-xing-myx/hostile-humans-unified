"""Work ownership/knowledge contracts. Java and live scene acceptance remain separate."""
from source_paths import SourcePath as Path
import unittest
PKG=Path(__file__).resolve().parents[3]/"src/main/java/dev/felix/hostilehumans"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class PerceptionPriorityContract(unittest.TestCase):
    def test_geometry_precedes_fast_reservation_and_body_work_follows(self):
        code=source("HumanPerception")
        scan=code.split("if(now<s.nextScan)",1)[1].split("private static int resumeBodies",1)[0]
        grant = scan.index('PerceptionBudget.claim(h,"rays",rays)')
        self.assertNotIn("resumeBodies(h,s", scan[:scan.index("if(checks.isEmpty())")])
        self.assertIn("resumeBodies(h,s", scan[grant:])
        self.assertLess(scan.index("eligibleVisual(h,s"),scan.index('PerceptionBudget.claim(h,"rays",rays)'))
        self.assertLess(scan.index("if(checks.isEmpty())"),scan.index('PerceptionBudget.claim(h,"rays",rays)'))
        self.assertIn("rays=checks.size()",scan)
        self.assertIn("t!=null&&t.checked==now",code)
    def test_only_completed_negative_body_sweep_enters_backoff(self):
        code=source("HumanPerception")
        body=code.split("private static VisualProbe bodyVisibility",1)[1].split("private static BodyVisibilitySamples.Point",1)[0]
        self.assertEqual(body.count("VisualOcclusionMemory.negative"),1)
        self.assertLess(body.index("samples.isEmpty()"),body.index("sweep.negative"))
        self.assertIn("if(complete){",body)
        self.assertIn("VisualOcclusionMemory.negative(h,e,now,cacheMask,cacheSamples)",body)
        self.assertIn("VisualOcclusionMemory.visible(h,e)",code)
    def test_urgent_bypasses_wait_without_resetting_unfinished_sweep(self):
        cache=source("VisualOcclusionMemory")
        geometry=cache.split("private static boolean geometryChanged",1)[1].split("private static void trim",1)[0]
        self.assertNotIn("urgent",geometry)
        self.assertIn("urgent||now>=entry.retry",cache)
        for condition in ("hasChunkAt", "getBlockState", "occlusion_move_reset", "CachedOcclusionProof.covers", "entries.size()>cap"):
            self.assertIn(condition,cache)
    def test_natural_rotation_cannot_override_actual_action_or_stable_observation(self):
        attention=source("HumanAttention")
        self.assertIn("boolean accepted=",attention)
        self.assertIn("boolean action=accepted&&request.priority>=0",attention)
        self.assertIn("!glance||request.pressure||human.hhu$hasPendingMeleeAttack()",attention)
        self.assertIn("HumanTurnControl.apply(human,body,head,pitch,limit,profile,speed)",attention)
        self.assertIn("AttentionFacingPolicy.surveyStep",attention)
        self.assertIn("navigationYaw(human)",attention)
    def test_sound_glance_does_not_select_or_navigate(self):
        code=source("HumanPerception")
        glance=code.split("private static void reactToClue",1)[1].split("private static boolean finite",1)[0]
        self.assertIn("HumanThreatAssessment.maySoundGlance(h,clue.position,clue.priority)",glance)
        policy=source("HumanThreatAssessment")
        self.assertIn("priority < 3",policy)
        self.assertIn("weakCurrent(h,h.getTarget())",policy)
        self.assertIn("attention.sound_glance_ticks",glance)
        self.assertNotIn("setTarget",glance)
        self.assertNotIn("moveTo",glance)
    def test_every_normal_proposal_uses_same_player_crowd_gate(self):
        code=source("HumanTargetCoordinator")
        self.assertIn("!HumanPerception.selectionAllowed(h,proposal)",code)
        self.assertIn("HumanPerception.selectionAllowed(h,found)",code)
        self.assertIn("!role&&!HumanPerception.selectionAllowed(h,desired)",code)
        perception=source("HumanPerception")
        self.assertIn("TargetCap cached=state.targetCaps.get",perception)
        self.assertIn("if(!selectionAllowed(h,e))continue",perception)
