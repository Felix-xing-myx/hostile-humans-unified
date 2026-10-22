"""Navigation ownership wiring only; bridge activation and runtime regressions remain separate gates."""
from pathlib import Path
import json
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/club/someoneice/humangunner'
NAV=ROOT/'src/main/java/com/craftix/hostile_humans/entity/ai'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class NavigationExecutionOwnershipContract(unittest.TestCase):
    def test_both_navigators_gate_real_installs_stop_ticks_and_native_recompute(self):
        for name in ['HumanNavigation','HumanWaterNavigation']:
            code=(NAV/(name+'.java')).read_text(encoding='utf-8')
            for interface in ['HumanNavigationOwnership.OwnedNavigation','mayStop(human, path)','runInstalled(human, path','recomputed(human, before, path)']:
                self.assertIn(interface,code)
            self.assertRegex(code,r'\.install\(human, \(\) -> this\.path')
    def test_continuity_repairs_inherit_installed_route_only(self):
        human=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        recovery=human.split('private void recoverStalledNavigation()',1)[1].split('public BlockPos investigateSound()',1)[0]
        self.assertIn('HumanNavigationOwnership.continueInstalled(this, this.navigation',recovery)
        self.assertIn('new com.craftix.hostile_humans.entity.ai.HumanWaterNavigation',human)
        state=source('HumanNavigationOwnership')
        self.assertIn('if (HumanGoalExecutionBridge.insideCallback(human) && !route.owner().equals(writeToken(human))) return false',state)
        self.assertIn('if (previous == null) CONTINUATION.remove()',state)
    def test_dynamic_flags_reconciled_after_actual_callback_and_before_selection(self):
        bridge=source('HumanGoalExecutionBridge')
        before=bridge.split('public static void beforeSelector(',1)[1].split('/** Invoked after',1)[0]
        self.assertIn('reconcileReleasedMove(execution, locks)',before)
        tick=bridge.split('public static void tickDelegate(',1)[1].split('public static void stopDelegate(',1)[0]
        self.assertLess(tick.index('scoped(execution, operation)'),tick.index('reconcileReleasedMove(execution,'))
        admission=bridge.split('public static ActionCoordinator.Token admitMovementWrite(',1)[1].split('/** For root-owned',1)[0]
        self.assertIn('execution.wrapped.getFlags().contains(Goal.Flag.MOVE)',admission)
        self.assertIn('owner.isRunning()',admission)
        self.assertIn('return delegate.getFlags()',source('TargetPreservingConditionalGoal'))
    def test_bridge_is_mandatory_and_binds_before_server_goal_execution(self):
        mixins=json.loads((ROOT/'src/main/resources/hostile_humans_unified.mixins.json').read_text(encoding='utf-8'))
        self.assertIn('HumanGoalSelectorExecutionMixin',mixins['mixins'])
        self.assertIn('HumanWrappedGoalExecutionMixin',mixins['mixins'])
        self.assertTrue(mixins['required'])
        control=source('HumanBehaviorControl').split('public static void beforeTick(',1)[1].split('public static void interruptMovement(',1)[0]
        self.assertIn('!HumanGoalExecutionBridge.bind(human)',control)
        self.assertIn('throw new IllegalStateException',control)
        self.assertIn('if (wrapped.isRunning()) wrapped.stop()',source('HumanGoalExecutionBridge'))
        human=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        tick=human.split('public void tick()',1)[1].split('public void',1)[0]
        self.assertLess(tick.index('HumanBehaviorControl.beforeTick(this)'),tick.index('super.tick()'))
        for method in ['setXxa', 'setZza']:
            self.assertIn('public void '+method+'(float input) { if (mayWriteMovementAxis(input))',human)
    def test_real_test_entry_contains_route_and_coordinator_lifecycle_checks(self):
        entry=(ROOT/'src/test/java/dev/felix/hostilehumans/core/RuntimePoliciesTest.java').read_text(encoding='utf-8')
        self.assertIn('ActionCoordinatorTest.main(args)',entry)
        self.assertIn('RouteExecutionLeaseTest.main(args)',entry)
