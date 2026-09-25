package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.PotionRangedAttackMob;
import club.someoneice.humangunner.PotionThrowing;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.SplashPotionItem;

public class PotionRangedAttackGoal
extends Goal {
    private final Mob mob;
    private final PotionRangedAttackMob rangedAttackMob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private final int attackIntervalMax;
    private final float attackRadius;
    private final float attackRadiusSqr;
    @Nullable
    private LivingEntity target;
    private int attackTime = -1;
    private int seeTime;

    public PotionRangedAttackGoal(PotionRangedAttackMob p_25768_, double p_25769_, int p_25770_, float p_25771_) {
        this(p_25768_, p_25769_, p_25770_, p_25770_, p_25771_);
    }

    public PotionRangedAttackGoal(PotionRangedAttackMob p_25773_, double p_25774_, int p_25775_, int p_25776_, float p_25777_) {
        if (!(p_25773_ instanceof LivingEntity)) {
            throw new IllegalArgumentException("AttackGoal requires Mob implements PotionRangedAttackMob");
        }
        this.rangedAttackMob = p_25773_;
        this.mob = (Mob)p_25773_;
        this.speedModifier = p_25774_;
        this.attackIntervalMin = p_25775_;
        this.attackIntervalMax = p_25776_;
        this.attackRadius = p_25777_;
        this.attackRadiusSqr = p_25777_ * p_25777_;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    public boolean canUse() {
        LivingEntity mobTarget = this.mob.getTarget();
        if (mobTarget != null && mobTarget.isAlive()
                && PotionThrowing.inRange(this.mob, mobTarget)
                && (this.mob.getMainHandItem().getItem() instanceof SplashPotionItem || this.mob.getOffhandItem().getItem() instanceof SplashPotionItem)) {
            this.target = mobTarget;
            return true;
        }
        return false;
    }

    public boolean canContinueToUse() {
        return this.canUse();
    }

    public void stop() {
        this.target = null;
        this.seeTime = 0;
        this.attackTime = -1;
    }

    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tick() {
        if (this.target == null) {
            return;
        }
        double distanceToSqr = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
        boolean hasLineOfSight = this.mob.getSensing().hasLineOfSight((Entity)this.target);
        this.seeTime = hasLineOfSight ? ++this.seeTime : 0;
        if (distanceToSqr > (double)this.attackRadiusSqr) {
            return;
        }
        this.mob.getLookControl().setLookAt((Entity)this.target, 30.0f, 30.0f);
        if (--this.attackTime == 0) {
            if (!hasLineOfSight) {
                return;
            }
            float v = (float)Math.sqrt(distanceToSqr) / this.attackRadius;
            float clamp = Mth.clamp((float)v, (float)0.1f, (float)1.0f);
            this.rangedAttackMob.performPotionRangedAttack(this.target, clamp);
            this.attackTime = Mth.floor((float)(v * (float)(this.attackIntervalMax - this.attackIntervalMin) + (float)this.attackIntervalMin));
        } else if (this.attackTime < 0) {
            this.attackTime = Mth.floor((double)Mth.lerp((double)(Math.sqrt(distanceToSqr) / (double)this.attackRadius), (double)this.attackIntervalMin, (double)this.attackIntervalMax));
        }
    }
}

