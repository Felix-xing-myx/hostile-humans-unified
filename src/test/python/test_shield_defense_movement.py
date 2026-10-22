"""Final defense steering, resource ownership and retreat boundaries; not a game simulation."""
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

    def test_defense_facing_preserves_world_path_motion_and_only_real_step_jumps(self):
        # The old low-health shield-only retreat overlay is no longer ticked. Defense
        # keeps ordinary navigation; the final MoveControl converts it into body-local inputs.
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        move = human.split('private void moveWhileShieldFacing()', 1)[1].split('private void applyShorePopImpulse', 1)[0]
        self.assertIn('else human.applyShieldFacing()', move)
        self.assertIn('dz * Mth.cos(yaw) - dx * Mth.sin(yaw)', move)
        self.assertIn('dx * Mth.cos(yaw) + dz * Mth.sin(yaw)', move)
        self.assertIn('wantedY > human.getY() + human.getStepHeight()', move)
        self.assertIn('wantedY <= human.getY() + 1.1D && distance <= 1.5D', move)
        goal = (JAVA / 'AdaptiveCombatGoal.java').read_text(encoding='utf-8')
        self.assertNotIn('shieldMovement.tick(', goal)
        defend = goal.split('private void tickDefend()', 1)[1].split('private void moveWhileDefendingRanged()', 1)[0]
        self.assertIn('WaterCombatNavigation.controlMelee', defend)
        self.assertIn('CombatTerrainNavigation.update', defend)
        self.assertIn('SoldierOrder.isHoldingPosition', defend)
        self.assertIn('shieldMovement.clear(human)', goal.split('public void stop()', 1)[1])

    def test_pressure_does_not_take_navigation_or_require_an_attack_target(self):
        controller = (JAVA / 'ShieldDefenseController.java').read_text(encoding='utf-8')
        resources = controller.split('shieldResources()', 1)[1].split('public boolean ownsCombatDefense', 1)[0]
        self.assertNotIn('Resource.MOVE', resources)
        for resource in ('FACING', 'USE_ITEM', 'MAIN_HAND', 'OFF_HAND', 'ATTACK'):
            self.assertIn('Resource.' + resource, resources)
        goal = (JAVA / 'PressureReactionGoal.java').read_text(encoding='utf-8')
        self.assertIn('setFlags(EnumSet.noneOf(Flag.class))', goal)
        self.assertNotIn('getNavigation', goal)
        allows = controller.split('boolean allowsPressureReaction()', 1)[1].split('boolean beginPressureReaction()', 1)[0]
        self.assertNotIn('getTarget()', allows)
        self.assertNotIn('canAttack(', allows)
        self.assertNotIn('!human.isFleeing', allows)
        self.assertIn('!RecoverySupplies.hasActiveUse(human)', allows)

    def test_retreat_priority_survives_pressure_and_melee_guard(self):
        goal = (JAVA / 'AdaptiveCombatGoal.java').read_text(encoding='utf-8')
        use = goal.split('public boolean canUse()', 1)[1].split('private boolean shouldEnterRetreat', 1)[0]
        self.assertLess(use.index('shouldEnterRetreat(currentThreat)'), use.index('if (shields.wantsHighPressureGuard()) return false'))
        continuation = goal.split('public boolean canContinueToUse()', 1)[1].split('public void start()', 1)[0]
        self.assertIn('phase == Phase.DEFEND && shouldEnterRetreat(threat)', continuation)
        self.assertNotIn('if (shields.wantsHighPressureGuard()) return false', continuation)

    def test_independent_speed_limits_compose_once_in_shared_controller(self):
        speed = (JAVA / 'MovementSpeedController.java').read_text(encoding='utf-8')
        self.assertIn('limits.put(source, new Restriction(multiplier', speed)
        self.assertIn('for (Restriction limit : limits.values()) product *= limit.multiplier()', speed)
        self.assertIn('Operation.MULTIPLY_TOTAL', speed)
        controller = (JAVA / 'ShieldDefenseController.java').read_text(encoding='utf-8')
        self.assertIn('owner == Owner.ADVANCE ? .8D : config.shieldUseSpeedMultiplier()', controller)
        self.assertIn('owner == Owner.ADVANCE ? .85D : .7D', controller)
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        stop = human.split('public void stopUsingItem()', 1)[1].split('private void clearUseSpeedPenalty', 1)[0]
        self.assertIn('MovementSpeedController.Limit.DEFENSE, 1, false', stop)


if __name__ == '__main__':
    unittest.main()
