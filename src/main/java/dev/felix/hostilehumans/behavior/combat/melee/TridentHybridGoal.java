package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedShotClearance;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedStrafeSpeed;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.navigation.ShoreSeekingPolicy;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.TridentItem;

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
    private final TridentMovementExecution movement;
    private int attackCooldown;
    private int nextThrowTick;
    private int unseenTicks;

    public TridentHybridGoal(Human human) {
        this.human = human;
        this.movement = new TridentMovementExecution(human);
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
        movement.start(target);
        attackCooldown = 0;
        unseenTicks = 0;
        clearStuckUseState();
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        movement.stop();
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

        boolean keepDistance = HumanCombatDirector.keepsRangedDistance(human, human.getMainHandItem());
        // Other factions retain the hybrid melee fallback; ranged-distance
        // strategy retries safe separation and throwing even when backed up.
        RangedStrafeSpeed.setTrident(human, !isShoreSeeking() && visible && distanceSqr <= MAX_THROW_DISTANCE_SQR);
        if (distanceSqr < ENTER_MELEE_DISTANCE_SQR && !isShoreSeeking()
                && !SoldierOrder.isHoldingPosition(human)) {
            boolean separationFailed = movement.separate(target, distanceSqr);
            if (separationFailed && !keepDistance) attackMeleeIfReady(distanceSqr);
            throwTridentIfReady(distanceSqr, visible);
            return;
        }
        movement.resetSeparation(distanceSqr);
        if (!keepDistance && distanceSqr < ENTER_MELEE_DISTANCE_SQR) attackMeleeIfReady(distanceSqr);
        tickRanged(distanceSqr, visible, isShoreSeeking());
    }

    private void attackMeleeIfReady(double distanceSqr) {
        if (!human.isFleeing && human.hhu$canMeleeAttack()
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
        if (movement.ranged(target, distanceSqr, visible, shoreSeeking)) throwTridentIfReady(distanceSqr, visible);
    }

    private void throwTridentIfReady(double distanceSqr, boolean visible) {
        if (human.isFleeing || human.hhu$shieldDefense().reservesRangedUse()
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
