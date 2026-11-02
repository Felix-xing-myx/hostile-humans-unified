"""Recovery effect replacement boundaries; no transformed game runtime is started."""
import json,unittest
from source_paths import SourcePath as Path
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class RecoveryRebalance(unittest.TestCase):
 def test_replaces_native_pulse_for_humans_only(self):
  s=source('HumanRecoveryEffects')
  self.assertIn('target instanceof Human',s);self.assertIn('!target.level().isClientSide',s)
  self.assertIn('if (effect == MobEffects.REGENERATION) return true',s)
  self.assertIn('target.isInvertedHealAndHarm()',s)
  mixin=(PKG/'mixin/HumanRecoveryEffectMixin.java').read_text(encoding='utf-8')
  self.assertEqual(mixin.count('callback.cancel()'),2);self.assertIn('m_6742_',mixin);self.assertIn('m_19461_',mixin)
  registry=json.loads((ROOT/'src/main/resources/hostile_humans_unified.mixins.json').read_text(encoding='utf-8'))
  self.assertIn('HumanRecoveryEffectMixin',registry['mixins'])
 def test_no_extra_instant_heal_after_native_completion(self):
  s=source('RecoverySupplies');chunk=s.split('public static void onUseCompleted',1)[1].split('static void tickEffects',1)[0]
  self.assertNotIn('human.heal(',chunk);self.assertIn('consumeCompletedPotion',chunk)
 def test_regen_clock_independent_of_food_configuration(self):
  s=source('RecoverySupplies').split('private static void tickRegenerationHealing',1)[1].split('private static boolean canUsePotionNow',1)[0]
  self.assertIn('PotionRecoveryPolicy.REGENERATION_INTERVAL_TICKS',s);self.assertNotIn('gradualHealIntervalTicks',s)
  data=json.loads((ROOT/'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
  self.assertEqual(data['ai']['gradual_heal_interval_ticks'],15)
  self.assertIn('                15,\n                2.0F,',source('CombatAiConfig'))
if __name__=='__main__':unittest.main()
