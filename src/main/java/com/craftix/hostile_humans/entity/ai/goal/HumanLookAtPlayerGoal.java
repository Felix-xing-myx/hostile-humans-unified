package com.craftix.hostile_humans.entity.ai.goal;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

public class HumanLookAtPlayerGoal
extends Goal {
    public static final float DEFAULT_PROBABILITY = 0.02f;
    protected final Mob mob;
    protected final float lookDistance;
    protected final float probability;
    protected final Class<? extends LivingEntity> lookAtType;
    protected final TargetingConditions lookAtContext;
    private final boolean onlyHorizontal;
    @Nullable
    protected Entity lookAt;
    private int lookTime;

    public HumanLookAtPlayerGoal(Mob p_25520_, Class<? extends LivingEntity> p_25521_, float p_25522_) {
        this(p_25520_, p_25521_, p_25522_, 0.8f);
    }

    public HumanLookAtPlayerGoal(Mob p_25524_, Class<? extends LivingEntity> p_25525_, float p_25526_, float p_25527_) {
        this(p_25524_, p_25525_, p_25526_, p_25527_, false);
    }

    public HumanLookAtPlayerGoal(Mob p_148118_, Class<? extends LivingEntity> p_148119_, float p_148120_, float p_148121_, boolean p_148122_) {
        this.mob = p_148118_;
        this.lookAtType = p_148119_;
        this.lookDistance = p_148120_;
        this.probability = p_148121_;
        this.onlyHorizontal = p_148122_;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        this.lookAtContext = p_148119_ == Player.class ? TargetingConditions.forNonCombat().range((double)p_148120_).selector(p_25531_ -> EntitySelector.notRiding((Entity)p_148118_).test(p_25531_)) : TargetingConditions.forNonCombat().range((double)p_148120_);
    }

    public boolean canUse() {
        if (this.mob.getRandom().nextFloat() >= this.probability) {
            return false;
        }
        if (this.mob.getTarget() != null) {
            this.lookAt = this.mob.getTarget();
        }
        this.lookAt = this.lookAtType == Player.class ? this.mob.level().getNearestPlayer(this.lookAtContext, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ()) : this.mob.level().getNearestEntity(this.mob.level().getEntitiesOfClass(this.lookAtType, this.mob.getBoundingBox().inflate((double)this.lookDistance, 9.0, (double)this.lookDistance), p_148124_ -> true), this.lookAtContext, (LivingEntity)this.mob, this.mob.getX(), this.mob.getEyeY(), this.mob.getZ());
        return this.lookAt != null;
    }

    public boolean canContinueToUse() {
        if (!this.lookAt.isAlive()) {
            return false;
        }
        if (this.mob.distanceToSqr(this.lookAt) > (double)(this.lookDistance * this.lookDistance)) {
            return false;
        }
        return this.lookTime > 0;
    }

    public void start() {
        this.lookTime = this.adjustedTickDelay(40 + this.mob.getRandom().nextInt(40));
    }

    public void stop() {
        this.lookAt = null;
    }

    public void tick() {
        if (this.lookAt.isAlive()) {
            double d0 = this.onlyHorizontal ? this.mob.getEyeY() : this.lookAt.getEyeY();
            this.mob.getLookControl().setLookAt(this.lookAt.getX(), d0, this.lookAt.getZ());
            --this.lookTime;
        }
    }
}

