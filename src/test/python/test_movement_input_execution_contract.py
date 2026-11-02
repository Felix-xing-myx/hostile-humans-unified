"""Source wiring for delayed inputs and final steering safety; not physical/game simulation."""
from source_paths import SourcePath as Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/dev/felix/hostilehumans'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
HUMAN=(ROOT/'src/main/java/dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
class MovementInputExecutionContract(unittest.TestCase):
    def test_native_inputs_capture_separate_real_sources_before_writes(self):
        control=HUMAN.split('static class HumanMoveControl',1)[1]
        wanted=control.split('@Override public void setWantedPosition(',1)[1].split('@Override public void strafe',1)[0]
        self.assertLess(wanted.index('inputAuthority.request(human, WANTED'),wanted.index('super.setWantedPosition'))
        strafe=control.split('private void requestStrafe(',1)[1].split('private boolean clearCombatStrafe',1)[0]
        self.assertLess(strafe.index('inputAuthority.request(human, STRAFE'),strafe.index('super.strafe'))
        self.assertIn('inputAuthority.request(this, HumanMoveControl.WATER_HOLD',HUMAN)
        self.assertIn('inputAuthority.execute(human, channel',control)
    def test_travel_inherits_last_actual_control_execution(self):
        travel=HUMAN.split('public void travel(Vec3 input)',1)[1].split('private void travelWithMovementInput',1)[0]
        self.assertIn('control.appliedTick == this.tickCount',travel)
        self.assertIn('continueExecution(this, control.appliedSource',travel)
        self.assertIn('new Vec3(0, input.y, 0)',travel)
        control=HUMAN.split('static class HumanMoveControl',1)[1]
        self.assertIn('appliedSource = inputAuthority.source(channel)',control)
        self.assertIn('physicalBefore = human.getDeltaMovement()',control)
        self.assertIn('guardSteeringImpulse(',control)
    def test_cancel_and_late_cleanup_do_not_erase_external_motion(self):
        cleanup=HUMAN.split('public void hhu$cancelMovementExecution()',1)[1].split('/** A current melee',1)[0]
        self.assertNotIn('setDeltaMovement',cleanup)
        self.assertIn('cancelExecutionInputs()',cleanup)
        self.assertIn('human.hhu$cancelMovementExecution()',source('HumanBehaviorControl'))
        clear=HUMAN.split('public void clearRangedStrafeMotion()',1)[1].split('/** Root session',1)[0]
        self.assertIn('if (!control.clearCombatStrafe()) return',clear)
    def test_local_delayed_step_keeps_original_token(self):
        continuity=source('MovementContinuity')
        self.assertIn('state.stepSource = HumanNavigationOwnership.writeToken(human)',continuity)
        self.assertIn('continueExecution(human, state.stepSource',continuity)
        authority=source('HumanNavigationOwnership').split('public static boolean continueExecution(',1)[1].split('/** Read an existing',1)[0]
        self.assertIn('ownsBaseMove(human, token)',authority)
        self.assertIn('insideCallback(human) && !token.equals(writeToken(human))',authority)
    def test_final_world_checks_and_impulse_veto_preserve_physical_baseline(self):
        terrain=source('HumanTerrainSafety')
        impulse=terrain.split('public static Vec3 guardSteeringImpulse(',1)[1].split('private static Assessment assessSteering',1)[0]
        self.assertIn('new Vec3(before.x, after.y, before.z)',impulse)
        self.assertIn('SoldierOrder.allowsNavigationPosition(human, point)',terrain)
        self.assertIn('human.getBoundingBox().expandTowards(step)',terrain)
        self.assertIn('hasChunksAt(min, max)',terrain)
        self.assertIn('return assessStep(human, offset)',terrain)
        self.assertIn('super.travel(p_32394_)',HUMAN)
        self.assertIn('this.move(MoverType.SELF, motion)',HUMAN)
