"""Movement authority contracts; actual surface collisions and combat remain game acceptance."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = ROOT / 'club/someoneice/humangunner'
HUMAN = (ROOT / 'com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')


class WaterCombatAuthorityContract(unittest.TestCase):
    def test_melee_and_guard_share_surface_controller(self):
        melee = (ROOT / 'com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal.java').read_text(encoding='utf-8')
        defense = (PKG / 'AdaptiveCombatGoal.java').read_text(encoding='utf-8')
        pursuit = (PKG / 'PursuitNavigation.java').read_text(encoding='utf-8')
        self.assertIn('WaterCombatNavigation.controlMelee(human, livingEntity, this.speedModifier)', melee)
        self.assertIn('this.tickAttackOnly(livingEntity);\n                return;', melee)
        self.assertIn('WaterCombatNavigation.controlMelee(human, threat, 1.0D)', defense)
        self.assertIn('human.hasWaterCombatMovementInput()', pursuit)
        self.assertIn('WaterCombatNavigation.surfaceCombat(this)', HUMAN)
        self.assertIn('this.setSwimming(false)', HUMAN)

    def test_actual_hitbox_stops_only_combat_movement(self):
        approach = HUMAN.split('public void approachCombatTargetInWater(', 1)[1].split('public boolean hasWaterCombatMovementInput()', 1)[0]
        self.assertIn('melee && !this.seekingShore && club.someoneice.humangunner.MeleeCombatRange.canStrike', approach)
        self.assertIn('this.getNavigation().stop()', approach)
        self.assertIn('this.waterCombatHoldingTick = this.tickCount', approach)
        self.assertIn('noCollision(this, this.getBoundingBox().expandTowards(toward))', approach)
        self.assertIn('SoldierOrder.allowsCombatPosition(this, this.position().add(toward))', approach)
        continuity = (PKG / 'MovementContinuity.java').read_text(encoding='utf-8')
        self.assertGreaterEqual(continuity.count('human.hasWaterCombatMovementInput()'), 2)

    def test_no_loot_request_can_steal_combat(self):
        loot = HUMAN.split('public void approachWaterLoot(', 1)[1].split('private void selectShoreNavigation()', 1)[0]
        self.assertIn('this.getTarget() != null || this.isFleeing', loot)
        target = HUMAN.split('public void setTarget(', 1)[1].split('public boolean humanGunner$shouldFastGunRetarget()', 1)[0]
        self.assertIn('this.directWaterLootSteeringTick = Integer.MIN_VALUE', target)
        self.assertIn('this.waterCombatHoldingTick = Integer.MIN_VALUE', target)
        pickup = (PKG / 'SoldierPickupPolicy.java').read_text(encoding='utf-8')
        self.assertIn('sinceCombat >= 0 && sinceCombat <= 60', pickup)

    def test_progress_and_facing_not_overwritten_by_waypoints(self):
        water = (PKG / 'WaterCombatNavigation.java').read_text(encoding='utf-8')
        self.assertIn('WaterCombatPolicy.closingProgress(state.targetDistance,', water)
        self.assertIn('WaterCombatPolicy.chasingMovingTarget(', water)
        self.assertLess(water.index('if (canFight)'), water.index('if (ownsRoute(human)) return true'))
        self.assertIn('if (this.human.hasShieldFacing()) this.human.applyShieldFacing()', HUMAN)
        self.assertIn('&& !this.human.hasShieldFacing())', HUMAN)
        self.assertNotIn('getEntitiesOfClass', water)


if __name__ == '__main__':
    unittest.main()
