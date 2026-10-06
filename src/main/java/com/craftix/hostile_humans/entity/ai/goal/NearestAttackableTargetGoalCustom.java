package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.HumanUtil;
import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public class NearestAttackableTargetGoalCustom<T extends LivingEntity>
extends TargetGoal {
    protected final Class<T> targetType;
    protected final int randomInterval;
    @Nullable
    protected LivingEntity target;
    protected TargetingConditions targetConditions;
    private boolean reacquireAfterDeath;

    public NearestAttackableTargetGoalCustom(Mob p_26053_, Class<T> p_26054_, int p_26055_, boolean p_26056_, boolean p_26057_, @Nullable Predicate<LivingEntity> livingEntityPredicate) {
        super(p_26053_, p_26056_, p_26057_);
        this.targetType = p_26054_;
        this.randomInterval = NearestAttackableTargetGoalCustom.reducedTickDelay((int)p_26055_);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        this.targetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(livingEntityPredicate);
    }

    public boolean canUse() {
        if (this.mob instanceof com.craftix.hostile_humans.entity.entities.Human lowHuman
                ? club.someoneice.humangunner.RetreatRecoveryPolicy.shouldForceRetreat(lowHuman) || lowHuman.isFleeing
                : HumanUtil.isLowHp((LivingEntity)this.mob)) {
            return false;
        }
        boolean fastRetarget = this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human
                && human.humanGunner$shouldFastGunRetarget();
        if (!reacquireAfterDeath && !fastRetarget && this.randomInterval > 0
                && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        }
        reacquireAfterDeath = false;
        this.findTarget();
        return this.target != null;
    }

    @Override public boolean canContinueToUse() {
        LivingEntity current = this.mob.getTarget() != null ? this.mob.getTarget() : this.target;
        if (current != null && (!current.isAlive() || current.isRemoved() || current.level() != this.mob.level())) {
            reacquireAfterDeath = club.someoneice.humangunner.GunSupport.isGunItem(this.mob.getMainHandItem());
            return false;
        }
        return super.canContinueToUse();
    }

    protected AABB getTargetSearchArea(double p_26069_) {
        return this.mob.getBoundingBox().inflate(p_26069_, 4.0, p_26069_);
    }

    protected void findTarget() {
        // Follow range changes when a human switches between guns and melee.
        // Update the range while reusing the immutable selector and flags.
        this.targetConditions.range(this.getFollowDistance());
        this.target = this.targetType != Player.class && this.targetType != ServerPlayer.class ? this.mob.level().getNearestEntity(this.mob.level().getEntitiesOfClass(this.targetType, this.getTargetSearchArea(this.getFollowDistance()), entity -> true), this.targetConditions, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ()) : this.mob.level().getNearestPlayer(this.targetConditions, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
    }

    public void start() {
        this.mob.setTarget(this.target);
        super.start();
    }
}

