"""Ordinary mobility uses modifier authority, never a replacement route or attack scheduler."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[3]
PKG=ROOT/'src/main/java/club/someoneice/humangunner'
def source(name):return (PKG/(name+'.java')).read_text(encoding='utf-8')
class MovementModifierExecutionContract(unittest.TestCase):
    def test_flag_free_helper_executes_under_modifier_authority(self):
        goal=source('PlayerLikeMovementGoal')
        self.assertIn('modifierToken = HumanMovementModifiers.execute(human, this::tickMovementModifier)',goal)
        self.assertIn('if (!owned) return',goal)
        module=source('HumanMovementModifiers')
        self.assertIn('EnumSet.of(ActionCoordinator.Resource.MOVE_MODIFIER), 20, true, 40',module)
        self.assertNotIn('Resource.BASE_MOVE',module)
        self.assertIn('baseMoveOwner(human)',module)
        self.assertIn('if (bound && base == null) return null',module)
        self.assertIn('actions.heartbeat(token)',module)
    def test_melee_jump_reuses_its_actual_separate_modifier(self):
        attack=source('PursuitJumpAttack')
        self.assertIn('HumanMovementModifiers.withToken(human, state.token, () -> human.getJumpControl().jump())',attack)
        self.assertIn('ActionCoordinator.Resource.MOVE_MODIFIER), 50, true, 35',attack)
        self.assertIn('actions.owns(token, ActionCoordinator.Resource.MOVE_MODIFIER)',source('HumanMovementModifiers'))
    def test_pending_jump_revalidates_original_sources_before_consumption(self):
        jump=source('HumanSafeJumpControl')
        request=jump.split('public void jump()',1)[1].split('public void cancelPending()',1)[0]
        self.assertNotIn('super.jump()',request)
        consume=jump.split('public void tick()',1)[1].split('private boolean available()',1)[0]
        for guard in ['ownsBaseMove(human, base)', 'modifierValid(human, modifier)', 'safeCombatJump(human)', 'cancelPending()']:
            self.assertIn(guard,consume)
        self.assertLess(consume.index('modifierValid(human, modifier)'),consume.index('super.jump()'))
    def test_cancel_retires_modifiers_and_pending_jump_without_erasing_physics(self):
        root=source('HumanBehaviorControl')
        self.assertIn('HumanMovementModifiers.cancel(human, reason)',root)
        human=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        self.assertIn('HumanSafeJumpControl control) control.cancelPending()',human)
        cleanup=human.split('public void humanGunner$cancelMovementExecution()',1)[1].split('/** A current melee',1)[0]
        self.assertNotIn('setDeltaMovement',cleanup)
    def test_navigation_speed_changes_require_real_movement_source(self):
        for name in ['HumanNavigation','HumanWaterNavigation']:
            code=(ROOT/'src/main/java/com/craftix/hostile_humans/entity/ai'/(name+'.java')).read_text(encoding='utf-8')
            method=code.split('public void setSpeedModifier(',1)[1].split('@Override public void tick()',1)[0]
            self.assertLess(method.index('writeToken(human) == null'),method.index('super.setSpeedModifier(speed)'))
