package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.HumanMobEntityData;
import com.craftix.hostile_humans.entity.ai.goal.HumanGoal;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.MeleeAttackTiming;
import club.someoneice.humangunner.SoldierOrder;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;

public class MeleeAttackGoal
extends HumanGoal {
    private static final long COOLDOWN_BETWEEN_CAN_USE_CHECKS = 5L;
    private final double speedModifier;
    private final boolean followingTargetEvenIfNotSeen;
    private final boolean canPenalize = false;
    private Path path;
    private double pathedTargetX;
    private double pathedTargetY;
    private double pathedTargetZ;
    private int ticksUntilNextPathRecalculation;
    private int ticksUntilNextAttack;
    private long lastCanUseCheck;
    private int failedPathFindingPenalty = 0;

    public MeleeAttackGoal(HumanEntity humanEntity, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(humanEntity);
        this.speedModifier = speedModifier;
        this.followingTargetEvenIfNotSeen = followingTargetEvenIfNotSeen;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        long gameTime;
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            if (human.isFleeing) {
                return false;
            }
        }
        if ((gameTime = this.mob.level().getGameTime()) - this.lastCanUseCheck < 5L) {
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
        this.path = this.mob.getNavigation().createPath((Entity)livingEntity, 0);
        if (this.path != null) {
            return true;
        }
        return this.getAttackReachSqr(livingEntity) >= this.mob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
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
        boolean isSit = this.mob.isOrderedToSit();
        if (!isSit && (!(this.mob instanceof Human human)
                || !SoldierOrder.isHoldingPosition(human))) {
            this.mob.getNavigation().moveTo(this.path, this.speedModifier);
        }
        this.mob.setAggressive(true);
        this.ticksUntilNextPathRecalculation = 0;
        this.ticksUntilNextAttack = 0;
    }

    public void stop() {
        this.mob.setTarget(null);
        this.mob.setAggressive(false);
        boolean isSit = this.mob.isOrderedToSit();
        if (isSit) {
            this.mob.setOrderedToPosition((BlockPos)this.mob.getEntityData().get(HumanMobEntityData.DATA_SIT_POS));
        } else {
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
                this.stop();
                return;
            }
            this.mob.getLookControl().setLookAt((Entity)livingEntity, 30.0f, 30.0f);
            double distance = this.mob.distanceToSqr(livingEntity.getX(), livingEntity.getY(), livingEntity.getZ());
            this.ticksUntilNextPathRecalculation = Math.max(this.ticksUntilNextPathRecalculation - 1, 0);
            boolean holdingPosition = this.mob instanceof Human human
                    && SoldierOrder.isHoldingPosition(human);
            if (!holdingPosition && (this.followingTargetEvenIfNotSeen || this.mob.getSensing().hasLineOfSight((Entity)livingEntity)) && this.ticksUntilNextPathRecalculation <= 0 && (this.pathedTargetX == 0.0 && this.pathedTargetY == 0.0 && this.pathedTargetZ == 0.0 || livingEntity.distanceToSqr(this.pathedTargetX, this.pathedTargetY, this.pathedTargetZ) >= 1.0 || this.mob.getRandom().nextFloat() < 0.05f)) {
                this.pathedTargetX = livingEntity.getX();
                this.pathedTargetY = livingEntity.getY();
                this.pathedTargetZ = livingEntity.getZ();
                this.ticksUntilNextPathRecalculation = 4 + this.mob.getRandom().nextInt(7);
                Objects.requireNonNull(this);
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
            this.ticksUntilNextAttack = Math.max(this.ticksUntilNextAttack - 1, 0);
            this.checkAndPerformAttack(livingEntity, distance);
        }
    }

    protected void checkAndPerformAttack(LivingEntity livingEntity, double attackDistance) {
        double distance = this.getAttackReachSqr(livingEntity);
        if (attackDistance <= distance && this.ticksUntilNextAttack <= 0) {
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
        if (target == null || !target.isAlive() || !this.mob.canAttack(target)
                || this.mob.distanceToSqr(target) > this.getAttackReachSqr(target)) {
            return false;
        }
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
        HumanEntity humanEntity = this.mob;
        if (humanEntity instanceof Human) {
            Human human = (Human)humanEntity;
            // The former flurry path inserted one-tick melee intervals. Clear
            // old transient state so every real close-range hit now observes
            // the configured 16-28 tick cadence.
            human.meleeFlurryHitsRemaining = 0;
            human.meleeFlurryDamageTicks = 0;
        }
        this.ticksUntilNextAttack = this.adjustedTickDelay(
                this.mob instanceof Human human
                        ? MeleeAttackTiming.nextCooldown(human)
                        : MeleeAttackTiming.nextCooldown(this.mob.getRandom())
        );
    }

    protected double getAttackReachSqr(LivingEntity livingEntity) {
        return this.mob.getBbWidth() * 3.0f * this.mob.getBbWidth() * 3.0f + livingEntity.getBbWidth();
    }
}

