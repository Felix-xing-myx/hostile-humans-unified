"""Event/ownership boundaries; execution of Java arithmetic and game acceptance are separate."""
import json
from source_paths import SourcePath as Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/dev/felix/hostilehumans'

def source(name):
    return (PKG / (name + '.java')).read_text(encoding='utf-8')

class SpeedHearingAbilitiesContract(unittest.TestCase):
    def test_limit_excludes_only_itself_and_ui_uses_same_projection(self):
        controller = source('MovementSpeedController')
        self.assertIn('DefenseAttributes.projectRaw(speed, speed.getBaseValue(), CEILING_ID)', controller)
        self.assertIn('speed.removeModifier(CEILING_ID)', controller)
        self.assertNotIn('removeModifiers()', controller)
        self.assertIn('MovementSpeedController.projectedSpeed(human, live, baseline + actualBase - expected)', source('EffectiveAttributes'))
        self.assertIn('MovementSpeedController.refreshCeiling(human)', source('TierAttributes'))

    def test_real_arrow_launch_not_disk_load_or_trident_is_acoustic(self):
        events = source('HumanRuntime')
        launch = events.split('!event.loadedFromDisk() && event.getEntity() instanceof', 1)[1].split('// Internal launchers', 1)[0]
        self.assertIn('!(arrow instanceof ThrownTrident)', launch)
        self.assertIn('arrow.getOwner() instanceof LivingEntity', launch)
        self.assertIn('arrow.shotFromCrossbow()', launch)
        awareness = source('HumanAwareness')
        self.assertIn('hearing.projectile_shot_cooldown_ticks', awareness)
        self.assertNotIn('actor.hurtTime', awareness)
        self.assertIn('if(moved>8)', awareness)
        memory = (ROOT/'src/main/java/dev/felix/hostilehumans/perception/SoundExclusionMemory.java').read_text(encoding='utf-8')
        for kind in ('bowshot', 'crossbowshot'):
            self.assertIn('"'+kind+'"', memory)

    def test_unseen_sounds_never_read_attributes_or_assign_target(self):
        for name in ('HumanAwareness', 'HearingRelevance'):
            code = source(name)
            self.assertNotIn('setTarget(', code)
            self.assertNotIn('getAttribute(', code)
        threat = source('HumanThreatAssessment')
        weak = threat.split('private static boolean weakCurrent', 1)[1].split('static boolean maySoundGlance', 1)[0]
        self.assertLess(weak.index('HumanPerception.canTarget'), weak.index('intrinsic(h,target)'))
        self.assertIn('!HumanPerception.soundGlanceActive(human)', source('HumanAttention'))

    def test_ability_cancels_before_wear_and_never_owns_ai(self):
        self.assertIn('EventPriority.LOW, FactionAbilities::incoming', source('HumanRuntime'))
        entry = source('FactionAbilities')
        self.assertIn('LivingAttackEvent', entry)
        self.assertIn('event.setCanceled(true)', entry)
        self.assertIn('!HumanFactions.enabled()', entry)
        dodge = source('DodgeAbility')
        for excluded in ('IS_FALL', 'IS_EXPLOSION', 'BYPASSES_INVULNERABILITY'):
            self.assertIn(excluded, dodge)
        self.assertIn('HumanDamagePipeline.physical(source)', dodge)
        for excluded in ('IS_FIRE', 'IS_DROWNING', 'INDIRECT_MAGIC', 'WITHER'):
            self.assertIn(excluded, source('HumanDamagePipeline'))
        self.assertIn('ModSoundEvents.DODGE.get()', dodge)
        self.assertIn('SoundSource.HOSTILE, 1.0F, 1.0F', dodge)
        self.assertNotIn('PLAYER_ATTACK_NODAMAGE', dodge)
        for code in (entry, dodge):
            for forbidden in ('setTarget(', 'getNavigation(', 'setDeltaMovement(', 'setHealth(', 'hurtAndBreak('):
                self.assertNotIn(forbidden, code)

    def test_dodge_audio_variants_resolve_to_bundled_sounds_and_subtitles(self):
        assets = ROOT / 'src/main/resources/assets/hostile_humans_unified'
        event = json.loads((assets/'sounds.json').read_text(encoding='utf-8'))['ability.dodge']
        self.assertEqual([s.get('weight', 1) for s in event['sounds']], [1, 1])
        for sound in event['sounds']:
            namespace, name = sound['name'].split(':', 1)
            self.assertEqual(namespace, 'hostile_humans_unified')
            self.assertTrue((assets/'sounds'/(name+'.ogg')).read_bytes().startswith(b'OggS'))
        for language in ('zh_cn', 'en_us'):
            self.assertIn(event['subtitle'], json.loads((assets/'lang'/(language+'.json')).read_text(encoding='utf-8')))

    def test_default_assignment_and_chances(self):
        config = json.loads((ROOT/'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        abilities = config['faction_abilities']
        self.assertEqual(abilities['assignments'], {'hostile_humans_unified:ashen': ['dodge']})
        self.assertEqual(abilities['definitions']['dodge']['chance_by_tier'], {'roamer': 0, 'tier1': .05, 'tier2': .10, 'tier3': .20})
        self.assertEqual(config['perception']['hearing']['bowshot_radius'], 24)
        self.assertEqual(config['perception']['hearing']['crossbowshot_radius'], 28)

if __name__ == '__main__':
    unittest.main()
