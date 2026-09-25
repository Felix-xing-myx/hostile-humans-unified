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
    @Nullable
    private final Predicate<LivingEntity> targetPredicate;

    public NearestAttackableTargetGoalCustom(Mob p_26053_, Class<T> p_26054_, int p_26055_, boolean p_26056_, boolean p_26057_, @Nullable Predicate<LivingEntity> livingEntityPredicate) {
        super(p_26053_, p_26056_, p_26057_);
        this.targetType = p_26054_;
        this.randomInterval = NearestAttackableTargetGoalCustom.reducedTickDelay((int)p_26055_);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        this.targetPredicate = livingEntityPredicate;
        this.targetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(livingEntityPredicate);
    }

    public boolean canUse() {
        if (HumanUtil.isLowHp((LivingEntity)this.mob)) {
            return false;
        }
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        }
        this.findTarget();
        return this.target != null;
    }

    protected AABB getTargetSearchArea(double p_26069_) {
        return this.mob.getBoundingBox().inflate(p_26069_, 4.0, p_26069_);
    }

    protected void findTarget() {
        // Follow range changes when a human switches between guns and melee.
        // Rebuild the range gate instead of keeping the constructor-time value.
        this.targetConditions = TargetingConditions.forCombat()
                .range(this.getFollowDistance()).selector(this.targetPredicate);
        this.target = this.targetType != Player.class && this.targetType != ServerPlayer.class ? this.mob.level().getNearestEntity(this.mob.level().getEntitiesOfClass(this.targetType, this.getTargetSearchArea(this.getFollowDistance()), entity -> true), this.targetConditions, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ()) : this.mob.level().getNearestPlayer(this.targetConditions, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
    }

    public void start() {
        this.mob.setTarget(this.target);
        super.start();
    }
}

