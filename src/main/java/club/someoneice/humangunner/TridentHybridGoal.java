package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
    private static final double ENTER_MELEE_DISTANCE_SQR = 3.25D * 3.25D;
    private static final double LEAVE_MELEE_DISTANCE_SQR = 4.25D * 4.25D;
    private static final double MAX_THROW_DISTANCE_SQR = 36.0D * 36.0D;

    private final Human human;
    private LivingEntity target;
    private boolean meleeMode;
    private int attackCooldown;
    private int pathCooldown;
    private int unseenTicks;

    public TridentHybridGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = human.getTarget();
        return isHoldingTrident()
                && isValidTarget(candidate)
                && (!human.isFleeing || human.distanceToSqr(candidate) <= ENTER_MELEE_DISTANCE_SQR);
    }

    @Override
    public boolean canContinueToUse() {
        return isHoldingTrident()
                && isValidTarget(human.getTarget())
                && (!human.isFleeing || human.distanceToSqr(human.getTarget()) <= ENTER_MELEE_DISTANCE_SQR);
    }

    @Override
    public void start() {
        target = human.getTarget();
        meleeMode = target != null && human.distanceToSqr(target) <= ENTER_MELEE_DISTANCE_SQR;
        attackCooldown = 0;
        pathCooldown = 0;
        unseenTicks = 0;
        clearStuckUseState();
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        human.getNavigation().stop();
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
    public void tick() {
        target = human.getTarget();
        if (!isValidTarget(target)) {
            return;
        }

        clearStuckUseState();
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (pathCooldown > 0) {
            pathCooldown--;
        }

        double distanceSqr = human.distanceToSqr(target);
        boolean visible = human.getSensing().hasLineOfSight(target);
        unseenTicks = visible ? 0 : unseenTicks + 1;
        human.getLookControl().setLookAt(target, 45.0F, 35.0F);

        // Separate enter/leave thresholds prevent rapid goal oscillation when
        // the target stands at the melee/throw boundary.
        if (meleeMode) {
            meleeMode = distanceSqr <= LEAVE_MELEE_DISTANCE_SQR;
        } else {
            meleeMode = distanceSqr <= ENTER_MELEE_DISTANCE_SQR;
        }

        if (meleeMode) {
            tickMelee(distanceSqr);
        } else {
            tickRanged(distanceSqr, visible);
        }
    }

    private void tickMelee(double distanceSqr) {
        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
        } else if (pathCooldown <= 0) {
            human.getNavigation().moveTo(target, 1.0D);
            pathCooldown = 4 + human.getRandom().nextInt(3);
        }
        if (distanceSqr <= meleeReachSqr(target) && attackCooldown <= 0) {
            human.swing(InteractionHand.MAIN_HAND);
            human.doHurtTarget(target);
            attackCooldown = MeleeAttackTiming.nextCooldown(human);
        }
    }

    private void tickRanged(double distanceSqr, boolean visible) {
        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
            if (visible && attackCooldown <= 0 && distanceSqr <= MAX_THROW_DISTANCE_SQR) {
                float distanceFactor = Mth.clamp(
                        (float) (Math.sqrt(distanceSqr) / 36.0D), 0.1F, 1.0F
                );
                human.performRangedAttackTrident(target, distanceFactor);
                attackCooldown = 14;
            }
            return;
        }
        if (!visible || distanceSqr > MAX_THROW_DISTANCE_SQR) {
            if (pathCooldown <= 0) {
                human.getNavigation().moveTo(target, 1.0D);
                pathCooldown = 5 + human.getRandom().nextInt(4);
            }
            return;
        }

        human.getNavigation().stop();
        if (unseenTicks == 0 && attackCooldown <= 0) {
            float distanceFactor = Mth.clamp(
                    (float) (Math.sqrt(distanceSqr) / 36.0D), 0.1F, 1.0F
            );
            human.performRangedAttackTrident(target, distanceFactor);
            attackCooldown = 14;
        }
    }

    private double meleeReachSqr(LivingEntity victim) {
        double width = human.getBbWidth() * 2.0D;
        return width * width + victim.getBbWidth();
    }

    private boolean isHoldingTrident() {
        return human.getMainHandItem().getItem() instanceof TridentItem;
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
