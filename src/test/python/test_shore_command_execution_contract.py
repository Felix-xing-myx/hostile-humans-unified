"""Source isolation for shore state and explicit movement handoff, not a live AI simulation."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/club/someoneice/humangunner'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
HUMAN=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
class ShoreCommandExecutionContract(unittest.TestCase):
    def test_shore_state_and_pending_search_have_distinct_original_sources(self):
        self.assertIn('SHORE, SHORE_SEARCH',source('HumanMovementInputAuthority'))
        fallback=HUMAN.split('private void beginSeekingShoreWithoutPath(',1)[1].split('public boolean continueSeekingShore(',1)[0]
        self.assertLess(fallback.index('requestShoreSource(HumanMoveControl.SHORE)'),fallback.index('this.seekingShore = true'))
        stop=HUMAN.split('public void stopSeekingShore()',1)[1].split('private void stopSeekingShoreState()',1)[0]
        self.assertIn('clearShoreSource(HumanMoveControl.SHORE_SEARCH)',stop)
        self.assertIn('clearShoreSource(HumanMoveControl.SHORE)',stop)
        self.assertIn('control.inputAuthority.execute(this, HumanMoveControl.SHORE, () -> this.getMoveControl().setWantedPosition(',HUMAN)
    def test_navigator_switch_checks_that_old_live_route_was_released(self):
        switch=HUMAN.split('private boolean switchNavigation(',1)[1].split('public boolean startSeekingShore(',1)[0]
        self.assertLess(switch.index('continueInstalled(this, old, old::stop)'),switch.index('this.navigation = desired'))
        self.assertIn('old.getPath() != null && !old.isDone()',switch)
    def test_move_interrupt_does_not_revoke_independent_behavior_session(self):
        control=source('HumanBehaviorControl').split('public static void interruptMovement(',1)[1].split('public static void cancel(',1)[0]
        for forbidden in ['cancelSession(', 'SESSIONS.remove(', 'closeAdmissions(', 'cancelBackgroundReloads(', 'RecoverySupplies.clear(']:
            self.assertNotIn(forbidden,control)
        self.assertIn('INTERRUPTING.add(human)',control)
        self.assertIn('HumanGoalExecutionBridge.interruptMovement(human, reason)',control)
        bridge=source('HumanGoalExecutionBridge').split('public static void interruptMovement(',1)[1].split('/** Token-specific',1)[0]
        self.assertIn('Goal.Flag.MOVE',bridge)
        self.assertIn('state.cancelling = true',bridge)
    def test_commands_use_explicit_handoff_and_offline_escape_preserves_route(self):
        for module in ['SoldierOrder','GarrisonSettings','OrderDistances','SoldierCombatMode','NaturalState','SoldierStuckRecovery']:
            self.assertIn('HumanBehaviorControl.interruptMovement(',source(module),module)
        self.assertIn('if (!preserveNavigation) HumanBehaviorControl.interruptMovement',source('SoldierOrder'))
        self.assertIn('GarrisonSettings.set(h,settings,true)',source('SoldierOrder'))
        self.assertIn('!preserveMovement && !settings.equals(previous)',source('GarrisonSettings'))
        self.assertIn('HumanBehaviorControl.cancel(human, "ownership dismissed")',source('HumanRelations'))
        self.assertIn('HumanBehaviorControl.cancel(human, "owner recall relocation")',source('HiredHumanRecall'))
    def test_test_run_and_expired_target_cleanup_keep_captured_identity(self):
        service=source('NavigationDebugService')
        self.assertIn('if (expected == null || !RUNS.remove(human, expected)) return',service)
        self.assertIn('activeRun != null && RUNS.get(human) == activeRun',service)
        self.assertIn('finish(human, ending, "interrupted")',service)
        perception=source('HumanPerception')
        self.assertLess(perception.index('HumanNavigationOwnership.captureRoute(h)'),perception.index('HumanTargetCoordinator.select(h,null,"原目标失效，记忆接替")'))
        capture=source('HumanNavigationOwnership').split('public static void stopCapturedRoute(',1)[1].split('/** A delayed local input',1)[0]
        self.assertIn('captured.navigation().getPath() != captured.path()',capture)
        self.assertIn('route.owner(), captured.owner()',capture)
