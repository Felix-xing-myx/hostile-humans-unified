"""Control ownership and bounded safe repositioning contracts, not live navigation tests."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / 'src/main/java/club/someoneice/humangunner'


class ShieldDefenseMovementTests(unittest.TestCase):
    def test_defensive_spacing_does_not_require_permission_to_attack(self):
        goal = (JAVA / 'AdaptiveCombatGoal.java').read_text(encoding='utf-8')
        decisions = goal.split('private boolean canCounterFromCurrentRange()', 1)[1].split('private boolean performShieldCounterattack()', 1)[0]
        self.assertIn('MeleeCombatRange.canReachTarget(human, threat)', decisions)
        self.assertNotIn('MeleeCombatRange.canStrike(', decisions)
        ranges = (JAVA / 'MeleeCombatRange.java').read_text(encoding='utf-8')
        geometry = ranges.split('public static boolean canReachTarget(', 1)[1].split('public static boolean canStartAttack(', 1)[0]
        self.assertNotIn('humanGunner$canMeleeAttack()', geometry)
        self.assertIn('GunSupport.isGunItem(', geometry)
        self.assertIn('human.humanGunner$canMeleeAttack() && canReachTarget(human, target)', ranges)

    def test_better_combat_reach_remains_available_while_blocking(self):
        source = (JAVA / 'BetterCombatMeleeCombat.java').read_text(encoding='utf-8')
        reach = source.split('public static double attackRange(', 1)[1].split('public static int cooldownTicks(', 1)[0]
        profile = source.split('public static boolean hasWeaponProfile(', 1)[1].split('/** Single-target geometry', 1)[0]
        self.assertIn('canReadWeaponProfile(human)', reach)
        self.assertIn('canReadWeaponProfile(human)', profile)
        self.assertNotIn('humanGunner$canMeleeAttack()', profile)
        execution = source.split('public static AttackPlan createAttackPlan(', 1)[1].split('/** Same selected weapon', 1)[0]
        self.assertIn('!isEnabled(human)', execution)

    def test_low_health_defense_preempts_water_terrain_and_pursuit(self):
        goal = (JAVA / 'AdaptiveCombatGoal.java').read_text(encoding='utf-8')
        defend = goal.split('private void tickDefend()', 1)[1].split('private void moveWhileDefendingRanged()', 1)[0]
        for control in ('WaterCombatNavigation.controlMelee', 'CombatTerrainNavigation.update', 'moveToThreat'):
            self.assertLess(defend.index('shieldMovement.tick('), defend.index(control))
        self.assertIn('shields.wantsHighPressureGuard()', defend)
        stop = goal.split('public void stop()', 1)[1]
        self.assertIn('shieldMovement.clear(human)', stop)

    def test_health_hysteresis_uses_actual_health_and_existing_rank_thresholds(self):
        source = (JAVA / 'ShieldDefenseMovement.java').read_text(encoding='utf-8')
        self.assertIn('human.getHealth() / Math.max(1.0F, human.getMaxHealth())', source)
        self.assertIn('health >= config.resumeHealthRatio()', source)
        self.assertIn('!disengaging && health > config.retreatHealthRatio()', source)
        self.assertIn('WaterCombatNavigation.clear(human)', source)
        water = (JAVA / 'WaterCombatNavigation.java').read_text(encoding='utf-8')
        clear = water.split('public static void clear(Human human)', 1)[1].split('/** Surface-only', 1)[0]
        self.assertLess(clear.index('release(human, state)'), clear.index('STATES.remove(human)'))
        self.assertNotIn('projectedHealth', source)

    def test_candidates_are_bounded_and_preserve_order_and_route_safety(self):
        source = (JAVA / 'ShieldDefenseMovement.java').read_text(encoding='utf-8')
        self.assertIn('Math.min(12, config.coverSearchAttempts())', source)
        self.assertIn('OptionalPathBudget.Kind.RETREAT, 2', source)
        self.assertIn('RetreatPathSafety.isSafe(human, threat, path, false)', source)
        self.assertIn('SoldierOrder.allowsCombatPath(human, path)', source)
        self.assertIn('route != null && !route.isDone() && remaining == 0', source)
        self.assertIn('human.tickCount - progressTick > 40', source)
        self.assertIn('if (SoldierOrder.isHoldingPosition(human))', source)

    def test_cover_checks_body_and_eye_and_never_falls_back_to_pursuit(self):
        source = (JAVA / 'ShieldDefenseMovement.java').read_text(encoding='utf-8')
        self.assertIn('human.getEyeHeight(), human.getBbHeight() * .5', source)
        self.assertIn('nextCoverCheck = human.tickCount + 8', source)
        self.assertIn('DefaultRandomPos.getPos(human, radius, 3)', source)
        self.assertIn('threat.distanceToSqr(candidate) < human.distanceToSqr(threat)', source)
        self.assertNotIn('moveToThreat', source)


if __name__ == '__main__':
    unittest.main()
