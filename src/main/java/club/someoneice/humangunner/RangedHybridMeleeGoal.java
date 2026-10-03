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
        if (human.isFleeing) return false;
        if (RangedWeaponCustody.isActive(human) && human.getMainHandItem().isEmpty()
                && isValidTarget(human.getTarget())) HumanLootManager.equipMeleeFallback(human);
        return RangedWeaponCustody.isActive(human)
                && (human.getMainHandItem().isEmpty()
                || HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem()))
                && isValidTarget(human.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        if (human.getTarget() != null && human.distanceToSqr(human.getTarget()) <= 16)
            SoldierDialogue.event(human, "melee_switch");
        attackCooldown = 0;
        pathCooldown = 0;
        // A bow/crossbow orbit can have issued movement later in the same
        // tick. Hand the controller and speed back to melee before moving.
        RangedStrafeSpeed.clear(human);
        human.clearRangedStrafeMotion();
        human.getNavigation().stop();
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        if (!human.isFleeing) human.getNavigation().stop();
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
                // A failed route is not a reason to keep the old ranged
                // strafing movement active while the melee weapon is held.
                human.getNavigation().stop();
            }
            pathCooldown = 3 + human.getRandom().nextInt(3);
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
        return MeleeCombatRange.reachSqr(human, target);
    }
}
