"""Ownership/lifecycle contracts; real motion assertions live in NaturalTurnResponseTest."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
CORE=ROOT/'src/main/java/dev/felix/hostilehumans/common'
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
class TurnGestureContract(unittest.TestCase):
    def test_sampling_is_private_to_the_turn_engine(self):
        gesture=(CORE/'TurnGesture.java').read_text(encoding='utf-8')
        motor=(CORE/'TurnResponse.java').read_text(encoding='utf-8')
        for forbidden in ('getRandom(', 'RandomSource', 'setTarget(', 'getNavigation(', 'getEntities('):
            self.assertNotIn(forbidden,gesture+motor)
        self.assertIn('TurnGesture.create(seed,++number',motor)
        self.assertIn('start=current',motor)
        self.assertIn('!gesture.urgent',motor)
    def test_no_intent_releases_instead_of_tracking_its_own_rotation(self):
        attention=(PKG/'HumanAttention.java').read_text(encoding='utf-8')
        self.assertIn('if(point==null&&route==null){HumanTurnControl.release(human)',attention)
        adapter=(PKG/'HumanTurnControl.java').read_text(encoding='utf-8')
        self.assertIn('state.frame.stop',adapter)
        self.assertIn('getMostSignificantBits()',adapter)
        self.assertIn('speed,intent)',adapter)
    def test_shooting_and_melee_keep_their_existing_authorities(self):
        turn=(PKG/'HumanTurnControl.java').read_text(encoding='utf-8')
        self.assertIn('yaw-h.getYHeadRot()))<=5',turn)
        self.assertIn('yaw-h.yBodyRot))<=5',turn)
        self.assertIn('pitch-h.getXRot())<=5',turn)
        native=(ROOT/'src/main/java/dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
        self.assertIn('super.doHurtTarget(entityIn)',native)
        bc=(PKG/'BetterCombatMeleeCombat.java').read_text(encoding='utf-8')
        self.assertIn('human.getViewVector(1.0F)',bc)
if __name__=='__main__':unittest.main()
