"""Static wiring checks; runtime cycle boundaries live in CombatPressurePolicyTest."""
import json
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
def source(name):
    return (PKG / (name + '.java')).read_text(encoding='utf-8')
class PressureBreakoutContract(unittest.TestCase):
    def test_global_times_exposed_without_overwriting_custom_values(self):
        cfg = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))['shield_pressure']
        self.assertEqual(cfg['max_guard_ticks'], 140)
        self.assertEqual(cfg['break_attempt_ticks'], 60)
        storage = source('SplitConfigFiles')
        self.assertIn('if (!pressure.has(key)) pressure.add(key, template.get(key).deepCopy());', storage)
        self.assertIn('List.of("max_guard_ticks", "break_attempt_ticks")', storage)
    def test_one_cycle_controls_recovery_attack_and_guard(self):
        shield = source('ShieldDefenseController')
        query = shield.split('public boolean shouldSuppressRecovery()', 1)[1].split('boolean wantsHighPressureGuard()', 1)[0]
        self.assertIn('!pressureCycle.attempting()', query)
        self.assertIn('recoveryPressure.clear()', query)
        intent = shield.split('private boolean wantsCombatNow(', 1)[1].split('boolean hasHeldMinimumCombatGuard()', 1)[0]
        self.assertLess(intent.index('isPressureBreakAttempt()'), intent.index('wantsHighPressureGuard()'))
        self.assertIn('phase == Phase.DEFEND && shields.isPressureBreakAttempt()', source('AdaptiveCombatGoal'))
        self.assertIn('if (ownsCurrentShield()) pressureCycle.guarded(human.tickCount);', shield)
        self.assertIn('isPressureBreakAttempt() ||', shield)
    def test_trial_counts_only_fresh_real_damage_and_uses_existing_thresholds(self):
        cycle = source('PressureDefenseCycle')
        self.assertIn('attemptDamage.clear()', cycle)
        self.assertIn('attemptDamage.record(now, damage, maximumHealth, config)', cycle)
        self.assertIn('if (attemptDamage.active(now, config)) clear()', cycle)
        self.assertNotIn('.blocked(', cycle)
        self.assertNotIn('getEntities', cycle)
        shield = source('ShieldDefenseController')
        record = shield.split('public void recordPressureDamage(', 1)[1].split('public boolean shouldSuppressRecovery()', 1)[0]
        self.assertLess(record.index('BYPASSES_SHIELD'), record.index('pressureCycle.damage('))
        self.assertLess(record.index('recoveryPressure.record('), record.index('pressureCycle.damage('))
