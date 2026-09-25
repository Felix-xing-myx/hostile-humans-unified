package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Close-range half of bow/crossbow hybrid combat without target-clearing stop logic. */
public final class RangedHybridMeleeGoal extends Goal {
    private final Human human;
    private int attackCooldown;
    private int pathCooldown;

    public RangedHybridMeleeGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return RangedWeaponCustody.isActive(human)
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                && isValidTarget(human.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        pathCooldown = 0;
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        human.getNavigation().stop();
        human.setAggressive(false);
        // Never clear Human#getTarget here: reaching the six-block return
        // threshold must hand the same target directly back to bow/crossbow AI.
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = human.getTarget();
        if (!isValidTarget(target)) {
            return;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        if (pathCooldown > 0) {
            pathCooldown--;
        }
        human.getLookControl().setLookAt(target, 45.0F, 35.0F);
        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
        } else if (pathCooldown <= 0) {
            double currentDistanceSqr = human.distanceToSqr(target);
            Vec3 away = DefaultRandomPos.getPosAway(human, 10, 5, target.position());
            if (away != null && target.distanceToSqr(away) > currentDistanceSqr + 4.0D
                    && human.getNavigation().moveTo(away.x, away.y, away.z, 1.0D)) {
                // Continue along a route which actually grows the gap.
            } else {
                human.getNavigation().stop();
                human.getMoveControl().strafe(-0.8F, 0.0F);
            }
            pathCooldown = 3 + human.getRandom().nextInt(3);
        } else if (human.getNavigation().isDone()) {
            // A failed/short path must not strand the melee lease at 4-5
            // blocks, below the six-block ranged-weapon return threshold.
            human.getMoveControl().strafe(-0.8F, 0.0F);
        }
        if (human.distanceToSqr(target) <= meleeReachSqr(target) && attackCooldown <= 0) {
            human.swing(InteractionHand.MAIN_HAND);
            human.doHurtTarget(target);
            attackCooldown = MeleeAttackTiming.nextCooldown(human);
        }
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && human.canAttack(target);
    }

    private double meleeReachSqr(LivingEntity target) {
        double width = human.getBbWidth() * 2.0D;
        return width * width + target.getBbWidth();
    }
}
