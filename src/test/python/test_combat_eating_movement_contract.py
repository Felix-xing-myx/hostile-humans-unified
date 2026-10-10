"""Food movement authority and hand-custody integration guards (source only)."""
import unittest
from pathlib import Path
PKG = Path(__file__).resolve().parents[3] / 'src/main/java/club/someoneice/humangunner'
def source(name):
    return (PKG / (name + '.java')).read_text(encoding='utf-8')
class CombatEatingMovementContract(unittest.TestCase):
    def test_native_counterattack_and_food_custody(self):
        goal = source('CombatEatingMovementGoal')
        self.assertIn('extends MeleeAttackGoal', goal)
        self.assertIn('tickAttackOnly(target)', goal)
        self.assertNotIn('stopUsingItem', goal)
        for name in ['GunCustody', 'RangedWeaponCustody']:
            self.assertIn('human.isUsingItem() && !RecoverySupplies.isEatingFood(human)', source(name))
    def test_only_real_offhand_food_qualifies(self):
        body = source('RecoverySupplies').split('public static boolean isEatingFood', 1)[1].split('/**', 1)[0]
        for condition in ['session.food()', 'human.isUsingItem()', 'InteractionHand.OFF_HAND', 'UseAnim.EAT']:
            self.assertIn(condition, body)
    def test_single_movement_owner_and_emergency_priority(self):
        goal = source('CombatEatingMovementGoal')
        for condition in ['!human.isFleeing', '!human.isInWater()', 'SoldierOrder.isHoldingPosition']:
            self.assertIn(condition, goal)
        adaptive = source('AdaptiveCombatGoal')
        self.assertLess(adaptive.index('shouldEnterRetreat(currentThreat)'), adaptive.index('if (RecoverySupplies.isEatingFood(human)) return false'))
        self.assertIn('if (!human.isFleeing && RecoverySupplies.isEatingFood(human)) return;', source('PlayerLikeMovementGoal'))
    def test_routes_and_hops_share_existing_safety_and_budgets(self):
        goal = source('CombatEatingMovementGoal')
        for gate in ['BasicNavigationBudget.tryAcquire', 'RetreatPathSafety.isSafe', 'SoldierOrder.allowsCombatPosition', 'HumanTerrainSafety.safeCombatJump', '!human.humanGunner$hasPendingMeleeAttack()', 'config.combatJumpChance()', 'nextPathTick = human.tickCount + 12']:
            self.assertIn(gate, goal)
