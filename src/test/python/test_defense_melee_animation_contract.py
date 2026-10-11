"""Prevent temporary defense from animating rejected/out-of-reach melee requests."""
from pathlib import Path
import unittest

JAVA = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = JAVA / 'club/someoneice/humangunner'


class DefenseMeleeAnimationContract(unittest.TestCase):
    def test_final_attack_gate_precedes_animation_and_plan(self):
        human = (JAVA / 'com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        attack = human.split('public boolean doHurtTarget(Entity entityIn)', 1)[1].split('private boolean hasPendingBetterCombatFacing', 1)[0]
        self.assertLess(attack.index('MeleeCombatRange.canStartAttack(this, target)'), attack.index('createAttackPlan'))
        self.assertLess(attack.index('MeleeCombatRange.canStartAttack(this, target)'), attack.index('swing('))
        reach = (PKG / 'MeleeCombatRange.java').read_text(encoding='utf-8')
        gate = reach.split('public static boolean canStartAttack(', 1)[1].split('/** Aim', 1)[0]
        self.assertIn('shieldDefense().shouldSuppressRecovery()', gate)
        self.assertIn('human.humanGunner$hasPendingMeleeAttack()', gate)
        self.assertIn('return canStrike(human, target)', gate)

    def test_defensive_callers_cannot_swing_before_validation(self):
        for name, method in [('GunnerGoal', 'private void tickCloseMeleeDefense'),
                             ('AdaptiveCombatGoal', 'private void performRetreatMeleeCounter'),
                             ('TridentHybridGoal', 'private void attackMeleeIfReady')]:
            code = (PKG / (name + '.java')).read_text(encoding='utf-8').split(method, 1)[1].split('\n    private ', 1)[0]
            self.assertNotIn('.swing(', code, name)
            self.assertLess(code.index('MeleeCombatRange.canStartAttack'), code.index('.doHurtTarget'), name)
