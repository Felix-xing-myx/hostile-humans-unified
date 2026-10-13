package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.UseAnim;

/** One per Human. Owns shield decisions/timers; goals retain navigation and attack execution.
 * Reactive and anticipatory requests share ownership, bounded observation and counter windows.
 */
public final class ShieldDefenseController {
    private enum Owner { COMBAT, BODYGUARD }
    public record IncomingArrow(AbstractArrow projectile, LivingEntity shooter, double ticksToImpact) { }
    record CombatUpdate(int remainingTicks, boolean counterattack) { }
    private final Human human;
    private final CombatAiConfig config;
    private Owner owner;
    private boolean defendingGunfire;
    private final GuardedRangedCharge guardedCharge = new GuardedRangedCharge();
    private boolean preparingCombatGuard;
    private final RecoveryPressureWindow recoveryPressure = new RecoveryPressureWindow();
    private final PressureDefenseCycle pressureCycle = new PressureDefenseCycle();
    private ItemStack ownedShield;
    private int guardBlockUntil, nextGuardBlock;
    private int nextDefenseTick;
    private int defenseStartTick;
    private int defensePressureTick;
    private int defenseObservedHurtTimestamp = Integer.MIN_VALUE;
    private int defenseObservedGunfireUntil;
    private int projectileDefenseUntil;
    private int nextProjectileGuardTick;
    private boolean defendingProjectile;
    private int lastShieldTriggerHurtTimestamp = Integer.MIN_VALUE;
    private int lastShieldTriggerGunfireUntil;
    private static final int PROJECTILE_SCAN_INTERVAL = 3;
    private static final int MAX_TRACKED_ARROWS = 32;
    private static final int SHOOTER_MEMORY_TICKS = 100;
    private int nextProjectileScan;
    private int lastProjectileProbeTick = Integer.MIN_VALUE;
    private IncomingArrow lastProjectileProbe;
    private final List<AbstractArrow> trackedArrows = new ArrayList<>();
    private LivingEntity rememberedShooter;
    private int shooterMemoryUntil;
    private int lastBlockedTick = Integer.MIN_VALUE;
    private boolean rangedWarning;
    private int rangedWarningUntil;
    private int nextCounterGuardTick;


    public ShieldDefenseController(Human human) {
        this.human = human;
        this.config = CombatAiConfig.forHuman(human);
    }

    boolean wantsCombat(LivingEntity threat, IncomingArrow incomingArrow) {
        preparingCombatGuard = wantsCombatNow(threat, incomingArrow);
        return preparingCombatGuard;
    }

    /** Called before the weapon goal releases vanilla's sole active-use slot. */
    public void pauseRangedUseForGuard() {
        if (preparingCombatGuard || wantsHighPressureGuard()) guardedCharge.capture(human);
    }

    public void restoreRangedUseAfterGuard() { guardedCharge.restore(human); }

    /** Weapon goals must not compete with a shield lease for vanilla's sole use slot. */
    public boolean reservesRangedUse() {
        return owner == Owner.COMBAT || ownsCurrentShield();
    }

    public boolean hasRangedCounterWindow() {
        return isPressureBreakAttempt() || RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                && human.tickCount < nextCounterGuardTick;
    }

    /** Actual health loss only; environmental or allied damage cannot demand a shield. */
    public void recordPressureDamage(DamageSource source, float damage) {
        if (human.level().isClientSide
                || !(source.getEntity() instanceof LivingEntity attacker) || !isValidThreat(attacker)) return;
        CloseRangedTactics.damage(human, damage);
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)) return;
        ShieldPressureConfig rules = UnifiedConfig.get().shieldPressure();
        updatePressureCycle();
        recoveryPressure.record(human.tickCount, damage, human.getMaxHealth(), rules);
        pressureCycle.damage(human.tickCount, damage, human.getMaxHealth(), rules);
    }

    public boolean shouldSuppressRecovery() {
        updatePressureCycle();
        return !pressureCycle.attempting() && pressureDefenseAvailable()
                && recoveryPressure.active(human.tickCount, UnifiedConfig.get().shieldPressure());
    }

    boolean isPressureBreakAttempt() {
        updatePressureCycle();
        return pressureCycle.attempting();
    }

    private void updatePressureCycle() {
        if (human.level().isClientSide) return;
        if (!pressureDefenseAvailable()) { pressureCycle.clear(); return; }
        ShieldPressureConfig rules = UnifiedConfig.get().shieldPressure();
        if (pressureCycle.update(human.tickCount, recoveryPressure.active(human.tickCount, rules), rules))
            recoveryPressure.clear();
    }

    private boolean pressureDefenseAvailable() {
        return !human.level().isClientSide && config.enabled() && human.isAlive() && human.shieldCoolDown <= 0
                && !HumanInventorySession.isEditing(human)
                && human.explosionThreat == null
                && (hasShieldInHands() || RecoverySupplies.hasRecoverableShield(human))
                && GunCustody.canBeginPressureDefense(human);
    }

    boolean wantsHighPressureGuard() { return shouldSuppressRecovery() && canBlockWithShield(); }

    void prepareHighPressureDefense() {
        if (!shouldSuppressRecovery()) return;
        // Defense is a weapon handoff, never an implicit counterattack. Cancel
        // the previous windup before publishing a melee stack to other goals.
        human.humanGunner$cancelMeleeAttackForDefense();
        if (RecoverySupplies.hasActiveUse(human)) return;
        if (isHoldingTaczGun()) {
            if (human.isUsingItem()) human.stopUsingItem();
            GunCustody.beginCloseMeleeCounter(human);
        }
    }

    /** Shared by every server melee entry, including native/fallback attacks. */
    public boolean blocksMeleeAttack() {
        return shouldSuppressRecovery() || human.isUsingItem()
                && human.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    private boolean wantsCombatNow(LivingEntity threat, IncomingArrow incomingArrow) {
        if (isPressureBreakAttempt()) return false;
        if (wantsHighPressureGuard()) return true;
        if (human.isFleeing || !canBlockWithShield()) return false;
        if (CloseRangedTactics.keepDefense(human) && human.tickCount >= nextDefenseTick) return true;
        if (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem())
                && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())) return false;
        // Bow/crossbow counter windows must survive repeated hits long enough to fire.
        if (hasRangedCounterWindow()) return false;
        if (underGunfirePressure()) return true;
        if (human.tickCount < nextCounterGuardTick) return false;
        if (incomingArrow != null) {
            projectileDefenseUntil = human.tickCount + ProjectileShieldPolicy.guardWindowTicks(
                    isDedicatedRangedCombatant(), incomingArrow.ticksToImpact());
            return true;
        }
        // A known visible shooter drawing again is an earlier cue than an arrow at impact.
        if (human.tickCount >= nextDefenseTick && canPreemptivelyBlockProjectile()
                && !shouldDeferRangedProjectileGuard() && preparingKnownShot() != null) {
            rangedWarning = true;
            rangedWarningUntil = human.tickCount + CombatPressurePolicy.MAX_GUARD_WAIT_TICKS;
            return true;
        }
        if (!canBlockWithShield() || human.tickCount < nextDefenseTick) return false;
        int gunfireUntil = HumanGunner.recentGunfireUntil(human);
        int hurtTimestamp = human.getLastHurtByMobTimestamp();
        boolean freshGunfire = CombatPressurePolicy.isRecentGunfire(human.tickCount, gunfireUntil)
                && gunfireUntil != lastShieldTriggerGunfireUntil;
        boolean freshHit = isValidThreat(human.getLastHurtByMob())
                && CombatPressurePolicy.isRecentHit(human.tickCount, hurtTimestamp)
                && hurtTimestamp != lastShieldTriggerHurtTimestamp;
        if (freshGunfire) lastShieldTriggerGunfireUntil = gunfireUntil;
        if (freshHit) lastShieldTriggerHurtTimestamp = hurtTimestamp;
        // Close-range intent and recent melee hits are one defense decision, without a dice roll.
        boolean closeThreat = predictsIncomingMelee(threat)
                || (freshHit && predictsIncomingMelee(human.getLastHurtByMob()));
        return closeThreat || freshGunfire || (freshHit
                && healthRatio() >= config.retreatHealthRatio()
                && human.distanceToSqr(threat) > 12.25D);
    }

    boolean hasHeldMinimumCombatGuard() {
        return human.tickCount - defenseStartTick >= CombatPressurePolicy.MINIMUM_EFFECTIVE_BLOCK_TICKS;
    }

    int beginCombat(LivingEntity threat) {
        release(Owner.BODYGUARD);
        defendingGunfire = underGunfirePressure();
        guardedCharge.capture(human);
        preparingCombatGuard = false;
        if (human.isUsingItem() && RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())) human.stopUsingItem();
        human.setChargingCrossbow(false); // Background charge must not advertise a competing active hand pose.
        defendingProjectile = human.tickCount < projectileDefenseUntil || rangedWarning;
        int projectileWindow = projectileDefenseUntil - human.tickCount;
        int duration = defendingProjectile && isDedicatedRangedCombatant()
                ? Math.max(1, projectileWindow)
                : Math.max(rollShieldBlockDuration(), projectileWindow);
        if (rangedWarning || predictsIncomingMelee(threat))
            // Goal ticks also run on the start tick (held=0). Keep the lease through held=20.
            duration = Math.max(duration, CombatPressurePolicy.MAX_GUARD_WAIT_TICKS + 1);
        // The goal ticks on its start tick. A short predicted impact window must
        // still survive vanilla's block activation delay before ordinary release.
        duration = Math.max(duration, CombatPressurePolicy.MINIMUM_EFFECTIVE_BLOCK_TICKS + 1);
        defenseStartTick = human.tickCount;
        defensePressureTick = human.tickCount;
        defenseObservedHurtTimestamp = human.getLastHurtByMobTimestamp();
        defenseObservedGunfireUntil = HumanGunner.recentGunfireUntil(human);
        lastShieldTriggerHurtTimestamp = defenseObservedHurtTimestamp;
        lastShieldTriggerGunfireUntil = defenseObservedGunfireUntil;
        maintainCombat(threat);
        return duration;
    }

    void endCombat() {
        int reblockDelay = config.shieldBlockCooldownTicks();
        if (RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem()))
            reblockDelay = Math.max(reblockDelay, guardedCharge.counterWindowTicks(human));
        if (RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem())) {
            // Leave a complete crossbow charge-and-shot window after a block.
            reblockDelay = Math.max(reblockDelay, 24);
        }
        reblockDelay = Math.max(reblockDelay, CombatPressurePolicy.COUNTER_WINDOW_TICKS);
        nextDefenseTick = human.tickCount + reblockDelay;
        nextCounterGuardTick = human.tickCount + reblockDelay;
        if (defendingProjectile && isDedicatedRangedCombatant()) {
            nextProjectileGuardTick = human.tickCount + (isCrossbowCombatant() ? 32 : 24);
        }
        defendingGunfire = false;
        defendingProjectile = false;
        projectileDefenseUntil = 0;
        rangedWarning = false;
        release(Owner.COMBAT);
    }

    boolean underGunfirePressure() {
        return canBlockWithShield() && !human.isFleeing
                && CombatPressurePolicy.isRecentGunfire(human.tickCount, HumanGunner.recentGunfireUntil(human));
    }

    boolean awaitingProjectile() { return human.tickCount < projectileDefenseUntil || (rangedWarning && human.tickCount < rangedWarningUntil); }
    void releaseCombat() { release(Owner.COMBAT); }

    void maintainCombat(LivingEntity threat) {
        if (!BodyguardProtection.protectingFromExplosion(human, threat)) startShieldBlockIfAvailable();
    }

    CombatUpdate tickCombat(LivingEntity threat, int phaseTicks, boolean canCounterFromCurrentRange) {
        if (isPressureBreakAttempt()) {
            release(Owner.COMBAT);
            return new CombatUpdate(0, false);
        }
        if (human.isFleeing || human.humanGunner$hasPendingMeleeAttack()
                || BodyguardProtection.protectingFromExplosion(human, threat)) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) release(Owner.COMBAT);
            phaseTicks = 0;
            return new CombatUpdate(0, false);
        }
        MovementSpeedController.combat(human, false);
        if (!canBlockWithShield()
                && !(defendingProjectile && canPreemptivelyBlockProjectile())) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            return new CombatUpdate(0, false);
        }

        if (!wantsHighPressureGuard() && !underGunfirePressure() && awaitingProjectile()
                && shouldDeferRangedProjectileGuard() && hasHeldMinimumCombatGuard()) {
            projectileDefenseUntil = 0;
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            return new CombatUpdate(0, false);
        }

        updateDefensePressure();
        maintainCombat(threat);
        guardedCharge.tick(human);
        IncomingArrow incomingArrow = findIncomingArrow();
        net.minecraft.world.entity.Entity facingThreat = threat;
        if (incomingArrow != null) {
            defendingProjectile = true;
            projectileDefenseUntil = human.tickCount
                    + ProjectileShieldPolicy.guardWindowTicks(
                    isDedicatedRangedCombatant(), incomingArrow.ticksToImpact());
            // Face the incoming shot's source, not a changing combat/path
            // target. Do not change the attack focus merely to orient a shield.
            facingThreat = incomingArrow.shooter() != null && incomingArrow.shooter().isAlive()
                    && inFront(incomingArrow.shooter().position())
                    ? incomingArrow.shooter() : incomingArrow.projectile();
        } else if (rangedWarning && preparingKnownShot() != null) {
            facingThreat = rememberedShooter;
        } else if (CombatPressurePolicy.isRecentHit(human.tickCount, human.getLastHurtByMobTimestamp())
                && isValidThreat(human.getLastHurtByMob())
                && (threat == human.getLastHurtByMob()
                || (!BodyguardProtection.isBodyguard(human)
                && human.distanceToSqr(threat) > 36.0D))) {
            facingThreat = human.getLastHurtByMob();
        }
        human.getLookControl().setLookAt(facingThreat, 90.0F, 90.0F);
        human.markShieldFacing(facingThreat);
        if (rangedWarning && (preparingKnownShot() == null || human.tickCount >= rangedWarningUntil))
            rangedWarning = false;
        boolean awaitingProjectile = awaitingProjectile();
        if (awaitingProjectile) {
            phaseTicks = Math.max(phaseTicks, projectileDefenseUntil - human.tickCount);
            if (incomingArrow != null) defensePressureTick = human.tickCount;
        }
        // Survival pressure wins over treatment and ordinary ranged counter windows.
        if (wantsHighPressureGuard()) {
            maintainCombat(threat);
            if (ownsCurrentShield()) pressureCycle.guarded(human.tickCount);
            return new CombatUpdate(Math.max(phaseTicks, 2), false);
        }
        if (underGunfirePressure()) {
            defendingGunfire = true;
            maintainCombat(threat);
            if (guardedCharge.ready(human)
                    && human.tickCount - defenseStartTick >= CombatPressurePolicy.MAX_GUARD_WAIT_TICKS) {
                release(Owner.COMBAT);
                return new CombatUpdate(0, true);
            }
            return new CombatUpdate(Math.max(phaseTicks, 2), false);
        }
        if (defendingGunfire) {
            release(Owner.COMBAT);
            return new CombatUpdate(0, true);
        }
        int heldTicks = human.tickCount - defenseStartTick;
        int quietTicks = human.tickCount - defensePressureTick;
        boolean meleePressure = predictsIncomingMelee(threat);
        boolean blockedThisGuard = lastBlockedTick >= defenseStartTick;
        boolean counterReady = CombatPressurePolicy.shouldCounterAfterGuard(
                heldTicks, blockedThisGuard, awaitingProjectile);
        if (counterReady && (canCounterFromCurrentRange || heldTicks >= CombatPressurePolicy.MAX_GUARD_WAIT_TICKS)) {
            release(Owner.COMBAT);
            return new CombatUpdate(0, true);
        }
        if (!meleePressure && !awaitingProjectile
                && CombatPressurePolicy.shouldReleaseUnpressuredBlock(heldTicks, quietTicks, false, false)) {
            release(Owner.COMBAT);
            human.getPersistentData().putString("hostile_humans_unified:ai_phase", "shield_threat_gone");
            return new CombatUpdate(0, false);
        }

        if (!awaitingProjectile && CombatPressurePolicy.shouldReleaseForPursuit(
                heldTicks, quietTicks, !isRangedThreat(threat),
                human.distanceToSqr(threat), radialApproachSpeed(threat)
        )) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            human.getPersistentData().putString("hostile_humans_unified:ai_phase", "shield_pursuit_release");
            return new CombatUpdate(0, false);
        }

        return new CombatUpdate(phaseTicks, false);
    }

    private void updateDefensePressure() {
        int hurtTimestamp = human.getLastHurtByMobTimestamp();
        if (hurtTimestamp != defenseObservedHurtTimestamp) {
            defenseObservedHurtTimestamp = hurtTimestamp;
            lastShieldTriggerHurtTimestamp = hurtTimestamp;
            defensePressureTick = human.tickCount;
        }
        int gunfireUntil = HumanGunner.recentGunfireUntil(human);
        if (gunfireUntil >= human.tickCount && gunfireUntil != defenseObservedGunfireUntil) {
            defenseObservedGunfireUntil = gunfireUntil;
            lastShieldTriggerGunfireUntil = gunfireUntil;
            defensePressureTick = human.tickCount;
        }
    }

    IncomingArrow findIncomingArrow() {
        if (!canPreemptivelyBlockProjectile() || human.tickCount < nextCounterGuardTick
                || shouldDeferRangedProjectileGuard()) return null;
        if (lastProjectileProbeTick == human.tickCount) return lastProjectileProbe;
        lastProjectileProbeTick = human.tickCount;
        lastProjectileProbe = null;
        boolean ranged = isDedicatedRangedCombatant();
        if (ranged && human.tickCount < nextProjectileGuardTick) return null;
        AABB bounds = human.getBoundingBox();
        if (human.tickCount >= nextProjectileScan) {
            // Stagger subsequent refreshes. Geometry for these bounded candidates stays current.
            nextProjectileScan = human.tickCount + PROJECTILE_SCAN_INTERVAL
                    + (nextProjectileScan == 0 ? Math.floorMod(human.getId(), 3) : 0);
            trackedArrows.clear();
            PriorityQueue<AbstractArrow> nearest = new PriorityQueue<>(MAX_TRACKED_ARROWS + 1,
                    Comparator.comparingDouble((AbstractArrow arrow) -> human.distanceToSqr(arrow)).reversed());
            for (AbstractArrow arrow : human.level().getEntitiesOfClass(AbstractArrow.class,
                    bounds.inflate(32.0D, 16.0D, 32.0D),
                    candidate -> candidate.isAlive() && candidate.getOwner() != human
                            && candidate.getDeltaMovement().lengthSqr() >= 0.04D)) {
                if (!inFront(arrow.position())) continue;
                if (arrow.getOwner() instanceof LivingEntity shooter && HumanRelations.allied(human, shooter)) continue;
                if (bounds.getCenter().subtract(arrow.position()).dot(arrow.getDeltaMovement()) < 0.0D) continue;
                nearest.offer(arrow);
                if (nearest.size() > MAX_TRACKED_ARROWS) nearest.poll();
            }
            trackedArrows.addAll(nearest);
        }
        double earliest = Double.MAX_VALUE;
        for (AbstractArrow arrow : trackedArrows) {
            if (!arrow.isAlive() || arrow.isRemoved() || arrow.level() != human.level()
                    || !inFront(arrow.position())) continue;
            Vec3 velocity = arrow.getDeltaMovement();
            if (velocity.lengthSqr() < 0.04D
                    || bounds.getCenter().subtract(arrow.position()).dot(velocity) < 0.0D) continue;
            if (arrow.getOwner() instanceof LivingEntity shooter && HumanRelations.allied(human, shooter)) continue;
            double impact = ProjectileShieldPolicy.impactTicks(arrow.position(), velocity, bounds,
                    human.getDeltaMovement(), arrow.isInWater() ? 0.6D : 0.99D,
                    arrow.isNoGravity() ? 0.0D : 0.05D);
            if (!Double.isFinite(impact) || impact >= earliest
                    || (ranged && !ProjectileShieldPolicy.shouldGuardRangedUser(impact))) continue;
            if (!trajectoryClear(arrow, impact)) continue;
            lastProjectileProbe = new IncomingArrow(arrow,
                    arrow.getOwner() instanceof LivingEntity shooter ? shooter : null, impact);
            earliest = impact;
        }
        return lastProjectileProbe;
    }

    private boolean inFront(Vec3 point) {
        Vec3 offset = point.subtract(human.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 facing = human.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        return facing.dot(offset) >= 0.0D;
    }

    private boolean trajectoryClear(AbstractArrow arrow, double impactTicks) {
        Vec3 point = arrow.position(), velocity = arrow.getDeltaMovement();
        double drag = arrow.isInWater() ? 0.6D : 0.99D;
        double gravity = arrow.isNoGravity() ? 0.0D : 0.05D;
        for (int tick = 0; tick < Math.ceil(impactTicks); tick++) {
            Vec3 end = point.add(velocity.scale(Math.min(1.0D, impactTicks - tick)));
            if (human.level().clip(new ClipContext(point, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, human)).getType() != HitResult.Type.MISS) return false;
            point = end;
            velocity = velocity.scale(drag).add(0.0D, -gravity, 0.0D);
        }
        return true;
    }

    static void onShieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!event.isCanceled() && event.getBlockedDamage() > 0.0F && event.getEntity() instanceof Human defender)
            defender.humanGunner$shieldDefense().observeAttack(event.getDamageSource(), true);
    }

    /** Only an observed attacker is remembered; no omniscient scan of every archer. */
    public void observeAttack(DamageSource source, boolean blocked) {
        if (human.level().isClientSide) return;
        if (blocked) observeBlockedHit();
        if (source.getEntity() instanceof LivingEntity attacker
                && !HumanRelations.allied(human, attacker)
                && (source.getDirectEntity() instanceof Projectile || isRangedThreat(attacker))) {
            rememberedShooter = attacker;
            shooterMemoryUntil = human.tickCount + SHOOTER_MEMORY_TICKS;
        }
    }

    /** TaCZ applies its shield reduction outside vanilla ShieldBlockEvent. */
    public void observeBlockedHit() {
        if (!human.level().isClientSide) {
            lastBlockedTick = human.tickCount;
            recoveryPressure.blocked(human.tickCount, UnifiedConfig.get().shieldPressure());
        }
    }

    private LivingEntity preparingKnownShot() {
        LivingEntity shooter = rememberedShooter;
        if (shooter == null) return null;
        if (human.tickCount > shooterMemoryUntil || !shooter.isAlive() || shooter.level() != human.level()) {
            rememberedShooter = null;
            return null;
        }
        if (!isValidThreat(shooter) || !inFront(shooter.position())
                || human.distanceToSqr(shooter) > 4096.0D
                || !human.getSensing().hasLineOfSight(shooter) || !shooter.isUsingItem()) return null;
        UseAnim animation = shooter.getUseItem().getUseAnimation();
        if (animation != UseAnim.BOW && animation != UseAnim.CROSSBOW && animation != UseAnim.SPEAR) return null;
        Vec3 direction = human.getBoundingBox().getCenter().subtract(shooter.getEyePosition()).normalize();
        if (shooter.getLookAngle().dot(direction) < 0.8D) return null;
        if (shooter instanceof Mob mob && mob.getTarget() != human) return null;
        return shooter;
    }

    private boolean isDedicatedRangedCombatant() {
        return AmmoWeaponSelection.hasUsableGun(human)
                || RangedWeaponCustody.controlsMainHand(human)
                || isRangedCombat()
                || human.getMainHandItem().getItem() instanceof TridentItem
                || human.getOffhandItem().getItem() instanceof TridentItem;
    }

    private boolean isCrossbowCombatant() {
        return RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem());
    }

    private boolean canPreemptivelyBlockProjectile() {
        if (!hasShieldInHands() || human.shieldCoolDown > 0) {
            return false;
        }
        return !human.isFleeing && !isHoldingTaczGun();
    }

    private boolean shouldDeferRangedProjectileGuard() {
        if (!isDedicatedRangedCombatant()) {
            return false;
        }
        if (human.isFleeing
                || (AmmoWeaponSelection.hasUsableGun(human) && GunSupport.get().isReloading(human))
                || (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem())
                && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem()))) {
            return true;
        }
        LivingEntity target = human.getTarget();
        double dangerRange = isCrossbowCombatant() ? 16.0D : 12.0D;
        if (GunSupport.get().isGun(human.getMainHandItem())) {
            dangerRange = Math.min(12.0D, GunRangePolicy.forWeapon(human.getMainHandItem()).minimum());
        }
        double dangerRangeSqr = dangerRange * dangerRange;
        return isValidThreat(target) && human.distanceToSqr(target) <= dangerRangeSqr;
    }

    private boolean predictsIncomingMelee(LivingEntity attacker) {
        if (!isValidThreat(attacker)
                || isRangedThreat(attacker)
                || !human.getSensing().hasLineOfSight(attacker)) {
            return false;
        }
        double distanceSqr = human.distanceToSqr(attacker);
        if (distanceSqr > 12.25D) {
            return false;
        }
        Vec3 towardHuman = human.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        Vec3 direction = towardHuman.lengthSqr() < 0.01D
                ? attacker.getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize()
                : towardHuman.normalize();
        Vec3 look = attacker.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        Vec3 movement = attacker.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        boolean facingHuman = look.lengthSqr() > 0.01D && look.normalize().dot(direction) >= 0.60D;
        return CombatPressurePolicy.predictsIncomingMelee(
                distanceSqr, attacker.swinging, hasMeleeIntent(attacker) || attacker.swinging,
                facingHuman, movement.dot(direction)
        );
    }

    private double radialApproachSpeed(LivingEntity attacker) {
        Vec3 towardHuman = human.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        if (towardHuman.lengthSqr() < 0.01D) {
            return 0.0D;
        }
        return attacker.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)
                .dot(towardHuman.normalize());
    }

    private boolean hasMeleeIntent(LivingEntity attacker) {
        if (attacker instanceof Mob mob) {
            return mob.getTarget() == human;
        }
        return attacker.getLastHurtMob() == human
                && attacker.tickCount - attacker.getLastHurtMobTimestamp() <= 40;
    }

    private void startShieldBlockIfAvailable() {
        if (human.isFleeing || isHoldingTaczGun() || human.humanGunner$hasPendingMeleeAttack()) return;
        if (human.isUsingItem()) return;
        InteractionHand shieldHand = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? InteractionHand.OFF_HAND
                : SpartanEquipmentCompat.isShield(human.getMainHandItem())
                ? InteractionHand.MAIN_HAND : null;
        if (shieldHand == null) return;
        if (defendingProjectile && human.tickCount < projectileDefenseUntil) {
            raise(Owner.COMBAT, shieldHand, true);
        } else {
            raise(Owner.COMBAT, shieldHand, false);
        }
    }

    private boolean hasShieldInHands() {
        return SpartanEquipmentCompat.isShield(human.getOffhandItem())
                || SpartanEquipmentCompat.isShield(human.getMainHandItem());
    }

    private boolean canBlockWithShield() {
        return hasShieldInHands() && human.shieldCoolDown <= 0 && !isHoldingTaczGun();
    }

    private int rollShieldBlockDuration() {
        int minimum = config.shieldBlockDurationMinTicks();
        int span = Math.max(1, config.shieldBlockDurationMaxTicks() - minimum + 1);
        return minimum + human.getRandom().nextInt(span);
    }

    private boolean isValidThreat(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity == human) {
            return false;
        }
        if (HumanRelations.allied(human, entity)) {
            return false;
        }
        // Defense must respect the same faction/attack authorization as target selection.
        return human.canAttack(entity) && SoldierOrder.allowsTarget(human, entity);
    }

    private boolean isRangedCombat() {
        return CombatWeaponPolicy.usesTacticalRangedMovement(human);
    }

    private boolean isHoldingTaczGun() {
        return GunSupport.isGunItem(human.getMainHandItem());
    }

    private boolean isRangedThreat(LivingEntity target) {
        return CombatWeaponPolicy.isRangedThreat(target);
    }

    private double healthRatio() { return human.getHealth() / Math.max(1.0F, human.getMaxHealth()); }

    /** Bodyguard interception is a request to the same shield owner, without assigning aggro. */
    void tickBodyguard(ServerPlayer player, LivingEntity threat) {
        if (threat == null || human.isFleeing || isHoldingTaczGun() || isPressureBreakAttempt()) { endBodyguard(); return; }
        if (owner == Owner.BODYGUARD && human.tickCount >= guardBlockUntil) endBodyguard();
        if (human.getTarget() == null && human.shieldCoolDown <= 0 && !human.isUsingItem()
                && human.tickCount >= nextGuardBlock && human.distanceToSqr(player) <= 16.0D) {
            for (InteractionHand hand : new InteractionHand[] {InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND}) {
                if (!SpartanEquipmentCompat.isShield(human.getItemInHand(hand))) continue;
                raise(Owner.BODYGUARD, hand, true);
                guardBlockUntil = human.tickCount + 8;
                nextGuardBlock = human.tickCount + 16;
                break;
            }
        }
        if (owner == Owner.BODYGUARD && ownsCurrentShield()) human.markShieldFacing(threat);
    }

    void endBodyguard() { release(Owner.BODYGUARD); }

    private void raise(Owner requestedOwner, InteractionHand hand, boolean projectile) {
        if (human.level().isClientSide || human.isFleeing || isHoldingTaczGun() || human.isUsingItem()) return;
        if (projectile) human.humanGunner$startProjectileShield(hand);
        else human.humanGunner$startMovementShield(hand);
        // startUsingItem may reject a shield during a committed melee upswing.
        if (human.isUsingItem() && human.getUsedItemHand() == hand
                && SpartanEquipmentCompat.isShield(human.getUseItem())) {
            owner = requestedOwner;
            ownedShield = human.getUseItem();
        }
    }

    private boolean ownsCurrentShield() {
        return ownedShield != null && human.isUsingItem() && human.getUseItem() == ownedShield;
    }

    private void release(Owner requestedOwner) {
        if (owner != requestedOwner) return;
        if (ownsCurrentShield()) human.stopUsingItem();
        owner = null;
        ownedShield = null;
    }

}
