"""Source integration contracts; terrain and teleport behavior require in-game tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java/club/someoneice/humangunner'


def read(name):
    return (JAVA / name).read_text(encoding='utf-8')


class SoldierStuckRecoveryTest(unittest.TestCase):
    def test_monitor_runs_outside_idle_goal(self):
        self.assertIn('SoldierStuckRecovery.tick(human)', read('HumanGunner.java'))
        source = read('SoldierStuckRecovery.java')
        self.assertNotIn('getTarget() == null', source)
        self.assertNotIn('setFlags', source)
        self.assertIn('state.origin.distanceToSqr(human.position())', source)
        self.assertNotIn('getNextNodeIndex', source)

    def test_jumping_and_recovery_items_defer_teleport_without_resetting_stall_timer(self):
        source = read('SoldierStuckRecovery.java')
        eligibility = source.split('clear(human);', 1)[0]
        self.assertNotIn('!human.onGround()', eligibility)
        self.assertNotIn('RecoverySupplies.hasHandCustody', eligibility)
        self.assertIn('if (!human.onGround() || RecoverySupplies.hasHandCustody(human)) return;', source)

    def test_budget_or_reachable_route_cannot_count_as_entrapment(self):
        source = read('SoldierStuckRecovery.java')
        budget = source.index('if (!BasicNavigationBudget.tryAcquire(human, 1)) return;')
        reachable = source.index('if (path != null && path.canReach())')
        failures = source.index('if (++state.failures < 2) return;')
        teleport = source.index('human.teleportTo(')
        self.assertLess(budget, reachable)
        self.assertLess(reachable, failures)
        self.assertLess(failures, teleport)
        self.assertNotIn('getNavigation().moveTo', source)

    def test_recovery_preserves_order_and_uses_checked_landing(self):
        source = read('SoldierStuckRecovery.java')
        self.assertIn('SafePositions.nearOwner', source)
        self.assertIn('SafePositions.nearPost', source)
        self.assertNotIn('SoldierOrder.set(', source)
        self.assertIn('human.forgetSoldierTarget()', source)
        self.assertIn('MovementContinuity.cancelStep(human)', source)
        self.assertIn('state.peaceUntil = human.tickCount + 40', source)
        self.assertIn('state.cooldownUntil = human.tickCount + 200', source)
        self.assertIn('SoldierStuckRecovery.recovering(h)', read('SoldierOrder.java'))
        eligibility = source[source.index('static boolean eligibleOrder'):source.index('static void tick')]
        self.assertIn('SoldierOrder.FOLLOW', eligibility)
        self.assertIn('SoldierOrder.BODYGUARD', eligibility)
        self.assertNotIn('SoldierOrder.GARRISON', eligibility)
        self.assertNotIn('SoldierOrder.GUARD', eligibility)
        safe = read('SafePositions.java')
        self.assertIn('return checkedPlacement(human, floor == null ? null', safe)
        for gate in ('level.hasChunk', 'level.noCollision(human, box)', 'RetreatPathSafety.isHazard', 'border.getMinX()'):
            self.assertIn(gate, safe)

    def test_patrol_destination_is_saved_only_after_an_accepted_order_route(self):
        source = read('SoldierOrder.java')
        self.assertLess(source.index('if(!RetreatPathSafety.tryMove(h,path,speed))return false;'),
                        source.index('h.getPersistentData().putLong(PATROL_DESTINATION,pos.asLong())'))
        self.assertIn('static BlockPos recoveryPost(Human h)', source)
        self.assertIn('h.getPersistentData().remove(PATROL_DESTINATION)', source)
        self.assertNotIn('PATROL_DESTINATION', read('PursuitNavigation.java'))


if __name__ == '__main__':
    unittest.main()
