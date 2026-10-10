"""Source architecture guards; these do not replace live combat tests."""
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'
def source(name):
    return (PKG / (name + '.java')).read_text(encoding='utf-8')
class CombatEatingMovementContract(unittest.TestCase):
    def test_food_never_acquires_movement_or_weapon_use(self):
        self.assertFalse((PKG / 'CombatEatingMovementGoal.java').exists())
        action = source('OffhandFoodUse')
        for forbidden in ['getNavigation(', 'setTarget(', 'startUsingItem(', 'stopUsingItem(', 'setSprinting(', 'MOVEMENT_SPEED']:
            self.assertNotIn(forbidden, action)
        self.assertIn('human.getUsedItemHand() == InteractionHand.MAIN_HAND', action)
    def test_single_tick_and_finite_completion(self):
        action = source('OffhandFoodUse')
        for guard in ['state.lastTick == human.tickCount', 'state.ended', 'ItemStack.isSameItemSameTags', 'stack.getCount()', 'stack.finishUsingItem(human.level(), human)', 'ForgeEventFactory.onItemUseStart', 'ForgeEventFactory.onItemUseFinish', 'result.getCount() < before.getCount()']:
            self.assertIn(guard, action)
        self.assertLess(action.index('state.ended = true;', action.index('// Mark terminal')), action.index('stack.finishUsingItem'))
    def test_goals_wait_for_own_session_not_main_hand(self):
        for name in ['CombatFoodRecoveryGoal', 'IdleRecoveryGoal', 'CombatConsumableGoal']:
            self.assertIn('RecoverySupplies.isUseInProgress(human, session)', source(name))
        recovery = source('RecoverySupplies')
        for gate in ['ACTIVE_USES.containsKey(human)', 'OffhandFoodUse.tick(human)', 'OffhandFoodUse.completed(human, session)', 'pending.completed()', 'settleExchange(human, session)', 'boolean parallelFood = isRecoveryFood']:
            self.assertIn(gate, recovery)
        self.assertIn('hasUrgentInstantHealing(human)', source('CombatFoodRecoveryGoal'))
    def test_food_does_not_suspend_combat_or_add_stationary_hops(self):
        self.assertNotIn('if (RecoverySupplies.isEatingFood(human)) return false', source('AdaptiveCombatGoal'))
        movement = source('PlayerLikeMovementGoal')
        self.assertNotIn('if (!human.isFleeing && RecoverySupplies.isEatingFood(human)) return;', movement)
        self.assertIn('boolean movementHop = isRetreatingFrom(target)', movement)
        self.assertIn('movementHop && human.isSprinting()', movement)
        for name in ['GunCustody', 'RangedWeaponCustody']:
            self.assertNotIn('&& !RecoverySupplies.isEatingFood(human)', source(name))
    def test_server_visual_state_and_no_food_speed_penalty(self):
        entity = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/HumanMobEntityData.java').read_text(encoding='utf-8')
        self.assertIn('if (!level().isClientSide) this.entityData.set(DATA_OFFHAND_EATING, eating)', entity)
        defaults = (ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8')
        self.assertNotIn('food_use_speed_multiplier', defaults)
        self.assertNotIn('foodUseSpeedMultiplier', source('CombatAiConfig'))
