"""Static handoff checks only; movement, shield timing and damage need game acceptance."""
from pathlib import Path
import unittest

PKG = Path(__file__).resolve().parents[3] / 'src/main/java/club/someoneice/humangunner'


class CloseGunTacticsContract(unittest.TestCase):
    def test_human_retreat_cannot_use_unbounded_fallback(self):
        goal = (PKG / 'GunnerGoal.java').read_text(encoding='utf-8')
        retreat = goal.split('private void retreatFrom(', 1)[1].split('private void moveForFiringPosition', 1)[0]
        human = retreat.split('} else {', 1)[0]
        self.assertIn('CloseGunTactics.maneuver(human, target)', human)
        self.assertNotIn('getPosAway', human)
        self.assertNotIn('tryLocalStep', human)

    def test_whole_route_budget_and_command_boundary(self):
        code = (PKG / 'CloseGunTactics.java').read_text(encoding='utf-8')
        self.assertIn('OptionalPathBudget.Kind.RETREAT, 2', code)
        self.assertIn('path.getNextNodeIndex(); i < path.getNodeCount()', code)
        self.assertIn('!SoldierOrder.allowsCombatPosition(human, point)', code)
        self.assertIn('RetreatPathSafety.isSafe(human, target, path, false)', code)
        self.assertIn('SoldierOrder.isHoldingPosition(human)', code)
        self.assertIn('human.isSeekingShore()', code)

    def test_switch_keeps_owned_weapon_and_native_animation(self):
        code = (PKG / 'CloseGunTactics.java').read_text(encoding='utf-8')
        self.assertIn('CloseDefenseHold.shouldHold(human)', code)
        self.assertIn('human.humanGunner$pendingMeleeTicks(target) >= 0', code)
        self.assertIn('shieldDefense().shouldSuppressRecovery()', code)
        self.assertIn('GunCustody.restorePrimaryGun(human, "close_gun_trial")', code)
        self.assertNotIn('setItemSlot', code)
        self.assertNotIn('stopUsingItem', code)
        custody = (PKG / 'GunCustody.java').read_text(encoding='utf-8')
        self.assertEqual(custody.count('CloseGunTactics.returnedToGun(human)'), 2)
        self.assertIn('if (isActive(human) && CloseGunTactics.keepDefense(human)) return false;', custody)

    def test_damage_uses_final_health_loss_and_valid_enemy(self):
        shield = (PKG / 'ShieldDefenseController.java').read_text(encoding='utf-8')
        damage = shield.split('public void recordPressureDamage(', 1)[1].split('public boolean shouldSuppressRecovery', 1)[0]
        self.assertLess(damage.index('!isValidThreat(attacker)'), damage.index('CloseGunTactics.damage'))
        human = (PKG.parents[2] / 'com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        self.assertIn('Math.max(0.0F, previousHealth - this.getHealth())', human)
        code = (PKG / 'CloseGunTactics.java').read_text(encoding='utf-8')
        self.assertIn('state.hits.size() >= 64', code)
        self.assertIn('state.retryAfter = state.defendUntil', code)
        self.assertIn('new WeakHashMap<>()', code)
