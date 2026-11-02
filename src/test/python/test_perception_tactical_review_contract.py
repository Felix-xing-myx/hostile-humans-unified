"""Source boundary checks; deliberately not a replacement for Java or gameplay validation."""
from source_paths import SourcePath as Path
import json
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/dev/felix/hostilehumans'

def source(name):
    return (PKG / (name + '.java')).read_text(encoding='utf-8')

class PerceptionTacticalContract(unittest.TestCase):
    def test_native_inputs_have_config_and_sound_exclusion(self):
        adapter = source('HumanSensoryEvents')
        self.assertIn('@Mod.EventBusSubscriber', adapter)
        self.assertIn('instanceof ServerLevel', adapter)
        self.assertIn('isSameThread()', adapter)
        self.assertIn('projectile.getOwner()', adapter)
        self.assertIn('HumanAwareness.sensory', adapter)
        kinds = set(re.findall(r'return "([a-z]+)"', adapter)) | {'bowshot', 'crossbowshot', 'reload'}
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        exclusion = (ROOT / 'src/main/java/dev/felix/hostilehumans/perception/SoundExclusionMemory.java').read_text(encoding='utf-8')
        for kind in kinds:
            self.assertIn(kind + '_radius', defaults['perception']['hearing'])
            self.assertIn('"' + kind + '"', exclusion)
        for forbidden in ('setTarget(', 'getEntities(', 'getNavigation('):
            self.assertNotIn(forbidden, adapter)

    def test_anonymous_inputs_are_bounded_and_do_not_bypass_known_source_filters(self):
        code = source('HumanAwareness')
        sensory = code.split('public static void sensory', 1)[1].split('private static void emit', 1)[0]
        self.assertIn('emit(actor,kind,point)', sensory)
        self.assertIn('recent.size()>512', sensory)
        self.assertIn('environment_cooldown_ticks', sensory)
        self.assertIn('passiveCreature(actor)', code)
        self.assertIn('p.isCreative()||p.isSpectator()', code)

    def test_finisher_is_optional_visible_and_releases_to_real_damage(self):
        code = source('HumanFinisherSelection')
        for guard in ('selectionAllowed', 'recentlySaw', 'clearOpportunity', 'decisive', 'hasLineOfSight', 'PerceptionBudget.claim', 'interruptedUntil'):
            self.assertIn(guard, code)
        self.assertLess(code.index('PerceptionBudget.claim'), code.index('h.hasLineOfSight'))
        self.assertIn('recentDamageFrom', code)
        self.assertIn('HumanFinisherSelection.damaged(human)', source('HumanDamageEvents'))
        coordinator = source('HumanTargetCoordinator')
        self.assertIn('HumanFinisherSelection.defensiveTarget(h)', coordinator)
        self.assertNotIn('setTarget(', code)

    def test_idle_survey_never_owns_movement_and_rechecks_after_moving(self):
        code = source('IdleSurvey')
        self.assertLess(code.index('PerceptionBudget.claim'), code.index('.clip('))
        self.assertIn('survey_min_clearance', code)
        self.assertIn('rear?165', code)
        self.assertIn('s.origin=eye;s.yaws=null;s.point=null;s.next=now+20', code)
        for forbidden in ('getNavigation(', 'setTarget(', 'setPos(', 'setYRot('):
            self.assertNotIn(forbidden, code)
        attention = source('HumanAttention')
        self.assertIn('route==null && !combat && !urgent && !action', attention)
        self.assertIn('request.priority<0&&route==null', attention)
        human = (ROOT / 'src/main/java/dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
        self.assertNotIn('new RandomLookAroundGoal', human)

    def test_report_budget_shortage_is_not_authority_and_ledger_is_after_link(self):
        code = source('HumanReportPropagation')
        self.assertLess(code.index('PerceptionBudget.claim'), code.index('.clip('))
        self.assertIn('return null', code)
        self.assertIn('cache.size()>64', code)
        self.assertIn('!dimension.equals(p.dimension)', code)
        sharing = source('HumanTargetSharing')
        broadcast = sharing.split('static void broadcast', 1)[1]
        self.assertLess(broadcast.index('HumanReportPropagation.link'), broadcast.index('admit(receiver,report,now)'))
        self.assertIn('lease.linkReady', sharing)
        self.assertIn('report.created()+LOCK_TICKS', sharing)
        self.assertIn('audible==null', sharing)

if __name__ == '__main__':
    unittest.main()
