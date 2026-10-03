package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.MeleeAttackTiming;
import club.someoneice.humangunner.MeleeCombatRange;
import club.someoneice.humangunner.MeleeSpacing;
import club.someoneice.humangunner.SoldierOrder;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

public class MeleeAttackGoal
extends Goal {
    protected final HumanEntity mob;
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 5L;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private int recoveryRouteUntilTick;
    private int recoveryTargetId;
    private final dev.felix.hostilehumans.core.MeleePursuitProgress pursuitProgress =
            new dev.felix.hostilehumans.core.MeleePursuitProgress();

    public MeleeAttackGoal(HumanEntity humanEntity, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.mob = humanEntity;
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob instanceof Human human && human.getMainHandItem().isEmpty()
                && human.getTarget() != null) {
            club.someoneice.humangunner.HumanLootManager.equipMeleeFallback(human);
        }
        long gameTime;
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            if (human.isFleeing) {
                return false;
            }
        }
        if ((gameTime = this.mob.level().getGameTime()) - this.lastCanUseCheck < COOLDOWN_BETWEEN_CAN_USE_CHECKS) {
            return false;
        }
        this.lastCanUseCheck = gameTime;
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity == null || !livingEntity.isAlive()
                || !this.mob.canAttack(livingEntity)) {
            return false;
        }
        if (this.mob instanceof Human human && SoldierOrder.isHoldingPosition(human)) {
            return true;
        }
        if (canStrike(livingEntity)) {
            // Starting an in-reach attack does not need a pursuit path.
            this.path = null;
            return true;
        }
        this.path = this.mob.getNavigation().createPath((Entity)livingEntity, 0);
        if (this.path != null) {
            return true;
        }
        if (this.mob instanceof Human human && human.shouldUseWaterMovement()) {
            // A water navigator may not produce a complete path to a target
            // on the opposite bank. Keep the combat goal active so its direct
            // water fallback can continue the crossing.
            return true;
        }
        // Keep the goal alive to retry/recover an unreachable or partial path.
        // Otherwise a mutually blocked pair never runs its movement watchdog.
        return this.mob instanceof Human;
    }

    public boolean canContinueToUse() {
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            if (human.isFleeing) {
                return false;
            }
        }
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity == null || !livingEntity.isAlive()
                || !this.mob.canAttack(livingEntity)) {
            return false;
        }
        if (!this.followingTargetEvenIfNotSeen) {
            return !this.mob.getNavigation().isDone();
        }
        return this.mob.isWithinRestriction(livingEntity.blockPosition());
    }

    public void start() {
        this.recoveryRouteUntilTick = 0;
        boolean canPursue = !this.mob.isOrderedToSit();
        if (this.mob instanceof Human human) {
            canPursue &= !SoldierOrder.isHoldingPosition(human);
            LivingEntity target = this.mob.getTarget();
            if (target != null) {
                canPursue = canPursue && (!MeleeCombatRange.canStrike(human, target)
                        || this.mob.distanceTo(target)
                        > MeleeSpacing.preferredDistance(MeleeCombatRange.reach(human, target)));
            }
        }
        if (canPursue) {
            if (this.path != null) this.mob.getNavigation().moveTo(this.path, this.speedModifier);
            else this.mob.getNavigation().stop();
        }
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    public void stop() {
        // Target ownership belongs to targeting/order logic, not the movement
        // goal. Defense, a weapon handoff or a failed route may interrupt us.
        LivingEntity target = this.mob.getTarget();
        if (target != null && (!target.isAlive() || !this.mob.canAttack(target))) this.mob.setTarget(null);
        this.mob.setAggressive(false);
        // A defensive interruption is not a failed target/path acquisition.
        // Let melee resume at the next selector pass instead of idling through
        // the expensive-acquisition throttle after every received hit.
        this.lastCanUseCheck = this.mob.level().getGameTime() - COOLDOWN_BETWEEN_CAN_USE_CHECKS;
        boolean isSit = this.mob.isOrderedToSit();
        if (isSit) {
            this.mob.setOrderedToPosition((BlockPos)this.mob.getEntityData().get(HumanMobEntityData.DATA_SIT_POS));
        } else if (target == null || !target.isAlive()) {
            this.mob.getMoveControl().setWantedPosition(this.mob.position().x(), this.mob.position().y(), this.mob.position().z(), 1.0);
        }
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        LivingEntity livingEntity = this.mob.getTarget();
        if (livingEntity != null) {
            Player player;
            ItemStack itemStack;
            if (livingEntity instanceof Player && ((itemStack = (player = (Player)livingEntity).getItemInHand(player.getUsedItemHand())).is(this.mob.getTameItem()) || this.mob.isOwnedBy((LivingEntity)player))) {
                this.mob.setTarget(null);
                this.stop();
                return;
            }
            this.mob.getLookControl().setLookAt((Entity)livingEntity, 30.0f, 30.0f);
            double distance = this.mob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
            if (this.mob instanceof Human human && distance <= this.getAttackReachSqr(livingEntity) + 4.0D)
                MeleeCombatRange.faceTarget(human, livingEntity);
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            boolean holdingPosition = this.mob instanceof Human human
                    && SoldierOrder.isHoldingPosition(human);
            boolean spacing = !holdingPosition && this.mob instanceof Human human
                    && MeleeSpacing.control(human, livingEntity);
            boolean stalled = pursuitProgress.stalled(this.mob.tickCount, livingEntity.getId(),
                    this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    !holdingPosition && !spacing && !canStrike(livingEntity)
                            && !this.mob.isUsingItem());
            if (stalled) {
                recoverPursuit(livingEntity);
                this.ticksUntilNextPathRecalculation = 8;
            }
            if (spacing && this.ticksUntilNextPathRecalculation <= 0) {
                this.ticksUntilNextPathRecalculation = 8;
            }
            if (!holdingPosition && !spacing
                    && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight((Entity)livingEntity))
                    && this.ticksUntilNextPathRecalculation <= 0
                    && (this.mob.tickCount >= recoveryRouteUntilTick
                    || livingEntity.getId() != recoveryTargetId || this.mob.getNavigation().isDone())
                    && (this.mob.getNavigation().isDone()
                    || this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0
                    || livingEntity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0
                    || this.mob.getRandom().nextFloat() < 0.05f)) {
                this.pathedTargetX = livingEntity.getX();
                this.pathedTargetY = livingEntity.getY();
                this.pathedTargetZ = livingEntity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);
                if (distance > 1024.0) {
                    this.ticksUntilNextPathRecalculation += 10;
                } else if (distance > 256.0) {
                    this.ticksUntilNextPathRecalculation += 5;
                }
                if (!this.mob.getNavigation().moveTo((Entity)livingEntity, this.speedModifier)) {
                    this.ticksUntilNextPathRecalculation += 15;
                }
                this.ticksUntilNextPathRecalculation = this.adjustedTickDelay(this.ticksUntilNextPathRecalculation);
            }
            if (!holdingPosition && this.mob instanceof Human human) {
                human.approachCombatTargetInWater(livingEntity,
                        Math.sqrt(this.getAttackReachSqr(livingEntity)), this.speedModifier);
            }
            this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
            this.checkAndPerformAttack(livingEntity);
        }
    }

    protected void checkAndPerformAttack(LivingEntity livingEntity) {
        if (canStrike(livingEntity) && this.ticksUntilNextAttack <= 0) {
            HumanEntity humanEntity;
            this.resetAttackCooldown();
            if (this.mob.isBlocking()) {
                this.mob.stopUsingItem();
            }
            if ((humanEntity = this.mob) instanceof Human) {
                Human human = (Human)humanEntity;
                human.lastCombatTime = human.tickCount;
            }
            this.mob.doHurtTarget((Entity)livingEntity);
        }
    }

    /** Opens one guaranteed attack window after an adaptive shield block. */
    public boolean humanGunner$tryImmediateCounterattack(LivingEntity target) {
        if (target == null || !target.isAlive() || !this.mob.canAttack(target)) {
            return false;
        }
        if (this.mob instanceof Human human) MeleeCombatRange.faceTarget(human, target);
        if (!canStrike(target)) return false;
        if (this.mob.isBlocking()) {
            this.mob.stopUsingItem();
        }
        this.resetAttackCooldown();
        if (this.mob instanceof Human human) {
            human.lastCombatTime = human.tickCount;
        }
        this.mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return this.mob.doHurtTarget(target);
    }

    protected void resetAttackCooldown() {
        this.ticksUntilNextAttack = this.adjustedTickDelay(
                this.mob instanceof Human human
                        ? MeleeAttackTiming.nextCooldown(human)
                        : MeleeAttackTiming.nextCooldown(this.mob.getRandom())
        );
    }

    protected double getAttackReachSqr(LivingEntity livingEntity) {
        if (this.mob instanceof Human human) return MeleeCombatRange.reachSqr(human, livingEntity);
        return this.mob.getBbWidth() * 3.0f * this.mob.getBbWidth() * 3.0f + livingEntity.getBbWidth();
    }

    private boolean canStrike(LivingEntity target) {
        return this.mob instanceof Human human ? MeleeCombatRange.canStrike(human, target)
                : this.mob.distanceToSqr(target) <= getAttackReachSqr(target)
                && this.mob.getSensing().hasLineOfSight(target);
    }

    private void recoverPursuit(LivingEntity target) {
        // One bounded flank path attempt per stall, not a scan or forced push.
        Path oldPath = this.mob.getNavigation().getPath();
        if (oldPath != null && !oldPath.isDone()
                && !oldPath.getNextNodePos().equals(this.mob.blockPosition())
                && this.mob.getNavigation() instanceof com.craftix.hostile_humans.entity.ai.HumanNavigation ground)
            ground.avoidWaypoint(oldPath.getNextNodePos(), this.mob.level().getGameTime() + 60L);
        Vec3 candidate = DefaultRandomPos.getPosTowards(this.mob, 6, 3, target.position(), Math.PI / 2.0D);
        if (candidate != null && this.mob.getNavigation().moveTo(candidate.x, candidate.y, candidate.z, this.speedModifier)) {
            recoveryRouteUntilTick = this.mob.tickCount + 30;
            recoveryTargetId = target.getId();
            return;
        }
        this.mob.getNavigation().stop();
        if (this.mob.onGround() && this.mob.horizontalCollision && oldPath != null && !oldPath.isDone()
                && oldPath.getNextNodePos().getY() > this.mob.getY()
                && oldPath.getNextNodePos().getY() <= this.mob.getY() + 1.0D)
            this.mob.getJumpControl().jump();
    }
}

