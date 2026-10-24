"""Continuation retention and shared transaction wiring, separate from Java/game acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
ARROWS = ROOT / 'src/main/java/club/someoneice/humangunner/HumanArrowCombat.java'

class VolleyContinuationContract(unittest.TestCase):
    def setUp(self):
        self.code = ARROWS.read_text(encoding='utf-8')
        self.tick = self.code.split('static void tickFollowup(', 1)[1].split('private static boolean followupIdentityValid', 1)[0]

    def test_transient_blocks_do_not_delete_pending_record(self):
        block = self.tick.split('if (RecoverySupplies.hasActiveUse', 1)[1].split('int[] next', 1)[0]
        self.assertIn('attempted(now, false)', block)
        self.assertNotIn('FOLLOWUPS.remove', block)
        self.assertIn('FOLLOWUPS.remove(human, shot)', self.tick)
        self.assertNotIn('FOLLOWUPS.remove(human);', self.tick)

    def test_success_and_callback_failure_settle_only_this_continuation(self):
        self.assertIn('shot.timing().attempted(now, inserted > 0)', self.tick)
        self.assertIn('if (!settled) shot.timing().cancel()', self.tick)
        self.assertIn('if (shot.timing().terminal()) FOLLOWUPS.remove(human, shot)', self.tick)
        self.assertLess(self.tick.index('int inserted = publishVolley'), self.tick.index('attempted(now, inserted > 0)'))

    def test_identity_and_defence_are_separate_checks(self):
        identity = self.code.split('private static boolean followupIdentityValid', 1)[1].split('static void clear', 1)[0]
        for condition in ('shotContextValid', 'target == human.getTarget()', '!human.isFleeing', 'HumanAmmo.hasArrows', 'actions.session() == session'):
            self.assertIn(condition, identity)
        for temporary in ('reservesRangedUse', 'HumanAttention.canAim', 'RecoverySupplies.hasActiveUse'):
            self.assertNotIn(temporary, identity)
        release = self.code.split('boolean followup) {\n        return shotContextValid', 1)[1].split('private static int executeVolley', 1)[0]
        for safety in ('HumanAttention.canAim', 'reservesRangedUse', 'RecoverySupplies.hasActiveUse', 'target == human.getTarget()'):
            self.assertIn(safety, release)

    def test_second_round_keeps_existing_paid_publication(self):
        self.assertIn('publishVolley(human, target, weapon', self.tick)
        self.assertIn('shot.projectiles().get(next[0]++).recreate', self.tick)
        self.assertNotIn('addFreshEntity', self.tick)
        self.assertNotIn('consumeArrow', self.tick)
        self.assertIn('if (FOLLOWUPS.containsKey(human)) return false', self.code)

    def test_deadline_is_constructed_once_not_renewed_by_pressure(self):
        self.assertEqual(self.code.count('new VolleyContinuation('), 1)
        self.assertIn('DOUBLE_ARROW_DELAY_TICKS = 4', self.code)
        self.assertIn('FOLLOWUP_MAXIMUM_WAIT_TICKS = 40', self.code)
        self.assertIn('FOLLOWUP_RETRY_TICKS = 2', self.code)
        self.assertIn('human.level().getGameTime() + DOUBLE_ARROW_DELAY_TICKS', self.code)

if __name__ == '__main__':
    unittest.main()
