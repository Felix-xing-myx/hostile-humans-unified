package club.someoneice.humangunner;

import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One per Human. Owns shield decisions/timers; goals retain navigation and attack execution.
 * Existing combat timings are deliberately preserved until the replacement policy is agreed.
 */
public final class ShieldDefenseController {
    private enum Owner { COMBAT, BODYGUARD }
    public record IncomingArrow(AbstractArrow projectile, LivingEntity shooter, double ticksToImpact) { }
    record CombatUpdate(int remainingTicks, boolean counterattack) { }
    private final Human human;
    private final CombatAiConfig config;
    private Owner owner;
    private ItemStack ownedShield;
    private int guardBlockUntil, nextGuardBlock;
    private static final String REBLOCK_UNTIL = "hostile_humans_unified:shield_reblock_until";
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
    private int nextMeleePredictionTick;
    private final Map<UUID, Boolean> arrowDefenseDecisions = new LinkedHashMap<>();

    public ShieldDefenseController(Human human) {
        this.human = human;
        this.config = CombatAiConfig.forHuman(human);
    }

    boolean wantsCombat(LivingEntity threat, IncomingArrow incomingArrow) {
        if ((canBlockWithShield() && human.tickCount >= nextDefenseTick)
                || incomingArrow != null) {
            if (incomingArrow != null) {
                projectileDefenseUntil = human.tickCount
                        + ProjectileShieldPolicy.guardWindowTicks(
                        isDedicatedRangedCombatant(), incomingArrow.ticksToImpact());
                return true;
            }
            int gunfireUntil = HumanGunner.recentGunfireUntil(human);
            int hurtTimestamp = human.getLastHurtByMobTimestamp();
            boolean freshGunfire = CombatPressurePolicy.isRecentGunfire(human.tickCount, gunfireUntil)
                    && gunfireUntil != lastShieldTriggerGunfireUntil;
            boolean freshHit = isValidThreat(human.getLastHurtByMob())
                    && CombatPressurePolicy.isRecentHit(human.tickCount, hurtTimestamp)
                    && hurtTimestamp != lastShieldTriggerHurtTimestamp;
            if (consumeRecoveryReblockRequest()
                    && (freshHit || freshGunfire || predictsIncomingMelee(threat))) {
                return true;
            }
            if (freshGunfire) {
                lastShieldTriggerGunfireUntil = gunfireUntil;
            }
            if (freshHit) {
                lastShieldTriggerHurtTimestamp = hurtTimestamp;
            }
            if (freshGunfire
                    || (freshHit
                    && healthRatio() >= config.retreatHealthRatio()
                    && (human.distanceToSqr(threat) > 12.25D
                    || human.getRandom().nextDouble() < config.shieldBlockChanceAfterHit()))) {
                return true;
            }
            if (human.tickCount >= nextMeleePredictionTick
                    && predictsIncomingMelee(threat)) {
                boolean committedSwing = threat.swinging;
                nextMeleePredictionTick = human.tickCount + (committedSwing ? 12 : 18);
                if (committedSwing || human.getRandom().nextDouble() < 0.40D) {
                    return true;
                }
            }
        }
        return false;
    }

    int beginCombat(LivingEntity threat) {
        release(Owner.BODYGUARD);
        defendingProjectile = human.tickCount < projectileDefenseUntil;
        int projectileWindow = projectileDefenseUntil - human.tickCount;
        int duration = defendingProjectile && isDedicatedRangedCombatant()
                ? Math.max(1, projectileWindow)
                : Math.max(rollShieldBlockDuration(), projectileWindow);
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
        if (RangedWeaponCustody.isCrossbowWeapon(human.getMainHandItem())
                || RangedWeaponCustody.isCrossbowWeapon(human.getOffhandItem())) {
            // Leave a complete crossbow charge-and-shot window after a block.
            reblockDelay = Math.max(reblockDelay, 24);
        }
        nextDefenseTick = human.tickCount + reblockDelay;
        if (defendingProjectile && isDedicatedRangedCombatant()) {
            nextProjectileGuardTick = human.tickCount + (isCrossbowCombatant() ? 32 : 24);
        }
        defendingProjectile = false;
        projectileDefenseUntil = 0;
        release(Owner.COMBAT);
    }

    boolean awaitingProjectile() { return human.tickCount < projectileDefenseUntil; }
    void releaseCombat() { release(Owner.COMBAT); }

    void maintainCombat(LivingEntity threat) {
        if (!BodyguardProtection.protectingFromExplosion(human, threat)) startShieldBlockIfAvailable();
    }

    CombatUpdate tickCombat(LivingEntity threat, int phaseTicks, boolean canCounterFromCurrentRange) {
        if (human.humanGunner$hasPendingMeleeAttack()
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

        if (human.tickCount < projectileDefenseUntil
                && (isCloseMeleeEngagement() || shouldDeferRangedProjectileGuard())) {
            projectileDefenseUntil = 0;
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            return new CombatUpdate(0, false);
        }

        updateDefensePressure();
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
                    ? incomingArrow.shooter() : incomingArrow.projectile();
        } else if (CombatPressurePolicy.isRecentHit(human.tickCount, human.getLastHurtByMobTimestamp())
                && isValidThreat(human.getLastHurtByMob())) {
            facingThreat = human.getLastHurtByMob();
        }
        human.getLookControl().setLookAt(facingThreat, 90.0F, 90.0F);
        human.markShieldFacing(facingThreat);
        boolean awaitingProjectile = human.tickCount < projectileDefenseUntil;
        if (awaitingProjectile) {
            phaseTicks = Math.max(phaseTicks, projectileDefenseUntil - human.tickCount);
            if (incomingArrow != null) defensePressureTick = human.tickCount;
        }
        int heldTicks = human.tickCount - defenseStartTick;
        int quietTicks = human.tickCount - defensePressureTick;
        if (CombatPressurePolicy.shouldReleaseUnpressuredBlock(
                heldTicks, quietTicks, incomingArrow != null, predictsIncomingMelee(threat))) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) release(Owner.COMBAT);
            projectileDefenseUntil = 0;
            phaseTicks = 0;
            human.getPersistentData().putString("hostile_humans_unified:ai_phase", "shield_threat_gone");
            return new CombatUpdate(0, false);
        }
        if (defendingProjectile && isDedicatedRangedCombatant()
                && incomingArrow == null && heldTicks >= 6) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            return new CombatUpdate(0, false);
        }
        // Keep the shield through the incoming shot, but give a combatant that
        // can actually answer at this range a firing window between volleys.
        if ((!awaitingProjectile || (incomingArrow == null && canCounterFromCurrentRange))
                && CombatPressurePolicy.shouldOpenCounterWindow(
                heldTicks, quietTicks, canCounterFromCurrentRange
        )) {
            if (human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())) {
                release(Owner.COMBAT);
            }
            phaseTicks = 0;
            return new CombatUpdate(0, true);
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
        if (!canPreemptivelyBlockProjectile() || isCloseMeleeEngagement()) {
            return null;
        }
        boolean dedicatedRanged = isDedicatedRangedCombatant();
        if (dedicatedRanged && human.tickCount < nextProjectileGuardTick) {
            return null;
        }
        IncomingArrow closest = null;
        double earliestImpact = Double.MAX_VALUE;
        AABB bounds = human.getBoundingBox();
        AABB search = bounds.inflate(20.0D, 8.0D, 20.0D);
        Boolean deferRangedGuard = null;
        for (AbstractArrow arrow : human.level().getEntitiesOfClass(AbstractArrow.class, search)) {
            if (!arrow.isAlive() || arrow.getOwner() == human) {
                continue;
            }
            Vec3 velocity = arrow.getDeltaMovement();
            double speedSqr = velocity.lengthSqr();
            if (speedSqr < 0.04D) {
                continue;
            }
            if (bounds.getCenter().subtract(arrow.position()).dot(velocity) < 0.0D) {
                continue;
            }
            // Embedded/moving-away arrows never need inventory/faction work.
            if (arrow.getOwner() instanceof LivingEntity shooter
                    && HumanRelations.allied(human, shooter)) continue;
            UUID projectileId = arrow.getUUID();
            Boolean shouldBlock = arrowDefenseDecisions.get(projectileId);
            // A recorded miss cannot turn into a block on a later tick. Avoid
            // tracing its trajectory repeatedly while it remains nearby.
            if (Boolean.FALSE.equals(shouldBlock)) continue;
            double ticksToImpact = ProjectileShieldPolicy.impactTicks(arrow.position(), velocity,
                    bounds, human.getDeltaMovement(), arrow.isInWater() ? 0.6D : 0.99D,
                    arrow.isNoGravity() ? 0.0D : 0.05D);
            if (!Double.isFinite(ticksToImpact) || ticksToImpact >= earliestImpact) continue;
            if (dedicatedRanged && !ProjectileShieldPolicy.shouldGuardRangedUser(ticksToImpact)) {
                continue;
            }
            if (deferRangedGuard == null) {
                deferRangedGuard = shouldDeferRangedProjectileGuard();
            }
            if (deferRangedGuard) {
                return null;
            }
            Vec3 impact = bounds.getCenter().add(human.getDeltaMovement().scale(ticksToImpact));
            if (human.level().clip(new ClipContext(arrow.position(), impact,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType()
                    != HitResult.Type.MISS) {
                continue;
            }
            if (shouldBlock == null) {
                shouldBlock = human.getRandom().nextFloat() < projectileDefenseChance();
                arrowDefenseDecisions.put(projectileId, shouldBlock);
                if (arrowDefenseDecisions.size() > 128) {
                    arrowDefenseDecisions.remove(arrowDefenseDecisions.keySet().iterator().next());
                }
            }
            if (!shouldBlock) {
                continue;
            }
            closest = new IncomingArrow(arrow,
                    arrow.getOwner() instanceof LivingEntity shooter ? shooter : null,
                    ticksToImpact);
            earliestImpact = ticksToImpact;
        }
        return closest;
    }

    private float projectileDefenseChance() {
        if (TierThreeHuman.isTierThree(human)) {
            return 0.80F;
        }
        return switch (human.getTier()) {
            case ROAMER -> 0.40F;
            case LEVEL1 -> 0.55F;
            case LEVEL2 -> 0.70F;
        };
    }

    private boolean isCloseMeleeEngagement() {
        if (!HumanUtil.isMeleeWeapon(human.getMainHandItem())) {
            return false;
        }
        LivingEntity current = human.getTarget();
        LivingEntity attacker = human.getLastHurtByMob();
        return (isValidThreat(current) && human.distanceToSqr(current) <= 36.0D)
                || (isValidThreat(attacker) && human.distanceToSqr(attacker) <= 36.0D);
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
        return !isHoldingTaczGun()
                || SpartanEquipmentCompat.isShield(human.getOffhandItem());
    }

    private boolean shouldDeferRangedProjectileGuard() {
        if (!isDedicatedRangedCombatant()) {
            return false;
        }
        if (human.isFleeing
                || (AmmoWeaponSelection.hasUsableGun(human) && GunSupport.get().isReloading(human))
                || (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem()))) {
            return true;
        }
        LivingEntity target = human.getTarget();
        double dangerRange = isCrossbowCombatant() ? 16.0D : 12.0D;
        if (GunSupport.get().isGun(human.getMainHandItem())) {
            dangerRange = Math.min(12.0D, GunRangePolicy.forType(
                    GunSupport.get().gunType(human.getMainHandItem())).minimum());
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
        if (human.humanGunner$hasPendingMeleeAttack()) return;
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
        return human.canAttack(entity);
    }

    private boolean isRangedCombat() {
        if (AmmoWeaponSelection.usableGun(human, human.getMainHandItem())) {
            return true;
        }
        // Tridents and bow/crossbow users now own their distance transitions.
        // Do not stack this higher-priority random FLANK layer on top: the
        // native bow goal already performs measured backward strafing, while
        // custody switches to melee at five blocks and back at six.
        return (human.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                && !RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem()))
                || (human.getOffhandItem().getItem() instanceof ProjectileWeaponItem
                && !RangedWeaponCustody.isBowOrCrossbow(human.getOffhandItem()));
    }

    private boolean isHoldingTaczGun() {
        return AmmoWeaponSelection.usableGun(human, human.getMainHandItem())
                || AmmoWeaponSelection.usableGun(human, human.getOffhandItem());
    }

    private boolean isGunThreat(LivingEntity target) {
        return club.someoneice.humangunner.GunSupport.get().isGun(target.getMainHandItem())
                || club.someoneice.humangunner.GunSupport.get().isGun(target.getOffhandItem());
    }

    private boolean isRangedThreat(LivingEntity target) {
        // A stowed ranged weapon in the off-hand does not make a sword user a
        // ranged attacker while the active main hand is clearly melee.
        if (HumanUtil.isMeleeWeapon(target.getMainHandItem())) {
            return false;
        }
        if (isGunThreat(target)) {
            return true;
        }
        return target.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || target.getOffhandItem().getItem() instanceof ProjectileWeaponItem
                || target.getMainHandItem().getItem() instanceof TridentItem
                || target.getOffhandItem().getItem() instanceof TridentItem;
    }

    private double healthRatio() { return human.getHealth() / Math.max(1.0F, human.getMaxHealth()); }

    /** Bodyguard interception is a request to the same shield owner, without assigning aggro. */
    void tickBodyguard(ServerPlayer player, LivingEntity threat) {
        if (threat == null) { endBodyguard(); return; }
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
        if (human.level().isClientSide || human.isUsingItem()) return;
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

    public void requestRecoveryReblock() {
        if (human.getTarget() != null && hasShieldInHands())
            human.getPersistentData().putInt(REBLOCK_UNTIL, human.tickCount + 80);
    }

    private boolean consumeRecoveryReblockRequest() {
        int until = human.getPersistentData().getInt(REBLOCK_UNTIL);
        if (until < human.tickCount) {
            human.getPersistentData().remove(REBLOCK_UNTIL);
            return false;
        }
        if (until == 0) return false;
        human.getPersistentData().remove(REBLOCK_UNTIL);
        return true;
    }
}
