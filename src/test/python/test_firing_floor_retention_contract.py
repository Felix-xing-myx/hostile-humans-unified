"""Cross-mode tactical position boundaries; voxel gameplay is a separate acceptance step."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'


class FiringFloorRetentionContract(unittest.TestCase):
    def test_all_ranged_modes_share_memory_before_general_terrain_planning(self):
        for path in (PKG / 'GunMovementExecution.java',
                     ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal/BowMovementExecution.java',
                     ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal/CrossbowMovementExecution.java'):
            source = path.read_text(encoding='utf-8')
            self.assertIn('RangedFiringMemory.observe(', source)
            self.assertLess(source.index('RangedFiringMemory.recover('),
                            source.index('CombatTerrainNavigation', source.index('RangedFiringMemory.observe(')))

    def test_optional_shooting_veto_is_not_an_obstacle_failure(self):
        source = (PKG / 'RangedTerrainCombat.java').read_text(encoding='utf-8')
        self.assertLess(source.index('keepsFiringFloor('), source.index('human.getMoveControl().strafe(choice'))
        self.assertIn('target != null && !retainedFloor', source)
        memory = (PKG / 'RangedFiringMemory.java').read_text(encoding='utf-8')
        for boundary in ('s.shotTick!=h.tickCount', '!h.onGround()', 'shouldSuppressRecovery()',
                         'isHoldingPosition(h)', 'HumanPerception.canTarget(h,t)', 'h.distanceToSqr(t)>CLOSE_DISTANCE_SQR'):
            self.assertIn(boundary, memory)

    def test_return_requires_current_ballistics_path_commands_and_bounded_memory(self):
        source = (PKG / 'RangedFiringMemory.java').read_text(encoding='utf-8')
        for boundary in ('p.canReach()', 'HumanTerrainSafety.safePath(h,p)', 'SoldierOrder.allowsCombatPath(h,p)',
                         'RangedShotClearance.fromPosition(h,feet,t)', 's.maximumRangeSqr',
                         's.failures>=2', 's.until', 'MAX_DISTANCE_SQR', 's.progressTick>=40'):
            self.assertIn(boundary, source)
        self.assertNotIn('setTarget(', source)
        self.assertNotIn('extends Goal', source)
        self.assertNotIn('getEntity(', source)

    def test_budget_wait_preserves_return_route(self):
        source = (PKG / 'RangedFiringMemory.java').read_text(encoding='utf-8')
        check = source.split('if(h.tickCount>=s.nextCheck)', 1)[1].split('s.nextCheck=h.tickCount+10', 1)[0]
        self.assertIn('s.nextCheck=h.tickCount+1;return true', check)
        self.assertNotIn('failed(h,s)', check)

    def test_selected_routes_bind_actual_navigation_and_need_grounded_arrival(self):
        source = (PKG / 'RangedFiringMemory.java').read_text(encoding='utf-8')
        selected = source.split('public static void selected(', 1)[1].split('private static void bind', 1)[0]
        self.assertIn('Path installed=h.getNavigation().getPath()', selected)
        self.assertIn('bind(h,s,installed,SELECTED_ROUTE_TICKS)', selected)
        observe = source.split('public static void observe(', 1)[1].split('static boolean arrivedAtWindow', 1)[0]
        self.assertIn('boolean travelling=', observe)
        self.assertIn('if(!travelling)', observe)
        self.assertIn('arrivedAtWindow(h.position(),s.feet,h.onGround())', observe)
        for file in ('GunMovementExecution.java',):
            self.assertIn('RangedFiringMemory.selected(', (PKG / file).read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
