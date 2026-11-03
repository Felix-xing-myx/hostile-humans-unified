"""Cross-module source contracts; these do not execute Minecraft or TaCZ."""
import json
import unittest
from source_paths import SourcePath as Path

ROOT = Path(__file__).resolve().parents[3]
PKG = ROOT / 'src/main/java/dev/felix/hostilehumans'

def source(name):
    return (PKG / name).read_text(encoding='utf-8')

class SuppressiveShieldReloadContract(unittest.TestCase):
    def test_ranged_goals_respect_shield_lease_before_touching_use_state(self):
        goals = ROOT / 'src/main/java/dev/felix/hostilehumans/entity/ai/goal'
        for name in ('CrossbowGoal', 'BowAttack'):
            shot = (goals / (name + '.java')).read_text(encoding='utf-8').split('private void tickShot(', 1)[1]
            self.assertLess(shot.index('reservesRangedUse()'), shot.index('startUsingItem('))
        crossbow = (goals / 'CrossbowGoal.java').read_text(encoding='utf-8').split('private void tickShot(', 1)[1]
        self.assertLess(crossbow.index('!(mob.getUseItem().getItem() instanceof CrossbowItem)'), crossbow.index('crossbowState = CrossbowState.CHARGED'))
        self.assertIn('CrossbowItem.isCharged(heldCrossbow)', crossbow)
        self.assertLess(crossbow.index('this.mob.getUseItem() != heldCrossbow'), crossbow.index('this.crossbowState = CrossbowState.CHARGING'))
        shoot = source('HumanArrowCombat.java').split('public static boolean canShoot(', 1)[1].split('/** Read the projectile', 1)[0]
        self.assertIn('!human.hhu$shieldDefense().reservesRangedUse()', shoot)
        self.assertIn('SpartanEquipmentCompat.isShield(human.getUseItem())', shoot)

    def test_short_projectile_guard_survives_block_activation_delay(self):
        shield = source('ShieldDefenseController.java')
        reaction = shield.split('boolean continuesProjectileReaction()', 1)[1].split('void tickProjectileReaction()', 1)[0]
        self.assertIn('CombatPressurePolicy.MINIMUM_EFFECTIVE_BLOCK_TICKS', reaction)
        self.assertIn('human.tickCount < reactiveGuardUntil', reaction)
        self.assertIn('reactiveGuardUntil = human.tickCount + 100', shield)
        self.assertIn('nextProjectileGuardTick = human.tickCount + 40', shield)

    def test_pressure_handoff_acquires_authority_before_cancelling_attack(self):
        shield = source('ShieldDefenseController.java')
        raise_body = shield.split('private void raise(', 1)[1].split('private boolean ownsCurrentShield()', 1)[0]
        self.assertLess(raise_body.index('actions.acquire(requested)'), raise_body.index('hhu$cancelMeleeAttackForDefense()'))
        self.assertLess(raise_body.index('actions.acquire(requested)'), raise_body.index('hhu$startMovementShield(hand)'))
        self.assertNotIn('GunCustody.beginCloseMeleeCounter', raise_body)
        human = (ROOT / 'src/main/java/dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
        cancel = human.split('public void hhu$cancelMeleeAttackForDefense()', 1)[1].split('private void wearEquippedItem', 1)[0]
        for expected in ('pendingBetterCombatTarget = null', 'pendingBetterCombatUpswingTicks = 0',
                         'setNpcBetterCombatAttackAnimation("")', 'swinging = false', 'MeleeActionControl.finish'):
            self.assertIn(expected, cancel)
        self.assertIn('!hhu$shieldDefense().blocksMeleeAttack()', human)

    def test_old_melee_goal_cannot_lower_emergency_shield_to_attack(self):
        goal = (ROOT / 'src/main/java/dev/felix/hostilehumans/behavior/combat/melee/MeleeAttackGoal.java').read_text(encoding='utf-8')
        attack = goal.split('protected void checkAndPerformAttack(', 1)[1].split('public boolean hhu$tryImmediateCounterattack', 1)[0]
        self.assertLess(attack.index('!human.hhu$canMeleeAttack()'), attack.index('this.mob.stopUsingItem()'))
        counter = goal.split('public boolean hhu$tryImmediateCounterattack', 1)[1]
        self.assertLess(counter.index('shouldSuppressRecovery()'), counter.index('this.mob.stopUsingItem()'))

    def test_client_consumes_old_attack_sequence_while_blocking(self):
        renderer = ROOT / 'src/main/java/dev/felix/hostilehumans/client/render'
        animation = (renderer / 'BetterCombatNpcAnimator.java').read_text(encoding='utf-8')
        guard = animation.split('// Shield-use updates', 1)[1].split('if (sequence != playback.sequence)', 1)[0]
        self.assertIn('HumanShieldPresentation.active(human)', guard)
        self.assertIn('if (!visual.active(now)) playback.sequence = sequence', guard)
        self.assertIn('playback.attackStack = null', guard)
        self.assertIn('return false', guard)
        model = (renderer / 'HumanPlayerModel.java').read_text(encoding='utf-8')
        self.assertIn('HumanShieldPresentation.active(human)', model)
        self.assertIn('HumanShieldPresentation.apply(human, this)', model)

    def test_high_pressure_waits_for_current_drink_but_stops_food_before_advancing(self):
        recovery = source('RecoverySupplies.java')
        tick = recovery.split('public static void tickHandCustody(Human human)', 1)[1].split('private static boolean unavailable', 1)[0]
        self.assertLess(tick.index('shouldSuppressRecovery()'), tick.index('OffhandFoodUse.tick(human)'))
        self.assertIn('shouldSuppressRecovery() && !isDrinkingPotion(human)', tick)
        self.assertIn('finishUse(human, session, done)', tick)
        self.assertIn('human.getOffhandItem() != session.actualItem()', tick)
        human = (ROOT / 'src/main/java/dev/felix/hostilehumans/entity/Human.java').read_text(encoding='utf-8')
        hurt = human.split('float previousHealth = this.getHealth();', 1)[1].split('return damaged;', 1)[0]
        self.assertLess(hurt.index('super.hurt('), hurt.index('recordPressureDamage('))
        self.assertIn('previousHealth - this.getHealth()', hurt)

    def test_high_pressure_can_keep_gun_and_does_not_claim_movement(self):
        shield = source('ShieldDefenseController.java')
        prepare = shield.split('void prepareHighPressureDefense()', 1)[1].split('public boolean blocksMeleeAttack()', 1)[0]
        self.assertNotIn('beginCloseMeleeCounter', prepare)
        self.assertIn('source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)', shield)
        self.assertIn('recoveryPressure.blocked(human.tickCount,', shield)
        pressure_goal = source('PressureReactionGoal.java')
        self.assertIn('setFlags(EnumSet.noneOf(Flag.class))', pressure_goal)
        self.assertNotIn('getNavigation().stop()', pressure_goal)
        self.assertIn('requestedOwner != Owner.PRESSURE', shield)
        self.assertIn('GunCustody.cancelBackgroundReloads(human)', source('HumanBehaviorControl.java'))

    def test_high_pressure_is_global_and_independent_of_retreat_move(self):
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        expected = {'enabled': True, 'dps_smoothing_ticks': 10, 'dps_ratio': .22,
                    'quiet_ticks': 20, 'max_guard_ticks': 120, 'cooldown_ticks': 70}
        for key, value in expected.items():
            self.assertEqual(defaults['shield_pressure'][key], value)
        self.assertIn('"shield_pressure"', source('SplitConfigFiles.java'))
        self.assertIn('orderGroup(result, "shield_pressure"', source('TierConfigProjection.java'))
        self.assertIn('setFlags(EnumSet.noneOf(Flag.class))', source('PressureReactionGoal.java'))

    def test_pressure_and_melee_guard_use_separate_execution_owners(self):
        shield = source('ShieldDefenseController.java')
        combat = shield.split('CombatUpdate tickCombat(', 1)[1].split('private void updateDefensePressure()', 1)[0]
        self.assertIn('owner != Owner.COMBAT', combat)
        self.assertIn('meleeBlocks >= 3', combat)
        self.assertIn('human.tickCount - defensePressureTick >= 20', combat)
        self.assertIn('attackObligation = true', combat)
        end = shield.split('void endCombat()', 1)[1].split('boolean underGunfirePressure()', 1)[0]
        self.assertIn('if (owner != Owner.COMBAT) return', end)
        self.assertIn('case ADVANCE -> 200', shield)
        self.assertIn('case PROJECTILE -> 150', shield)

    def test_prediction_filters_rear_projectiles_before_trajectory_work(self):
        shield = source('ShieldDefenseController.java')
        probe = shield.split('IncomingArrow findIncomingArrow()', 1)[1].split('private boolean inFront(', 1)[0]
        self.assertLess(probe.index('!inFront(arrow.position())'), probe.index('ShieldProjectileBackend.describe(arrow)'))
        self.assertLess(probe.index('!inFront(arrow.position())'), probe.index('ProjectileShieldPolicy.impactTicks'))
        self.assertIn('visibleArrow(arrow)', probe)
        self.assertIn('ProjectileShieldPolicy.insideReactionView', shield)
        director = source('HumanCombatDirector.java')
        self.assertIn('PerceptionBudget.claim', director)
        self.assertIn('limit(32)', director)

    def test_gun_shield_is_passive_and_not_an_active_block(self):
        shield = source('ShieldDefenseController.java')
        self.assertIn('return GunSupport.isGunItem(human.getMainHandItem())', shield)
        passive = source('GunnerShieldProtection.java')
        self.assertIn('static double multiplier(LivingEntity target, DamageSource source)', passive)
        # The old after-absorption event cannot apply a second reduction or wear transaction.
        legacy = passive.split('public static void onDamage(', 1)[1]
        self.assertNotIn('event.setAmount(', legacy)
        self.assertNotIn('damagePassiveOffhandShield(', legacy)
        self.assertIn('!GunSupport.isGunItem(human.getMainHandItem())', passive)
        self.assertIn('!isUsableShield(human.getOffhandItem())', passive)
        self.assertNotIn('isUsingItem()', passive)
        self.assertIn('source.is(TRUE_DAMAGE)', passive)
        self.assertIn('source.is(DamageTypeTags.BYPASSES_ARMOR)', passive)
        self.assertIn('!HumanDamagePipeline.physical(source)', passive)
        pipeline = source('HumanDamagePipeline.java')
        self.assertEqual(pipeline.count('GunnerShieldProtection.multiplier(target, source)'), 1)
        self.assertIn('!Float.isFinite(amount) || amount <= 0', pipeline)
        self.assertIn('DamagePartition.armor(amount, fraction, passive,', pipeline)
        self.assertIn('damageFlag("passive_shield_before_armor", true)', pipeline)
        self.assertIn('amount * (1 - fraction)', pipeline)
        partition = source('DamagePartition.java')
        self.assertIn('if (passiveBeforeArmor) normal *= passiveMultiplier;', partition)
        self.assertIn('if (!passiveBeforeArmor) normal *= passiveMultiplier;', partition)
        self.assertIn('piercing + normal', partition)
        # Both vanilla implementations enter the same seam after Forge's LivingHurt cancellation gate.
        for name in ('LivingEntityDamagePipelineMixin', 'PlayerDamagePipelineMixin'):
            mixin = source('mixin/' + name + '.java')
            self.assertIn('m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V', mixin)
            self.assertIn('m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F', mixin)
            self.assertIn('HumanDamagePipeline.armor(', mixin)
        self.assertNotIn('.hurt(', pipeline)
        integration = source('TaczIntegration.java')
        self.assertIn('return human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())', integration)
        defaults = json.loads((ROOT / 'src/main/resources/defaults/hostile_humans_unified.json').read_text(encoding='utf-8'))
        self.assertEqual(defaults['tacz']['offhand_shield_damage_reduction'], .1)

    def test_background_reload_uses_native_ticks_and_same_owned_stack(self):
        custody = source('GunCustody.java')
        self.assertIn('ItemStack gun=human.getMainHandItem();', custody)
        self.assertIn('HumanEquipmentExchange.locate', custody)
        self.assertNotIn('setItemSlot(', custody)
        reload = source('TaczHumanReloadTransactions.java')
        self.assertIn('data.currentGunItem = () -> weapon', reload)
        self.assertIn('gun.tickReload(data, weapon, shooter)', reload)
        self.assertIn('GunReloadAuthority.validIdentity(human,weapon,clock.weapon,clock.session)', reload)
        self.assertIn('session=authority.session', reload)
        self.assertIn('GunCombatProfile.reloadWork(human,weapon)', reload)
        self.assertIn('sameGun(data,weapon)', reload)
        self.assertEqual(reload.count('authority.payOnCompletion('), 2)
        mixin = source('mixin/TaczHumanReloadMixin.java')
        self.assertIn('TaczAmmoMixinContract.TICK_RELOAD, at = @At("HEAD")', mixin)
        self.assertIn('prepareNativeTick(human, data)', mixin)
        self.assertIn('if (hhu$reboundReload && shooter instanceof Human human', mixin)
        self.assertIn('if (data.currentGunItem != null) data.currentGunItem = human::getMainHandItem;', reload)

    def test_guarded_crossbow_uses_existing_native_ammo_path(self):
        charge = source('GuardedRangedCharge.java')
        self.assertIn('crossbow.releaseUsing(', charge)
        self.assertIn('CrossbowItem.getChargeDuration(weapon)', charge)
        self.assertIn('HumanArrowCombat.markNpcLoad(weapon)', charge)
        self.assertNotIn('startUsingItem(', charge.split('void tick(Human human)', 1)[0])
        self.assertNotIn('consumeArrow(', charge)

    def test_guarded_bow_and_crossbow_progress_follows_original_weapon(self):
        charge = source('GuardedRangedCharge.java')
        shield = source('ShieldDefenseController.java')
        self.assertIn('HumanEquipmentExchange.Handle charging', charge)
        self.assertIn('HumanEquipmentExchange.locate(human, charging)', charge)
        self.assertIn('foregroundRate(human, weapon) / (1.5 * duration(weapon))', charge)
        self.assertIn('HumanInventorySession.isEditing(human)', charge)
        self.assertIn('HumanArrowCombat.crossbowCompletedBehindGuard(human, weapon)', charge)
        end = shield.split('void endCombat()', 1)[1].split('boolean underGunfirePressure()', 1)[0]
        self.assertNotIn('guardedCharge.reset()', end)

    def test_actual_counter_obligation_blocks_reentry_without_stale_timer(self):
        shield = source('ShieldDefenseController.java')
        wants = shield.split('private boolean wantsCombatNow(', 1)[1].split('boolean hasHeldMinimumCombatGuard()', 1)[0]
        self.assertIn('if (attackObligation || human.tickCount < nextDefenseTick) return false', wants)
        counter = shield.split('public boolean hasRangedCounterWindow()', 1)[1].split('/** Actual shieldable', 1)[0]
        self.assertIn('return attackObligation', counter)
        self.assertNotIn('isPressureBreakAttempt()', counter)
        self.assertIn('attackObligation = false', shield.split('public void onAttackExecuted()',1)[1].split('public void clearAttackObligation()',1)[0])
        self.assertIn('if (shields.hasRangedCounterWindow()) return false', source('AdaptiveCombatGoal.java'))

    def test_weapon_goals_capture_before_stop_and_restore_after_start(self):
        goals = ROOT / 'src/main/java/dev/felix/hostilehumans/entity/ai/goal'
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

    def test_verified_bc_profile_survives_read_failure_without_native_damage(self):
        adapter=source('BetterCombatMeleeCombat.java')
        self.assertIn('profile || failures < MAX_PROFILE_FAILURES ? WAIT : NATIVE',adapter)
        self.assertIn('if (read.available()) state.attributes = read.attributes()',adapter)
        self.assertIn('state.attributes = null;',adapter)
        renderer=ROOT / 'src/main/java/dev/felix/hostilehumans/client/render'
        animation=(renderer / 'BetterCombatNpcAnimator.java').read_text(encoding='utf-8')
        suppress=animation.split('static boolean shouldSuppressVanillaSwing',1)[1].split('/** True',1)[0]
        self.assertIn('!isApplicable(human) || resolveApi() == null',suppress)
        self.assertIn('playback.failedSequence != human.getNpcBetterCombatAttackAnimationSequence()',suppress)
        self.assertNotIn('hasActiveCustomAnimation',suppress)
