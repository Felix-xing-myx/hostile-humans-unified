"""Integration constraints for cliff detours; not world collision or gameplay tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = ROOT / 'club/someoneice/humangunner'
HUMAN = (ROOT / 'com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
WATER = (PKG / 'WaterCombatNavigation.java').read_text(encoding='utf-8')

class WaterCliffNavigationContract(unittest.TestCase):
    def test_stall_detour_reuses_budgeted_shore_planner_and_keeps_target(self):
        for call in ('OptionalPathBudget.claimFull', 'findNearestShorePath(state.avoid)',
                     'HumanTerrainSafety.safePath', 'SoldierOrder.allowsCombatPosition',
                     'human.startCombatShoreDetour', 'state.progress.isStalled'):
            self.assertIn(call, WATER)
        for forbidden in ('setTarget(', 'teleportTo(', 'new PathFinder', 'getEntitiesOfClass'):
            self.assertNotIn(forbidden, WATER)
        self.assertIn('state.nextSearch = human.tickCount + 40', WATER)
        self.assertIn('WaterCombatPolicy.closingProgress(state.targetDistance,', WATER)
        self.assertIn('horizontalDistance()', WATER)
        self.assertIn('maximumRange * maximumRange && human.getSensing().hasLineOfSight(target)', WATER)

    def test_owned_route_cannot_be_replaced_by_generic_pursuit_or_repair(self):
        self.assertIn('WaterCombatNavigation.tick(this)', HUMAN)
        self.assertIn('WaterCombatNavigation.update(this, target, maximumRange, speed)', HUMAN)
        repair = (PKG / 'MovementContinuity.java').read_text(encoding='utf-8')
        pursuit = (PKG / 'PursuitNavigation.java').read_text(encoding='utf-8')
        self.assertIn('WaterCombatNavigation.ownsRoute(human)', repair)
        self.assertIn('human.isSeekingShore() || human.isShoreTransitionPending()', pursuit)
        self.assertIn('if (ownsRoute(human)) human.stopSeekingShore()', WATER)
        swimming = HUMAN.split('private boolean computeShouldUseWaterMovement()', 1)[1].split('private boolean isNearWaterSurface()', 1)[0]
        self.assertEqual(swimming.count('!club.someoneice.humangunner.WaterCombatNavigation.ownsRoute(this)'), 2)
        self.assertIn('this.shoreCandidateNavigators.getOrDefault(path, this.shorePathUsesWaterNavigation)) return false', HUMAN)

    def test_cliff_checks_actual_surface_and_body_not_just_drop(self):
        safety = (PKG / 'HumanTerrainSafety.java').read_text(encoding='utf-8')
        for call in ('shoreExitReason(human, from, to)', 'canExitToDryHeight',
                     'level.noCollision(human, landing)', 'level.noCollision(human, crossing)',
                     'level.hasChunksAt', '++scanned >= 16'):
            self.assertIn(call, safety)
        self.assertLess(safety.index('shoreExitReason(human, from, to)'), safety.index('if (drop == 0'))
        nav = (ROOT / 'com/craftix/hostile_humans/entity/ai/HumanNavigation.java').read_text(encoding='utf-8')
        self.assertIn('avoidTransition(surface, node, untilTick)', nav)
        self.assertIn('getNextNodeIndex() - 1', nav)

if __name__ == '__main__':
    unittest.main()
