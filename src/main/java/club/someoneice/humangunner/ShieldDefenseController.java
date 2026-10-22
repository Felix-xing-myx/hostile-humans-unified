package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import dev.felix.hostilehumans.core.ActionCoordinator;
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
    private enum Owner { COMBAT, BODYGUARD, PROJECTILE, PRESSURE, ADVANCE }
    public record IncomingArrow(Projectile projectile, LivingEntity shooter, double ticksToImpact) { }
    record CombatUpdate(int remainingTicks, boolean counterattack) { }
    private final Human human;
    private final CombatAiConfig config;
    private Owner owner;
    private ActionCoordinator leaseActions;
    private ActionCoordinator.Token lease;
    private boolean raising;
    private InteractionHand startingHand;
    private ItemStack startingShield;
    private ActionCoordinator startingActions;
    private ActionCoordinator.Token startingToken;
    /** Human's native item-use override calls this before accepting any shield start. */
    public boolean allowsShieldStart(InteractionHand hand) {
        return raising && startingHand == hand && startingShield == human.getItemInHand(hand)
                && startingActions != null && startingActions.running(startingToken);
    }
    private static java.util.EnumSet<ActionCoordinator.Resource> shieldResources() {
        return java.util.EnumSet.of(ActionCoordinator.Resource.FACING, ActionCoordinator.Resource.USE_ITEM,
                ActionCoordinator.Resource.MAIN_HAND, ActionCoordinator.Resource.OFF_HAND, ActionCoordinator.Resource.ATTACK);
    }
    public boolean ownsCombatDefense() { return owner == Owner.COMBAT && ownsCurrentShield(); }
    /** Called before any goal can issue another use/facing request on this server tick. */
    public void validateDefenseLease() {
        if (owner == null) return;
        if (!human.isAlive() || human.isRemoved() || human.isNoAi() || HumanInventorySession.isEditing(human)
                || human.shieldCoolDown > 0 || leaseActions == null || !leaseActions.running(lease)
                || !ownsCurrentShield()) {
            Owner lost = owner;
            release(lost);
            if (lost == Owner.PRESSURE) endPressureExecution();
            if (lost == Owner.ADVANCE) advance.end();
        }
    }
    private boolean shieldResourcesAvailable() {
        var actions = HumanBehaviorControl.actions(human);
        for (var resource : shieldResources()) {
            String holder = actions.owner(resource);
            if (!holder.isEmpty() && (lease == null || !holder.equals(lease.action()))) return false;
        }
        return true;
    }
    private boolean pressureWaitingForDrink;
    private int deferredPressureGraceUntil = Integer.MIN_VALUE;
    private boolean defendingGunfire;
    private final GuardedRangedCharge guardedCharge = new GuardedRangedCharge();
    private final ShieldAdvancePolicy advance = new ShieldAdvancePolicy();
    public ShieldAdvancePolicy advancePolicy() { return advance; }
    /** Caller supplies verified faction/core shield, grounded melee mode, locked out-of-range
     * target and permission to move. The same shared snapshot is used for higher priority checks. */
    public boolean tickAdvance(boolean eligible, int effectiveTicks) {
        boolean allowed = config.enabled() && eligible && !human.isFleeing && canBlockWithShield()
                && (owner == null || owner == Owner.ADVANCE) && shieldResourcesAvailable()
                && !shouldSuppressRecovery() && meleeBearings().isEmpty()
                && !RecoverySupplies.hasHandCustody(human) && !human.humanGunner$hasPendingMeleeAttack();
        boolean requested = advance.observe(allowed, effectiveTicks, human.getRandom()::nextDouble);
        if (owner == Owner.ADVANCE && (!advance.active() || !ownsCurrentShield())) {
            release(Owner.ADVANCE); advance.end();
        }
        if (requested && !human.isUsingItem()) {
            InteractionHand hand = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            raise(Owner.ADVANCE, hand, false);
            if (owner == Owner.ADVANCE && !advance.begin(allowed)) release(Owner.ADVANCE);
        }
        if (owner == Owner.ADVANCE && ownsCurrentShield()) {
            LivingEntity target = human.getTarget();
            if (target != null && isValidThreat(target)) human.markShieldFacing(target);
            return true;
        }
        return false;
    }
    public void endAdvance() {
        if (owner == Owner.ADVANCE || advance.active()) { release(Owner.ADVANCE); advance.end(); }
    }
    private boolean preparingCombatGuard;
    private int lastAttackExecution = Integer.MIN_VALUE;
    private int meleeBlocks;
    private int meleeReentryCooldownTicks;
    /** Gray dual-wield policy supplies 120; all other modes supply zero. */
    public void meleeReentryCooldownTicks(int ticks) { meleeReentryCooldownTicks = Math.max(0, ticks); }
    private boolean meleeCounterRequested, attackObligation;
    private int detectionTick = Integer.MIN_VALUE;
    private List<LivingEntity> nearbyMelee = List.of(), lockedDefenseTargets = List.of();
    private List<Projectile> observedProjectiles;

    /** Detection layer may publish one shared snapshot; this module never grants target knowledge. */
    public void observeDefenseSnapshot(List<LivingEntity> nearby, List<LivingEntity> locked,
                                       List<Projectile> projectiles) {
        detectionTick = human.tickCount;
        nearbyMelee = List.copyOf(nearby);
        lockedDefenseTargets = List.copyOf(locked);
        observedProjectiles = List.copyOf(projectiles);
        lastProjectileProbeTick = Integer.MIN_VALUE;
    }
    /** Central lifecycle cleanup: release only our actual use, preserve earned cooldown. */
    public void cancelDefenseSession() {
        if (owner != null) release(owner);
        endPressureExecution(); endAdvance(); advance.end(); clearAttackObligation();
        guardedCharge.reset();
        pressureFacing.clear(); trackedArrows.clear();
        nearbyMelee = lockedDefenseTargets = List.of(); observedProjectiles = null;
        detectionTick = lastProjectileProbeTick = Integer.MIN_VALUE; lastProjectileProbe = null;
    }
    public void onAttackExecuted() { lastAttackExecution = human.tickCount; attackObligation = false; }
    public void clearAttackObligation() { attackObligation = false; meleeCounterRequested = false; }
    public boolean hasAttackObligation() { return attackObligation; }
    public boolean defenseOwnsFacing() { return ownsCurrentShield(); }
    public double defenseSpeedFactor() { return ownsCurrentShield() ? (owner == Owner.ADVANCE ? .8D : config.shieldUseSpeedMultiplier()) : 1; }
    public double defenseKnockbackFloor() { return ownsCurrentShield() ? (owner == Owner.ADVANCE ? .85D : .7D) : 0; }
    private void detectDefense() {
        if (detectionTick == human.tickCount) return;
        detectionTick = human.tickCount;
        nearbyMelee = human.level().getEntitiesOfClass(LivingEntity.class,
                human.getBoundingBox().inflate(4, 3, 4), this::isValidThreat);
        LivingEntity target = human.getTarget();
        lockedDefenseTargets = isValidThreat(target) ? List.of(target) : List.of();
        observedProjectiles = null;
    }
    private List<Float> meleeBearings() {
        detectDefense();
        List<Float> result = new ArrayList<>();
        for (LivingEntity candidate : nearbyMelee) if (predictsIncomingMelee(candidate))
            result.add(bearing(candidate));
        return result;
    }
    private float bearing(LivingEntity entity) {
        Vec3 offset = entity.position().subtract(human.position());
        return (float)Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90;
    }

    private final RecoveryPressureWindow recoveryPressure = new RecoveryPressureWindow();
    private boolean pressureCycleActive;
    private final PressureDefenseCycle pressureCycle = new PressureDefenseCycle();
    private final PressureGuardFacing pressureFacing = new PressureGuardFacing();
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
    private int reactiveGuardUntil;
    private int nextProjectileScan;
    private int lastProjectileProbeTick = Integer.MIN_VALUE;
    private IncomingArrow lastProjectileProbe;
    private final List<Projectile> trackedArrows = new ArrayList<>();
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

    /** Actual shieldable combat health loss; defense does not require offensive identity/permission. */
    public void recordPressureDamage(DamageSource source, float damage) {
        if (human.level().isClientSide || !Float.isFinite(damage) || damage <= 0) return;
        var direct = source.getDirectEntity();
        var attacker = source.getEntity();
        // Feeling damage is not offensive knowledge. Ownerless arrows may create pressure too.
        if (attacker instanceof LivingEntity living && isValidThreat(living)) CloseRangedTactics.damage(human, damage);
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
                || !(attacker instanceof LivingEntity) && !(direct instanceof Projectile)) return;
        ShieldPressureConfig rules = UnifiedConfig.get().shieldPressure();
        updatePressureCycle();
        if (!recoveryPressure.active(human.tickCount, rules) && !pressureCycle.attempting()) pressureFacing.clear();
        if (owner == Owner.COMBAT) defensePressureTick = human.tickCount;
        if (pressureCycleActive) recoveryPressure.executingEvent(human.tickCount, rules);
        recoveryPressure.record(human.tickCount, damage, human.getMaxHealth(), rules);
        if (RecoverySupplies.isDrinkingPotion(human) && !pressureCycle.attempting()
                && recoveryPressure.active(human.tickCount, rules)) pressureWaitingForDrink = true;
        pressureCycle.damage(human.tickCount, damage, human.getMaxHealth(), rules);
        recordPressureDirection(source);
    }

    /** Direction-only input shared by actual damage and successful shield blocks. */
    private void recordPressureDirection(DamageSource source) {
        // A moving projectile provides arrival direction, never its owner's remote coordinates.
        var direct = source.getDirectEntity();
        var attacker = source.getEntity();
        Vec3 direction = null;
        if (direct != null && direct != attacker && direct.getDeltaMovement().lengthSqr() > 1.0E-8)
            direction = direct.getDeltaMovement().scale(-1);
        else if (!source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE) && direct != null && direct == attacker)
            direction = direct.position().subtract(human.position());
        if (direction != null && Double.isFinite(direction.x) && Double.isFinite(direction.z)
                && direction.horizontalDistanceSqr() > 1.0E-8)
            pressureFacing.record((float)Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
    }

    public boolean shouldSuppressRecovery() {
        updatePressureCycle();
        if (!pressureDefenseAvailable()) { pressureWaitingForDrink = false; return false; }
        if (RecoverySupplies.isDrinkingPotion(human)) return false;
        return !pressureCycle.attempting() && (pressureWaitingForDrink
                || human.tickCount < deferredPressureGraceUntil
                || recoveryPressure.active(human.tickCount, UnifiedConfig.get().shieldPressure()));
    }

    boolean isPressureBreakAttempt() {
        updatePressureCycle();
        return pressureCycle.attempting();
    }

    private void updatePressureCycle() {
        if (human.level().isClientSide) return;
        if (!pressureDefenseAvailable()) {
            if (pressureCycleActive) endPressureExecution();
            clearDeferredPressure();
            return;
        }
        ShieldPressureConfig rules = UnifiedConfig.get().shieldPressure();
        if (pressureCycle.update(human.tickCount, pressureWaitingForDrink || human.tickCount < deferredPressureGraceUntil
                || recoveryPressure.active(human.tickCount, rules), rules)) {
            recoveryPressure.clear();
            clearDeferredPressure();
            pressureFacing.clear();
        }
    }

    private void clearDeferredPressure() {
        pressureWaitingForDrink = false;
        deferredPressureGraceUntil = Integer.MIN_VALUE;
    }

    private boolean pressureDefenseAvailable() {
        return !human.level().isClientSide && config.enabled() && human.isAlive() && human.shieldCoolDown <= 0
                && !HumanInventorySession.isEditing(human)
                && human.explosionThreat == null
                && (hasShieldInHands() || RecoverySupplies.hasRecoverableShield(human));
    }

    boolean wantsHighPressureGuard() { return shouldSuppressRecovery() && hasShieldInHands() && human.shieldCoolDown <= 0; }
    private void endPressureExecution() {
        pressureCycle.ended(human.tickCount, UnifiedConfig.get().shieldPressure());
        pressureCycleActive = false;
        recoveryPressure.clear();
        clearDeferredPressure();
    }

    void prepareHighPressureDefense() {
        // Publish/update the latched request only. Hand cancellation and shield use happen
        // in raise() after the complete defensive lease has been admitted.
        shouldSuppressRecovery();
    }

    /** Shared by every server melee entry, including native/fallback attacks. */
    public boolean blocksMeleeAttack() {
        return shouldSuppressRecovery() || human.isUsingItem()
                && human.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    private boolean wantsCombatNow(LivingEntity threat, IncomingArrow incomingArrow) {
        if (wantsHighPressureGuard() || owner == Owner.PRESSURE || owner == Owner.PROJECTILE) return false;
        if (human.isFleeing || !canBlockWithShield() || isHoldingTaczGun()
                || RecoverySupplies.hasActiveUse(human)) return false;
        if (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem())
                && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())) return false;
        if (attackObligation || human.tickCount < nextDefenseTick) return false;
        return !meleeBearings().isEmpty();
    }

    boolean hasHeldMinimumCombatGuard() {
        return human.tickCount - defenseStartTick >= CombatPressurePolicy.MINIMUM_EFFECTIVE_BLOCK_TICKS;
    }

    int beginCombat(LivingEntity threat) {
        if (raising) return 0;
        if (ownsCombatDefense()) return 41; // Keep the original per-raise timer, count and lease.
        if (owner == Owner.PRESSURE || owner == Owner.PROJECTILE || wantsHighPressureGuard()
                || RecoverySupplies.hasActiveUse(human)) return 0;
        release(Owner.BODYGUARD);
        endAdvance();
        preparingCombatGuard = false;
        defenseStartTick = defensePressureTick = human.tickCount;
        meleeBlocks = 0;
        meleeCounterRequested = false;
        maintainCombat(threat);
        return ownsCombatDefense() ? 41 : 0;
    }

    void endCombat() {
        if (owner != Owner.COMBAT) return; // A stale Goal.stop cannot settle the pressure/projectile owner.
        boolean pressureGuard = pressureCycleActive;
        release(Owner.COMBAT);
        if (pressureGuard) endPressureExecution();
        else if (!defendingProjectile) {
            attackObligation = lastAttackExecution < defenseStartTick;
            nextDefenseTick = human.tickCount + meleeReentryCooldownTicks;
        }
        meleeBlocks = 0;
        meleeCounterRequested = false;
        defendingGunfire = defendingProjectile = rangedWarning = false;
        projectileDefenseUntil = 0;
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
        if (owner != Owner.COMBAT || human.humanGunner$hasPendingMeleeAttack() || !canBlockWithShield()
                || BodyguardProtection.protectingFromExplosion(human, threat)) return new CombatUpdate(0, false);
        if (!ownsCombatDefense() || wantsHighPressureGuard() || human.isFleeing)
            return new CombatUpdate(0, false);
        maintainCombat(threat);
        guardedCharge.tick(human);
        List<Float> bearings = meleeBearings();
        if (!bearings.isEmpty()) human.markShieldFacingDirection(ShieldDefensePolicy.coverageYaw(
                bearings, human.getYRot(), true, human.getRandom().nextDouble()));
        if (meleeCounterRequested || meleeBlocks >= 3 || human.tickCount - defensePressureTick >= 40) {
            boolean counter = meleeCounterRequested;
            endCombat();
            attackObligation = true;
            return new CombatUpdate(0, counter);
        }
        return new CombatUpdate(2, false);
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
        if (!canPreemptivelyBlockProjectile() || human.tickCount < nextProjectileGuardTick
                || shouldSuppressRecovery() || !meleeBearings().isEmpty()) return null;
        if (lastProjectileProbeTick == human.tickCount) return lastProjectileProbe;
        lastProjectileProbeTick = human.tickCount;
        lastProjectileProbe = null;
        AABB bounds = human.getBoundingBox();
        if (observedProjectiles != null) {
            trackedArrows.clear(); trackedArrows.addAll(observedProjectiles);
        } else if (human.tickCount >= nextProjectileScan) {
            nextProjectileScan = human.tickCount + PROJECTILE_SCAN_INTERVAL;
            trackedArrows.clear();
            trackedArrows.addAll(human.level().getEntitiesOfClass(Projectile.class,
                    bounds.inflate(32, 16, 32), candidate -> candidate.isAlive() && candidate.getOwner() != human));
        }
        double earliest = Double.MAX_VALUE;
        for (Projectile arrow : trackedArrows) {
            if (!arrow.isAlive() || arrow.isRemoved() || arrow.level() != human.level()
                    || arrow.getOwner() == human || !inFront(arrow.position())) continue;
            ShieldProjectileBackend.Motion model = ShieldProjectileBackend.describe(arrow);
            if (model == null || !model.shieldable()) continue;
            Vec3 velocity = arrow.getDeltaMovement();
            if (velocity.lengthSqr() < .0001D) continue;
            if (arrow.getOwner() instanceof LivingEntity shooter && HumanRelations.allied(human, shooter)) continue;
            double impact = ProjectileShieldPolicy.impactTicks(arrow.position(), velocity, bounds,
                    human.getDeltaMovement(), model);
            if (!Double.isFinite(impact) || impact >= earliest || !trajectoryClear(arrow, impact) || !visibleArrow(arrow)) continue;
            lastProjectileProbe = new IncomingArrow(arrow,
                    arrow.getOwner() instanceof LivingEntity shooter ? shooter : null, impact);
            earliest = impact;
        }
        return lastProjectileProbe;
    }

    private boolean inFront(Vec3 point) {
        return ProjectileShieldPolicy.insideReactionView(human.getLookAngle(), point.subtract(human.getEyePosition()));
    }

    boolean allowsPressureReaction() {
        return !human.isNoAi() && !human.isSleeping() && !NavigationDebugService.controls(human)
                && !HumanGunner.isManualInventoryOpen(human)
                && human.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL
                && !human.isInLava() && !human.isPassenger() && human.explosionThreat == null
                && !RecoverySupplies.hasActiveUse(human)
                && (!human.isUsingItem() || SpartanEquipmentCompat.isShield(human.getUseItem())
                    || RangedWeaponCustody.isBowOrCrossbow(human.getUseItem()))
                && wantsHighPressureGuard();
    }
    boolean beginPressureReaction() {
        if (raising) return false;
        if (owner == Owner.PRESSURE && ownsCurrentShield()) return true;
        if (!allowsPressureReaction()) return false;
        endCombat();
        endProjectileReaction();
        release(Owner.BODYGUARD);
        endAdvance();
        InteractionHand hand = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        raise(Owner.PRESSURE, hand, false);
        if (owner != Owner.PRESSURE || !ownsCurrentShield()) return false;
        facePressure();
        return true;
    }
    boolean continuesPressureReaction() {
        return owner == Owner.PRESSURE && ownsCurrentShield() && allowsPressureReaction();
    }
    void tickPressureReaction() {
        if (owner != Owner.PRESSURE || !ownsCurrentShield()) return;
        guardedCharge.tick(human);
        pressureCycle.guarded(human.tickCount);
        pressureCycleActive = true;
        facePressure();
    }
    void endPressureReaction() {
        if (owner != Owner.PRESSURE) return;
        release(Owner.PRESSURE);
        pressureWaitingForDrink = false;
        deferredPressureGraceUntil = Integer.MIN_VALUE;
        endPressureExecution();
        if (!recoveryPressure.active(human.tickCount, UnifiedConfig.get().shieldPressure())) pressureFacing.clear();
    }
    private void facePressure() {
        detectDefense();
        List<Float> bearings = new ArrayList<>();
        for (LivingEntity target : lockedDefenseTargets) if (isValidThreat(target)) {
            // Shared discovery may authorize a target without revealing its current hidden position.
            Vec3 known = HumanPerception.knownPosition(human, target);
            if (known == null) continue;
            Vec3 offset = known.subtract(human.position());
            if (offset.horizontalDistanceSqr() > 1.0E-8)
                bearings.add((float) Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90);
        }
        if (!bearings.isEmpty()) pressureFacing.record(ShieldDefensePolicy.coverageYaw(
                bearings, human.getYRot(), false, human.getRandom().nextDouble()));
        if (ownsCurrentShield()) human.markShieldFacingDirection(pressureFacing.step(human.tickCount, human.getYRot()));
    }

    private boolean visibleArrow(Projectile arrow) {
        return inFront(arrow.position()) && human.level().clip(new ClipContext(human.getEyePosition(),
                arrow.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                human)).getType() == HitResult.Type.MISS;
    }

    /** A short physical reaction to a seen projectile, not permission to target its owner. */
    boolean allowsProjectileReaction() {
        return config.enabled() && human.isAlive() && !HumanTargetCoordinator.suspended(human)
                && !human.isFleeing && !human.isInLava() && human.explosionThreat == null
                && !human.isPassenger() && !RecoverySupplies.hasHandCustody(human)
                && !RetreatRecoveryPolicy.shouldForceRetreat(human)
                && !human.humanGunner$hasPendingMeleeAttack() && !shouldSuppressRecovery()
                && (!human.isUsingItem() || SpartanEquipmentCompat.isShield(human.getUseItem())
                    || RangedWeaponCustody.isBowOrCrossbow(human.getUseItem()))
                && (owner == null || owner == Owner.PROJECTILE || owner == Owner.ADVANCE)
                && meleeBearings().isEmpty() && canPreemptivelyBlockProjectile();
    }
    boolean beginProjectileReaction(IncomingArrow arrow) {
        if (raising) return false;
        if (owner == Owner.PROJECTILE && ownsCurrentShield()) return true;
        if (!allowsProjectileReaction() || arrow == null || !visibleArrow(arrow.projectile())) return false;
        release(Owner.BODYGUARD);
        endAdvance();
        reactiveGuardUntil = human.tickCount + 100;
        InteractionHand hand = SpartanEquipmentCompat.isShield(human.getOffhandItem())
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        raise(Owner.PROJECTILE, hand, true);
        if (owner != Owner.PROJECTILE || !ownsCurrentShield()) return false;
        human.markShieldFacing(arrow.projectile());
        return true;
    }
    boolean continuesProjectileReaction() {
        return owner == Owner.PROJECTILE && ownsCurrentShield() && allowsProjectileReaction()
                && human.tickCount < reactiveGuardUntil && findIncomingArrow() != null;
    }
    void tickProjectileReaction() {
        if (owner != Owner.PROJECTILE || !ownsCurrentShield()) return;
        IncomingArrow incoming = findIncomingArrow();
        if (incoming != null) {
            // Keep the original bounded deadline; a stream of arrows cannot hold this lease forever.
            human.getLookControl().setLookAt(incoming.projectile(), 90, 90);
            human.markShieldFacing(incoming.projectile());
        }
        guardedCharge.tick(human);
    }
    void endProjectileReaction() {
        if (owner != Owner.PROJECTILE) return;
        release(Owner.PROJECTILE);
        reactiveGuardUntil = 0;
        nextProjectileGuardTick = human.tickCount + 40;
    }

    private boolean trajectoryClear(Projectile arrow, double impactTicks) {
        Vec3 point = arrow.position(), velocity = arrow.getDeltaMovement();
        ShieldProjectileBackend.Motion model = ShieldProjectileBackend.describe(arrow);
        if (model == null || !model.shieldable()) return false;
        double drag = model.drag(), gravity = model.gravity();
        for (int tick = 0; tick < Math.ceil(impactTicks); tick++) {
            Vec3 end = point.add(velocity.scale(Math.min(1.0D, impactTicks - tick)));
            if (human.level().clip(new ClipContext(point, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, human)).getType() != HitResult.Type.MISS) return false;
            point = end;
            velocity = velocity.add(model.acceleration()).scale(drag).add(0.0D, -gravity, 0.0D);
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
        if (blocked) {
            observeBlockedHit();
            recordPressureDirection(source);
        }
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
            defensePressureTick = human.tickCount;
            if (owner == Owner.COMBAT && !pressureCycleActive && !defendingProjectile) {
                meleeBlocks++;
                if (human.getRandom().nextInt(3) == 0) meleeCounterRequested = true;
            }
            if (pressureCycleActive || owner == Owner.PRESSURE)
                recoveryPressure.executingEvent(human.tickCount, UnifiedConfig.get().shieldPressure());
            else recoveryPressure.blocked(human.tickCount, UnifiedConfig.get().shieldPressure());
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
            dangerRange = Math.min(12.0D, GunRangePolicy.forWeapon(human, human.getMainHandItem()).minimum());
        }
        double dangerRangeSqr = dangerRange * dangerRange;
        return isValidThreat(target) && human.distanceToSqr(target) <= dangerRangeSqr;
    }

    private boolean predictsIncomingMelee(LivingEntity attacker) {
        if (!isValidThreat(attacker) || isRangedThreat(attacker)
                || !human.getSensing().hasLineOfSight(attacker)) return false;
        Vec3 offset = attacker.position().subtract(human.position());
        Vec3 toward = offset.multiply(1, 0, 1).normalize();
        Vec3 enemyLook = attacker.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 ownLook = human.getLookAngle().multiply(1, 0, 1).normalize();
        return ShieldDefensePolicy.meleeCandidate(offset.x, offset.y, offset.z,
                enemyLook.dot(toward.scale(-1)), ownLook.dot(toward), hasMeleeIntent(attacker) || attacker.swinging);
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
        if ((human.isFleeing || isHoldingTaczGun()) && !wantsHighPressureGuard()
                || human.humanGunner$hasPendingMeleeAttack()) return;
        if (human.isUsingItem() && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())) return;
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
        return hasShieldInHands() && human.shieldCoolDown <= 0 && (!isHoldingTaczGun() || shouldSuppressRecovery());
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
        return human.canAttack(entity) && SoldierOrder.allowsTarget(human, entity)
                && HumanPerception.canTarget(human,entity);
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
        if (threat == null || human.isFleeing || isHoldingTaczGun()) { endBodyguard(); return; }
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
        if (raising || human.level().isClientSide || !human.isAlive() || human.isRemoved() || human.isNoAi()
                || HumanInventorySession.isEditing(human) || RecoverySupplies.hasActiveUse(human)
                || human.isUsingItem() && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem()) || owner != null
                || (human.isFleeing || isHoldingTaczGun()) && requestedOwner != Owner.PRESSURE) return;
        ItemStack candidate = human.getItemInHand(hand);
        if (!SpartanEquipmentCompat.isShield(candidate) || human.shieldCoolDown > 0) return;
        raising = true;
        ActionCoordinator actions = null;
        ActionCoordinator.Token requested = null;
        boolean admitted = false;
        try {
            actions = HumanBehaviorControl.actions(human);
            int priority = switch (requestedOwner) {
                case PRESSURE -> 300; case COMBAT -> 250; case PROJECTILE -> 200;
                case ADVANCE -> 150; case BODYGUARD -> 100;
            };
            requested = actions.request("shield:" + requestedOwner.name().toLowerCase(java.util.Locale.ROOT),
                    shieldResources(), priority, true, 2410);
            if (!actions.acquire(requested)) return;
            if (RecoverySupplies.hasActiveUse(human)
                    || human.isUsingItem() && !RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())
                    || human.getItemInHand(hand) != candidate || HumanInventorySession.isEditing(human)) return;
            if (requestedOwner == Owner.PRESSURE) human.humanGunner$cancelMeleeAttackForDefense();
            guardedCharge.capture(human);
            if (human.isUsingItem() && RangedWeaponCustody.isBowOrCrossbow(human.getUseItem())) human.stopUsingItem();
            human.setChargingCrossbow(false);
            startingHand = hand; startingShield = candidate;
            startingActions = actions; startingToken = requested;
            if (projectile) human.humanGunner$startProjectileShield(hand);
            else human.humanGunner$startMovementShield(hand);
            if (!actions.running(requested) || !human.isAlive() || human.isRemoved() || human.isNoAi()
                    || HumanInventorySession.isEditing(human) || human.getItemInHand(hand) != candidate
                    || !human.isUsingItem() || human.getUsedItemHand() != hand || human.getUseItem() != candidate) return;
            owner = requestedOwner; ownedShield = candidate;
            leaseActions = actions; lease = requested; admitted = true;
            if (pressureWaitingForDrink && requestedOwner == Owner.PRESSURE) {
                deferredPressureGraceUntil = human.tickCount + 20;
                pressureWaitingForDrink = false;
            }
        } finally {
            try {
                if (!admitted) {
                    try {
                        if (human.isUsingItem() && human.getUsedItemHand() == hand && human.getUseItem() == candidate)
                            human.stopUsingItem();
                    } finally {
                        if (requested != null) actions.finish(requested, ActionCoordinator.Status.FAILED, "shield raise rejected");
                    }
                }
            } finally {
                startingHand = null; startingShield = null; startingActions = null; startingToken = null;
                raising = false;
            }
        }
    }

    private boolean ownsCurrentShield() {
        return ownedShield != null && leaseActions != null && leaseActions.running(lease)
                && human.isUsingItem() && human.getUseItem() == ownedShield
                && human.getItemInHand(human.getUsedItemHand()) == ownedShield;
    }

    private void release(Owner requestedOwner) {
        if (owner != requestedOwner) return;
        ItemStack released = ownedShield;
        var actions = leaseActions;
        var releasedToken = lease;
        owner = null; ownedShield = null; leaseActions = null; lease = null;
        try {
            if (released != null && human.isUsingItem() && human.getUseItem() == released) human.stopUsingItem();
        } finally {
            if (actions != null && releasedToken != null)
                actions.finish(releasedToken, ActionCoordinator.Status.COMPLETED, "shield released");
        }
    }

}
