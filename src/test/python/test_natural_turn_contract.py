"""Integration ownership checks, not Java/game execution."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class NaturalTurnContract(unittest.TestCase):
    def test_attention_and_weapons_share_motor(self):
        self.assertIn('HumanTurnControl.apply(human,body,head,pitch,limit,profile,speed)',source('HumanAttention'))
        self.assertIn('HumanTurnControl.ballistic(human,aim.target.get()',source('HumanAttention'))
        self.assertIn('HumanTurnControl.action(human,yaw,pitch)',source('MeleeCombatRange'))
        self.assertIn('HumanTurnControl.ballistic(human,threat',source('AdaptiveCombatGoal'))
    def test_both_gun_paths_wait_for_real_alignment(self):
        self.assertIn('!HumanAttention.gunAligned(human)',source('GunnerGoal'))
        self.assertIn('!HumanTurnControl.aligned(human,exactYaw,exactPitch)',source('AdaptiveCombatGoal'))
        control=source('HumanTurnControl')
        self.assertIn('yaw-h.getYHeadRot()',control)
        self.assertIn('yaw-h.yBodyRot',control)
        self.assertIn('pitch-h.getXRot()',control)
    def test_lifecycle_and_no_extra_sensing(self):
        control=source('HumanTurnControl')
        for word in ('WeakHashMap','isNoAi','isSleeping','isEditing','dimension.equals','distanceToSqr(h.position())>16'):
            self.assertIn(word,control)
        for forbidden in ('getEntities(','.clip(','setTarget(','getNavigation('):self.assertNotIn(forbidden,control)
if __name__=='__main__':unittest.main()
