"""Cross-module source contracts; these do not execute Minecraft or TaCZ."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/club/someoneice/humangunner'

def source(name):
    return (PKG / name).read_text(encoding='utf-8')

class SuppressiveShieldReloadContract(unittest.TestCase):
    def test_ranged_goals_respect_shield_lease_before_touching_use_state(self):
        goals = ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal'
        for name in ('CrossbowGoal', 'BowAttack'):
            shot = (goals / (name + '.java')).read_text(encoding='utf-8').split('private void tickShot(', 1)[1]
            self.assertLess(shot.index('reservesRangedUse()'), shot.index('startUsingItem('))
        crossbow = (goals / 'CrossbowGoal.java').read_text(encoding='utf-8').split('private void tickShot(', 1)[1]
        self.assertLess(crossbow.index('!(mob.getUseItem().getItem() instanceof CrossbowItem)'), crossbow.index('crossbowState = CrossbowState.CHARGED'))
        self.assertIn('CrossbowItem.isCharged(heldCrossbow)', crossbow)
        self.assertLess(crossbow.index('this.mob.getUseItem() != heldCrossbow'), crossbow.index('this.crossbowState = CrossbowState.CHARGING'))
        shoot = source('HumanArrowCombat.java').split('public static boolean canShoot(', 1)[1].split('/** Read the projectile', 1)[0]
        self.assertIn('!human.humanGunner$shieldDefense().reservesRangedUse()', shoot)
        self.assertIn('SpartanEquipmentCompat.isShield(human.getUseItem())', shoot)

    def test_short_projectile_guard_survives_block_activation_delay(self):
        shield = source('ShieldDefenseController.java')
        begin = shield.split('int beginCombat(', 1)[1].split('void endCombat()', 1)[0]
        self.assertIn('CombatPressurePolicy.MINIMUM_EFFECTIVE_BLOCK_TICKS + 1', begin)
        self.assertIn('human.setChargingCrossbow(false)', begin)
        tick = shield.split('CombatUpdate tickCombat(', 1)[1].split('private void updateDefensePressure()', 1)[0]
        self.assertIn('shouldDeferRangedProjectileGuard() && hasHeldMinimumCombatGuard()', tick)

    def test_pressure_handoff_cancels_damage_and_animation_before_weapon_swap(self):
        shield = source('ShieldDefenseController.java')
        prepare = shield.split('void prepareHighPressureDefense()', 1)[1].split('private boolean wantsCombatNow', 1)[0]
        self.assertLess(prepare.index('humanGunner$cancelMeleeAttackForDefense()'), prepare.index('GunCustody.beginCloseMeleeCounter'))
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        cancel = human.split('public void humanGunner$cancelMeleeAttackForDefense()', 1)[1].split('private void wearEquippedItem', 1)[0]
        for expected in ('pendingBetterCombatTarget = null', 'pendingBetterCombatUpswingTicks = 0',
                         'setNpcBetterCombatAttackAnimation("")', 'swinging = false'):
            self.assertIn(expected, cancel)
        self.assertIn('!humanGunner$shieldDefense().blocksMeleeAttack()', human)

    def test_old_melee_goal_cannot_lower_emergency_shield_to_attack(self):
        goal = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal/MeleeAttackGoal.java').read_text(encoding='utf-8')
        attack = goal.split('protected void checkAndPerformAttack(', 1)[1].split('public boolean humanGunner$tryImmediateCounterattack', 1)[0]
        self.assertLess(attack.index('!human.humanGunner$canMeleeAttack()'), attack.index('this.mob.stopUsingItem()'))
        counter = goal.split('public boolean humanGunner$tryImmediateCounterattack', 1)[1]
        self.assertLess(counter.index('shouldSuppressRecovery()'), counter.index('this.mob.stopUsingItem()'))

    def test_client_consumes_old_attack_sequence_while_blocking(self):
        renderer = ROOT / 'src/main/java/com/craftix/hostile_humans/client/renderer'
        animation = (renderer / 'BetterCombatNpcAnimator.java').read_text(encoding='utf-8')
        guard = animation.split('// Shield-use updates', 1)[1].split('if (playback.sequence == Integer.MIN_VALUE)', 1)[0]
        self.assertIn('UseAnim.BLOCK', guard)
        self.assertIn('playback.sequence = sequence', guard)
        self.assertIn('playback.attackStack = null', guard)
        self.assertIn('return false', guard)
        model = (renderer / 'HumanPlayerModel.java').read_text(encoding='utf-8')
        self.assertIn('human.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK', model)

    def test_high_pressure_interrupts_recovery_before_food_advances(self):
        recovery = source('RecoverySupplies.java')
        tick = recovery.split('public static void tickHandCustody(Human human)', 1)[1]
        self.assertLess(tick.index('shouldSuppressRecovery()'), tick.index('OffhandFoodUse.tick(human)'))
        self.assertIn('finishUse(human, active, completed(human, active))', tick)
        self.assertIn('SpartanEquipmentCompat.isShield(active.displacedHandItem())', recovery)
        human = (ROOT / 'src/main/java/com/craftix/hostile_humans/entity/entities/Human.java').read_text(encoding='utf-8')
        hurt = human.split('float previousHealth = this.getHealth();', 1)[1].split('return damaged;', 1)[0]
        self.assertLess(hurt.index('super.hurt('), hurt.index('recordPressureDamage('))
        self.assertLess(hurt.index('authorizeSelfDefense('), hurt.index('recordPressureDamage('))
        self.assertIn('previousHealth - this.getHealth()', hurt)

    def test_high_pressure_gunner_uses_custody_and_keeps_lease(self):
        shield = source('ShieldDefenseController.java')
        self.assertIn('GunCustody.beginCloseMeleeCounter(human)', shield)
        self.assertIn('GunCustody.canBeginPressureDefense(human)', shield)
        self.assertIn('source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)', shield)
        self.assertIn('recoveryPressure.blocked(human.tickCount,', shield)
        custody = source('GunCustody.java')
        restore = custody.split('static boolean restorePrimaryGun(', 1)[1]
        self.assertLess(restore.index('shouldSuppressRecovery()'), restore.index('clearLease(human)'))
        ticking = source('HumanGunner.java').split('RecoverySupplies.tickHandCustody(human)', 1)[1]
        self.assertLess(ticking.index('prepareHighPressureDefense()'), ticking.index('GunCustody.tick(human)'))

    def test_high_pressure_is_global_and_precedes_low_health_retreat(self):
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertTrue(defaults['shield_pressure']['enabled'])
        self.assertEqual(defaults['shield_pressure']['quiet_ticks'], 20)
        goal = source('AdaptiveCombatGoal.java').split('public boolean canUse()', 1)[1]
        self.assertLess(goal.index('pendingPhase = Phase.DEFEND'), goal.index('if (shouldEnterRetreat('))
        self.assertIn('"shield_pressure"', source('SplitConfigFiles.java'))
        self.assertIn('orderGroup(result, "shield_pressure"', source('TierConfigProjection.java'))

    def test_gunfire_is_separate_from_bounded_melee_guard(self):
        shield = source('ShieldDefenseController.java')
        pressure = shield.index('if (underGunfirePressure()) {')
        counter = shield.index('CombatPressurePolicy.shouldCounterAfterGuard(')
        self.assertLess(pressure, counter)
        self.assertIn('Math.max(phaseTicks, 2)', shield[pressure:counter])
        self.assertIn('if (defendingGunfire)', shield[pressure:counter])
        goal = source('AdaptiveCombatGoal.java')
        self.assertIn('phase == Phase.DEFEND && shouldEnterRetreat(threat)', goal)
        self.assertIn('if (!moved && !shields.underGunfirePressure() && !shields.wantsHighPressureGuard())', goal)

    def test_prediction_filters_rear_arrows_before_candidate_budget(self):
        shield = source('ShieldDefenseController.java')
        self.assertLess(shield.index('if (!inFront(arrow.position())) continue;'),
                        shield.index('nearest.offer(arrow)'))
        self.assertIn('|| !inFront(arrow.position())) continue;', shield)
        self.assertIn('!inFront(shooter.position())', shield)

    def test_gun_shield_is_passive_and_not_an_active_block(self):
        shield = source('ShieldDefenseController.java')
        self.assertIn('return GunSupport.isGunItem(human.getMainHandItem())', shield)
        passive = source('GunnerShieldProtection.java')
        self.assertIn('LivingDamageEvent event', passive)
        self.assertIn('event.isCanceled()', passive)
        self.assertIn('!GunSupport.isGunItem(human.getMainHandItem())', passive)
        self.assertIn('!isUsableShield(human.getOffhandItem())', passive)
        self.assertNotIn('isUsingItem()', passive)
        integration = source('TaczIntegration.java')
        self.assertIn('return !GunSupport.isGunItem(human.getMainHandItem())', integration)
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertEqual(defaults['tacz']['offhand_shield_damage_reduction'], .2)

    def test_background_reload_uses_native_ticks_and_same_owned_stack(self):
        custody = source('GunCustody.java')
        self.assertIn('ItemStack gun = human.getMainHandItem();', custody)
        self.assertIn('gun = data.getInventoryItem(gunSlot);', custody)
        reload = source('TaczHumanReloadTransactions.java')
        self.assertIn('data.currentGunItem = () -> weapon', reload)
        self.assertIn('gun.tickReload(data, weapon, shooter)', reload)
        self.assertIn('getInventoryItem(slot) == weapon', reload)
        self.assertIn('!GunCustody.isCloseDefenseActive(human)', reload)
        self.assertIn('!sameGun(data, weapon)', reload)
        self.assertEqual(reload.count('HumanAmmo.consumeMagazines(human, weapon)'), 1)
        mixin = source('mixin/TaczHumanReloadMixin.java')
        self.assertIn('TaczAmmoMixinContract.TICK_RELOAD, at = @At("HEAD")', mixin)
        self.assertIn('prepareNativeTick(human, data)', mixin)
        self.assertIn('if (humanGunner$reboundReload && shooter instanceof Human human', mixin)
        self.assertIn('if (data.currentGunItem != null) data.currentGunItem = human::getMainHandItem;', reload)

    def test_guarded_crossbow_uses_existing_native_ammo_path(self):
        charge = source('GuardedRangedCharge.java')
        self.assertIn('crossbow.releaseUsing(', charge)
        self.assertIn('CrossbowItem.getChargeDuration(weapon)', charge)
        self.assertIn('HumanArrowCombat.markNpcLoad(weapon)', charge)
        self.assertNotIn('startUsingItem(', charge.split('void tick(Human human)', 1)[0])
        self.assertNotIn('consumeArrow(', charge)

    def test_guarded_bow_and_crossbow_progress_survives_guard_release(self):
        charge = source('GuardedRangedCharge.java')
        shield = source('ShieldDefenseController.java')
        self.assertIn('progress = Math.min(20, progress + 3)', charge)
        self.assertIn('human.advanceUseTime(Math.max(0, progress - human.getTicksUsingItem()))', charge)
        self.assertIn('human.getMainHandItem() != charging', charge)
        self.assertIn('human.tickCount > expiresAt', charge)
        end = shield.split('void endCombat()', 1)[1].split('boolean underGunfirePressure()', 1)[0]
        self.assertNotIn('guardedCharge.reset()', end)
        ticking = shield.split('CombatUpdate tickCombat(', 1)[1]
        self.assertLess(ticking.index('guardedCharge.tick(human)'), ticking.index('if (underGunfirePressure())'))

    def test_ranged_counter_window_blocks_reentry_and_tactical_preemption(self):
        shield = source('ShieldDefenseController.java')
        wants = shield.split('private boolean wantsCombatNow(', 1)[1].split('boolean hasHeldMinimumCombatGuard()', 1)[0]
        self.assertLess(wants.index('if (hasRangedCounterWindow()) return false'), wants.index('if (underGunfirePressure())'))
        self.assertIn('guardedCharge.counterWindowTicks(human)', shield)
        self.assertIn('return Math.max(32,', source('GuardedRangedCharge.java'))
        self.assertIn('guardedCharge.ready(human)', shield)
        self.assertIn('if (shields.hasRangedCounterWindow()) return false', source('AdaptiveCombatGoal.java'))

    def test_weapon_goals_capture_before_stop_and_restore_after_start(self):
        goals = ROOT / 'src/main/java/com/craftix/hostile_humans/entity/ai/goal'
        for name in ('BowAttack.java', 'CrossbowGoal.java'):
            text = (goals / name).read_text(encoding='utf-8')
            capture = text.index('pauseRangedUseForGuard()')
            self.assertLess(capture, text.index('this.mob.stopUsingItem();', capture))
            restore = text.index('restoreRangedUseAfterGuard()')
            self.assertIn('startUsingItem(', text[:restore])
        bow = (goals / 'BowAttack.java').read_text(encoding='utf-8')
        self.assertIn('if (!(this.mob.getUseItem().getItem() instanceof BowItem)) return;', bow)

if __name__ == '__main__':
    unittest.main()
