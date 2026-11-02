"""Static handoff checks only; movement, shield timing and damage need game acceptance."""
from source_paths import SourcePath as Path
import unittest

PKG = Path(__file__).resolve().parents[3] / 'src/main/java/dev/felix/hostilehumans'


class CloseRangedTacticsContract(unittest.TestCase):
    def test_bow_trial_uses_shared_pressure_and_own_custody(self):
        code = (PKG / 'CloseRangedTactics.java').read_text(encoding='utf-8')
        trial = code.split('static boolean tryBow(', 1)[1].split('static boolean allowsLocalPath', 1)[0]
        for guard in ('mayTryRanged', 'isInWater()', 'isInLava()', 'isShoreTransitionPending()',
                      'onClimbable()', 'RangedTerrainCombat.holdsMelee', 'RangedShotClearance.canShoot'):
            self.assertIn(guard, trial)
        self.assertIn('RangedWeaponCustody.restorePreferred(human, "close_bow_trial")', trial)
        damage = code.split('static void damage(', 1)[1].split('static void maneuver(', 1)[0]
        self.assertIn('RangedWeaponCustody.isBowOrCrossbow', damage)
        self.assertIn('human.getTarget().getUUID().equals(state.target)', damage)
        common = code.split('private static boolean mayTryRanged(', 1)[1].split('static boolean tryGun', 1)[0]
        self.assertIn('human.canAttack(target)', common)
        self.assertIn('!human.isFleeing', common)
        self.assertIn('loading + wait + 20', code)

    def test_no_melee_backup_does_not_cancel_active_bow(self):
        code = (PKG / 'RangedWeaponCustody.java').read_text(encoding='utf-8')
        begin = code.split('static boolean beginCloseMelee(', 1)[1].split('static boolean restorePreferred(', 1)[0]
        self.assertLess(begin.index('if (melee.isEmpty())'), begin.index('human.stopUsingItem()'))
        self.assertIn('CloseRangedTactics.beginMelee(human)', begin)
        self.assertIn('MELEE_ENTER_DISTANCE_SQR = WeaponTransitionPolicy.BOW_ENTER * WeaponTransitionPolicy.BOW_ENTER', code)
        self.assertIn('HumanEquipmentExchange.request', begin)
        self.assertIn('HumanLootManager.bestOwnedMeleeWeapon(human)', begin)
        self.assertEqual(code.count('CloseRangedTactics.returnedToBow(human)'), 2)
        hybrid = (PKG / 'RangedHybridMeleeGoal.java').read_text(encoding='utf-8')
        self.assertIn('RangedWeaponCustody.restorePreferred(human, "close_defense_separated")', hybrid)
        self.assertIn('!WeaponTransitionPolicy.holdsBowMelee(human.distanceToSqr(target), true)', hybrid)
        self.assertIn('RetreatPathSafety.isSafe', hybrid)
        self.assertIn('SoldierOrder.allowsCombatPath', hybrid)
        self.assertIn('!CloseRangedTactics.keepDefense(human)', hybrid)

    def test_human_retreat_cannot_use_unbounded_fallback(self):
        goal = (PKG / 'GunnerGoal.java').read_text(encoding='utf-8')
        self.assertNotIn('tickCloseMeleeDefense(', goal)
        self.assertNotIn('getPosAway', goal)
        self.assertNotIn('doHurtTarget(', goal)
        self.assertIn('RangedTerrainCombat.strafeFacingTarget', (PKG / 'GunMovementExecution.java').read_text(encoding='utf-8'))

    def test_whole_route_budget_and_command_boundary(self):
        code = (PKG / 'CloseRangedTactics.java').read_text(encoding='utf-8')
        self.assertIn('OptionalPathBudget.Kind.RETREAT, 2', code)
        self.assertIn('path.getNextNodeIndex(); i < path.getNodeCount()', code)
        self.assertIn('!SoldierOrder.allowsCombatPath(human, path)', code)
        self.assertIn('RetreatPathSafety.isSafe(human, target, path, false)', code)
        self.assertIn('SoldierOrder.isHoldingPosition(human)', code)
        self.assertIn('human.isSeekingShore()', code)

    def test_switch_keeps_owned_weapon_and_native_animation(self):
        code = (PKG / 'CloseRangedTactics.java').read_text(encoding='utf-8')
        self.assertIn('CloseDefenseHold.shouldHold(human)', code)
        self.assertIn('human.hhu$pendingMeleeTicks(target) < 0', code)
        self.assertIn('shieldDefense().shouldSuppressRecovery()', code)
        self.assertIn('GunCustody.restorePrimaryGun(human,"close_gun_return")', code)
        self.assertNotIn('setItemSlot', code)
        self.assertNotIn('stopUsingItem', code)
        custody = (PKG / 'GunCustody.java').read_text(encoding='utf-8')
        self.assertIn('GunCombatProfile.shouldForceMelee(human,gun)', custody)
        self.assertIn('GunSupport.get().isReloading(human,gun)', custody)
        self.assertIn('HumanEquipmentExchange.request', custody)
        self.assertIn('AmmoWeaponSelection.mayRestore', custody)

    def test_damage_uses_final_health_loss_and_valid_enemy(self):
        shield = (PKG / 'ShieldDefenseController.java').read_text(encoding='utf-8')
        damage = shield.split('public void recordPressureDamage(', 1)[1].split('public boolean shouldSuppressRecovery', 1)[0]
        self.assertIn('if (attacker instanceof LivingEntity living && isValidThreat(living)) CloseRangedTactics.damage(human, damage);', damage)
        self.assertIn('recoveryPressure.record(human.tickCount, damage, human.getMaxHealth(), rules)', damage)
        human = (PKG.parents[2] / 'dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
        self.assertIn('Math.max(0.0F, previousHealth - this.getHealth())', human)
        code = (PKG / 'CloseRangedTactics.java').read_text(encoding='utf-8')
        self.assertIn('state.hits.size() >= 64', code)
        self.assertIn('state.retryAfter = state.defendUntil', code)
        self.assertIn('new WeakHashMap<>()', code)
