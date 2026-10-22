package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;

/**
 * Treats a trident as a true hybrid weapon. Hostile Humans' original ranged
 * goal refuses to run inside 2.5 blocks, while the normal melee goal excludes
 * tridents, leaving the mob with no attack at close range. It also starts the
 * trident's item-use state even though the actual throw is performed directly,
 * which can leave the NPC visually charging forever when another goal wins.
 */
public final class TridentHybridGoal extends Goal {
    private static final double ENTER_MELEE_DISTANCE_SQR = 6.0D * 6.0D;
    private static final double MAX_THROW_DISTANCE_SQR = 16.0D * 16.0D;

    private final Human human;
    private LivingEntity target;
    private int failedSeparationTicks;
    private double separationDistanceSqr;
    private int attackCooldown;
    private int nextThrowTick;
    private int unseenTicks;
    private int nextFiringPositionTick;

    public TridentHybridGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = human.getTarget();
        return isHoldingTrident()
                && isValidTarget(candidate)
                && !human.isFleeing;
    }

    @Override
    public boolean canContinueToUse() {
        return isHoldingTrident()
                && isValidTarget(human.getTarget())
                && !human.isFleeing;
    }

    @Override
    public void start() {
        target = human.getTarget();
        failedSeparationTicks = 0;
        separationDistanceSqr = target == null ? 0 : human.distanceToSqr(target);
        attackCooldown = 0;
        unseenTicks = 0;
        nextFiringPositionTick = human.tickCount;
        clearStuckUseState();
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        RangedStrafeSpeed.setTrident(human, false);
        human.clearRangedStrafeMotion();
        RangedFiringPosition.cancelVisibleSearch(human);
        if (human.isCombatFiringShore()) {
            human.stopSeekingShore();
        }
        if (!CombatTerrainNavigation.ownsRoute(human)) human.getNavigation().stop();
        clearStuckUseState();
        human.setAggressive(false);
        target = null;
        // Deliberately retain Human#getTarget. The base melee goal clears it
        // during a distance-mode handoff, which is one cause of stalled throws.
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public EnumSet<Flag> getFlags() {
        return isShoreSeeking() && !human.isCombatFiringShore()
                ? EnumSet.of(Flag.LOOK)
                : EnumSet.of(Flag.MOVE, Flag.LOOK);
    }

    @Override
    public void tick() {
        if (human.isFleeing) return;
        target = human.getTarget();
        if (!isValidTarget(target)) {
            return;
        }
        human.updateCombatFiringShore(target, 1.0D);

        clearStuckUseState();
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        double distanceSqr = human.distanceToSqr(target);
        boolean visible = human.getSensing().hasLineOfSight(target);
        unseenTicks = visible ? 0 : unseenTicks + 1;
        human.getLookControl().setLookAt(target, 45.0F, 35.0F);

        // The weapon remains in ranged mode; close contact is a native melee fallback.
        RangedStrafeSpeed.setTrident(human, !isShoreSeeking() && visible && distanceSqr <= MAX_THROW_DISTANCE_SQR);
        if (distanceSqr < ENTER_MELEE_DISTANCE_SQR && !isShoreSeeking()
                && !SoldierOrder.isHoldingPosition(human)) {
            human.getNavigation().stop();
            boolean retreat = RangedTerrainCombat.strafeFacingTarget(human, target, -.8F,
                    ((human.tickCount / 40) & 1) == 0 ? .45F : -.45F);
            if (distanceSqr > separationDistanceSqr + .25) {
                failedSeparationTicks = 0; separationDistanceSqr = distanceSqr;
            } else failedSeparationTicks++;
            if (!retreat || failedSeparationTicks >= 10) attackMeleeIfReady(distanceSqr);
            throwTridentIfReady(distanceSqr, visible);
            return;
        }
        failedSeparationTicks = 0; separationDistanceSqr = distanceSqr;
        if (distanceSqr < ENTER_MELEE_DISTANCE_SQR) attackMeleeIfReady(distanceSqr);
        tickRanged(distanceSqr, visible, isShoreSeeking());
    }

    private void attackMeleeIfReady(double distanceSqr) {
        if (!human.isFleeing && human.humanGunner$canMeleeAttack()
                && distanceSqr <= meleeReachSqr(target) && attackCooldown <= 0
                && MeleeCombatRange.canStartAttack(human, target)) {
            human.doHurtTarget(target);
            attackCooldown = MeleeAttackTiming.nextCooldown(human);
        }
    }

    private void tickRanged(double distanceSqr, boolean visible, boolean shoreSeeking) {
        if (visible && distanceSqr <= MAX_THROW_DISTANCE_SQR) {
            human.markRangedFacing(target);
        }
        if (shoreSeeking) {
            throwTridentIfReady(distanceSqr, visible);
            return;
        }
        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
            throwTridentIfReady(distanceSqr, visible);
            return;
        }
        boolean waterApproach = human.shouldUseWaterMovement()
                && distanceSqr > MAX_THROW_DISTANCE_SQR;
        if (CombatTerrainNavigation.update(human, target, 1.0D,
                visible && distanceSqr <= MAX_THROW_DISTANCE_SQR).ownsMovement()) {
            throwTridentIfReady(distanceSqr, visible);
            return;
        }
        if (!visible && human.isInWater() && distanceSqr <= MAX_THROW_DISTANCE_SQR) {
            if ((human.getNavigation().isDone() || human.getNavigation().isStuck())
                    && human.tickCount >= nextFiringPositionTick) {
                Path firingPath = RangedFiringPosition.findVisiblePath(
                        human, target, 6.0D, 16.0D, 10, 12);
                if (firingPath != null) {
                    if (human.getNavigation().moveTo(firingPath, 1.0D))
                        SoldierDialogue.event(human, "reposition");
                } else if (!RangedFiringPosition.hasPendingVisibleSearch(human)) {
                    human.beginCombatFiringShore(target, 1.0D);
                }
                nextFiringPositionTick = human.tickCount + RangedFiringPosition.retryDelay(human, firingPath, 40);
            }
            return;
        }
        if (!visible || distanceSqr > MAX_THROW_DISTANCE_SQR || waterApproach) {
            PursuitNavigation.update(human, target, 1.0D);
            human.approachCombatTargetInWater(target, waterApproach ? 16.0D : 6.0D, 1.0D);
            throwTridentIfReady(distanceSqr, visible);
            return;
        }

        human.getNavigation().stop();
        RangedTerrainCombat.strafeFacingTarget(human, target, 0,
                ((human.tickCount / 40) & 1) == 0 ? .8F : -.8F);
        throwTridentIfReady(distanceSqr, visible);
    }

    private void throwTridentIfReady(double distanceSqr, boolean visible) {
        if (human.isFleeing || human.humanGunner$shieldDefense().reservesRangedUse()
                || human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem())
                || !visible || unseenTicks != 0 || attackCooldown > 0
                || human.tickCount < nextThrowTick
                || distanceSqr > MAX_THROW_DISTANCE_SQR
                || !RangedShotClearance.canShoot(human, target)) {
            return;
        }
        float distanceFactor = Mth.clamp(
                (float) (Math.sqrt(distanceSqr) / 16.0D), 0.1F, 1.0F
        );
        human.performRangedAttackTrident(target, distanceFactor);
        attackCooldown = 14;
        nextThrowTick = human.tickCount + 20;
    }

    private double meleeReachSqr(LivingEntity victim) {
        return MeleeCombatRange.reachSqr(human, victim);
    }

    private boolean isHoldingTrident() {
        return human.getMainHandItem().getItem() instanceof TridentItem;
    }

    private boolean isShoreSeeking() {
        return ShoreSeekingPolicy.isShoreTransitionActive(
                human.isSeekingShore(), human.isShoreTransitionPending());
    }

    private boolean isValidTarget(LivingEntity candidate) {
        return candidate != null
                && candidate.isAlive()
                && human.canAttack(candidate)
                && human.distanceToSqr(candidate) <= 64.0D * 64.0D;
    }

    private void clearStuckUseState() {
        if (human.isUsingItem() && human.getUseItem().getItem() instanceof TridentItem) {
            human.stopUsingItem();
        }
    }
}
