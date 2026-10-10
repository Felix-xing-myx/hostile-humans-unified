"""Movement handoff contracts; actual combat/path geometry needs game acceptance."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3] / 'src/main/java'
PKG = ROOT / 'club/someoneice/humangunner'


class RangedMeleeContinuityContract(unittest.TestCase):
    def test_temporary_melee_reuses_native_authority(self):
        source = (PKG / 'RangedHybridMeleeGoal.java').read_text(encoding='utf-8')
        self.assertIn('extends MeleeAttackGoal', source)
        self.assertIn('super.tick();', source)
        self.assertIn('tickAttackOnly(target);', source)
        self.assertNotIn('doHurtTarget(', source)
        self.assertNotIn('attackCooldown', source)
        self.assertNotIn('getPosAway', source)
        native = (ROOT / 'com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal.java').read_text(encoding='utf-8')
        self.assertIn('this.tickAttackOnly(livingEntity);', native)
        self.assertIn('protected final void tickAttackOnly', native)

    def test_search_waits_keep_melee_and_search_session(self):
        source = (PKG / 'RangedHybridMeleeGoal.java').read_text(encoding='utf-8')
        self.assertIn('RangedFiringPosition.findVisiblePath', source)
        self.assertIn('!RangedFiringPosition.hasPendingVisibleSearch(human)', source)
        self.assertIn('!cycle.separating() && !cycle.searching()', source)
        tick = source.split('public void tick()', 1)[1].split('private boolean tickSeparation', 1)[0]
        self.assertLess(tick.index('RetreatPathSafety.tryMove'), tick.index('PursuitNavigation.clear'))

    def test_only_owned_routes_are_stopped(self):
        source = (PKG / 'RangedHybridMeleeGoal.java').read_text(encoding='utf-8')
        self.assertEqual(source.count('getNavigation().stop()'), 1)
        self.assertIn('if (MovementContinuity.ownsRoute(human, separationRoute)) human.getNavigation().stop()', source)
        self.assertIn('RetreatPathSafety.isSafe(human, target, path, true)', source)
        self.assertIn('SoldierOrder.allowsCombatPosition', source)
        self.assertIn('human.tickCount + 10', source)
        self.assertIn('if (ownsSeparationSearch) RangedFiringPosition.cancelVisibleSearch(human)', source)

    def test_restore_respects_existing_custody_and_attack_completion(self):
        source = (PKG / 'RangedHybridMeleeGoal.java').read_text(encoding='utf-8')
        self.assertIn('RangedWeaponCustody.restorePreferred(human, "close_defense_separated")', source)
        self.assertIn('if (!RangedWeaponCustody.isActive(human)) return;', source)
        self.assertIn('human.getSensing().hasLineOfSight(target)', source)
        self.assertIn('!human.humanGunner$hasPendingMeleeAttack() && !human.swinging', source)
