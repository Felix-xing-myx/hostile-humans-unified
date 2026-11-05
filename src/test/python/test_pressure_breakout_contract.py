"""Actual pressure-guard lifecycle contracts; the former breakout/damage trial is retired.
These source guards do not execute Forge or the pure Java policy tests.
"""
import json
import unittest
from source_paths import SourcePath as Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/dev/felix/hostilehumans'
def source(name): return (PKG / (name + '.java')).read_text(encoding='utf-8')

class PressureBreakoutContract(unittest.TestCase):
    def test_current_defaults_backfill_missing_entries_without_overwriting_users(self):
        cfg = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))['shield_pressure']
        self.assertEqual(cfg['max_guard_ticks'], 120)
        self.assertEqual(cfg['cooldown_ticks'], 70)
        self.assertEqual(cfg['dps_smoothing_ticks'], 10)
        self.assertEqual(cfg['dps_ratio'], .22)
        for retired in ('window_ticks', 'damage_ratio', 'frequent_window_ticks',
                        'frequent_hits', 'frequent_damage_ratio', 'single_damage_ratio'):
            self.assertNotIn(retired, cfg)
        self.assertNotIn('break_attempt_ticks', cfg)
        storage = source('SplitConfigFiles')
        backfill = storage.split('JsonObject pressure = data.getAsJsonObject("shield_pressure")', 1)[1].split('if (rank &&', 1)[0]
        self.assertIn('if (template != null)', backfill)
        self.assertIn('for (var entry : template.entrySet())', backfill)
        self.assertIn('if (!pressure.has(entry.getKey()))', backfill)
        self.assertIn('entry.getValue().deepCopy()', backfill)
        self.assertNotIn('break_attempt_ticks', backfill)

    def test_actual_guard_starts_clock_and_end_starts_mandatory_cooldown(self):
        cycle = source('PressureDefenseCycle')
        guarded = cycle.split('void guarded(', 1)[1].split('void ended(', 1)[0]
        self.assertIn('!attempting() && guardSince == Integer.MIN_VALUE', guarded)
        self.assertIn('guardSince = now', guarded)
        ended = cycle.split('void ended(', 1)[1].split('/** Damage', 1)[0]
        self.assertIn('if (guardSince == Integer.MIN_VALUE) return', ended)
        self.assertIn('cooldownUntil = now + Math.min(config.cooldownTicks(), 10 + (duration + 1) / 2)', ended)
        self.assertIn('now - guardSince >= config.maxGuardTicks()', cycle)
        self.assertIn('now >= cooldownUntil', cycle)
        self.assertNotIn('attemptDamage', cycle)
        # Real damage cannot shorten or reset the mandatory end cooldown.
        self.assertEqual(cycle.count('cooldownUntil = now +'), 1)

    def test_pressure_has_own_owner_no_counter_and_waits_for_real_drink(self):
        shield = source('ShieldDefenseController')
        goal = source('PressureReactionGoal')
        for hook in ('allowsPressureReaction', 'beginPressureReaction', 'continuesPressureReaction', 'tickPressureReaction', 'endPressureReaction'):
            self.assertIn('shields.' + hook + '()', goal)
        tick = shield.split('void tickPressureReaction()', 1)[1].split('void endPressureReaction()', 1)[0]
        self.assertIn('owner != Owner.PRESSURE || !ownsCurrentShield()', tick)
        self.assertIn('pressureCycle.guarded(human.tickCount)', tick)
        self.assertNotIn('meleeBlocks', tick)
        self.assertNotIn('nextInt', tick)
        self.assertIn('RecoverySupplies.isDrinkingPotion(human)', shield)
        self.assertIn('pressureWaitingForDrink = true', shield)
        self.assertIn('pressureWaitingForDrink || pressureRequested(', shield)
        self.assertIn('!pressureCycle.attempting() && (owner == Owner.PRESSURE && ownsCurrentShield()', shield)
        record = shield.split('public void recordPressureDamage(', 1)[1].split('private void recordPressureDirection', 1)[0]
        self.assertLess(record.index('BYPASSES_SHIELD'), record.index('recoveryPressure.record('))
        self.assertIn('recordPressureDirection(source, damage, true)', record)
