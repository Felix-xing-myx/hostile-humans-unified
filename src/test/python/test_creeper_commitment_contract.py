"""Source boundaries for the current-target creeper exception; runtime math has Java tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


class CreeperCommitmentContract(unittest.TestCase):
    def test_current_target_server_and_reachable_path(self):
        code = (ROOT / 'main/java/club/someoneice/humangunner/CreeperMeleeCommitment.java').read_text(encoding='utf-8')
        for gate in ('human.level().isClientSide', 'human.getTarget() != creeper', '!human.canAttack(creeper)',
                     'path.canReach()', 'path.getNodeCount() - path.getNextNodeIndex() > 12',
                     'approach > pending', 'if (!reached) return -1', 'creeper.maxSwell - creeper.swell',
                     'HumanDamageScaling.outgoing', 'CombatRules.getDamageAfterAbsorb',
                     'creeper.getAbsorptionAmount()', 'MeleeAttackTiming.remainingCooldown'):
            self.assertIn(gate, code)
        for mutation in ('setTarget(', 'createPath(', '.hurt(', 'setHealth('):
            self.assertNotIn(mutation, code)

    def test_escape_keeps_existing_policy_and_rescue(self):
        code = (ROOT / 'main/java/com/craftix/hostile_humans/entity/ai/goal/AvoidCreeperGoal.java').read_text(encoding='utf-8')
        self.assertIn('CreeperSafetyPolicy.escape', code)
        self.assertIn('CreeperMeleeCommitment.canFinish(human, creeper)', code)
        self.assertIn('BodyguardProtection.protectingFromExplosion', code)
        self.assertIn('mob.setTarget(null)', code)

    def test_configured_fuse_and_boundary_tests_present(self):
        at = (ROOT / 'main/resources/META-INF/accesstransformer.cfg').read_text(encoding='utf-8')
        self.assertIn('Creeper f_32271_', at)
        tests = (ROOT / 'test/java/club/someoneice/humangunner/BodyguardAndVanillaModeTest.java').read_text(encoding='utf-8')
        self.assertGreaterEqual(tests.count('CreeperFinishPolicy.canFinish('), 9)


if __name__ == '__main__':
    unittest.main()
