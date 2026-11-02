"""Confirmed-identity and role handoff contracts; arithmetic Java tests require compilation separately."""
from source_paths import SourcePath as Path
import unittest
PKG=Path(__file__).resolve().parents[3]/"src/main/java/dev/felix/hostilehumans"
def source(name):return (PKG/(name+".java")).read_text(encoding="utf-8")
class ThreatAssessmentContract(unittest.TestCase):
    def test_attributes_require_identity_and_remain_bounded(self):
        code=source("HumanThreatAssessment")
        self.assertLess(code.index("!HumanPerception.canTarget(h,target)"),code.index("target.getAttribute"))
        self.assertIn("attack==null?0:attack.getValue()",code)
        self.assertIn("RecentDamageTracker.recentDamageFrom(h,target,80)",code)
        self.assertIn('entries.size()>c().integer("memory.maximum_targets")',code)
        self.assertNotIn("setTarget",code)
    def test_private_pressure_lock_cannot_resurrect_released_lock(self):
        code=source("AdaptiveCombatGoal").split("private LivingEntity activeFocusedThreat()",1)[1].split("private void keepTarget",1)[0]
        self.assertIn("!HumanTargetCoordinator.lockActive(human,focusedThreat)",code)
        self.assertIn("clearFocusLock()",code)
        coordinator=source("HumanTargetCoordinator")
        self.assertIn("HumanThreatAssessment.nearOverride(h,locked,best)",coordinator)
        self.assertIn("HumanThreatAssessment.shouldSwitch(h,desired,best)",coordinator)
        self.assertIn("if(role){BodyguardProtection.update(h)",coordinator)
        self.assertIn("if(h.isFleeing&&current==null)return null",coordinator)
    def test_scoring_preserves_player_crowd_gate(self):
        candidate=source("HumanPerception").split("public static LivingEntity candidate",1)[1].split("private static boolean isSelfAttacker",1)[0]
        self.assertIn("HumanThreatAssessment.score(h,e)",candidate)
        self.assertIn("playerLimit(h,player)",candidate)

    def test_relay_keeps_original_authority_and_only_mints_from_personal_evidence(self):
        code=source("HumanTargetSharing")
        self.assertIn("lease.report.created(),lease.positionAt,lease.report.threat()",code)
        self.assertIn("report.created()+COOLDOWN",code)
        self.assertIn("now>=report.created()+LOCK_TICKS",code)
        self.assertIn("HumanPerception.personalDynamic(sender",code)
        self.assertNotIn("setTarget(",code)
        self.assertNotIn("getEntities(",code)
        self.assertIn("TargetReportLedger",code)
        finisher=source("HumanFinisherSelection")
        self.assertIn("HumanThreatAssessment.shouldSwitch(h,e,current)",finisher)
