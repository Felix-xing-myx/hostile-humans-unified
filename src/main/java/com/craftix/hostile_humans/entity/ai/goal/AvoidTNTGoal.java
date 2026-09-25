package com.craftix.hostile_humans.entity.ai.goal;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public class AvoidTNTGoal
extends Goal {
    protected final PathfinderMob mob;
    protected final float maxDist;
    protected final PathNavigation pathNav;
    protected final Predicate<LivingEntity> avoidPredicate;
    protected final Predicate<LivingEntity> predicateOnAvoidEntity;
    private final double walkSpeedModifier;
    private final double sprintSpeedModifier;
    @Nullable
    protected PrimedTnt toAvoid;
    @Nullable
    protected Path path;

    public AvoidTNTGoal(PathfinderMob p_25027_, float p_25029_, double p_25030_, double p_25031_) {
        this(p_25027_, p_25052_ -> true, p_25029_, p_25030_, p_25031_, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    public AvoidTNTGoal(PathfinderMob p_25040_, Predicate<LivingEntity> p_25042_, float p_25043_, double p_25044_, double p_25045_, Predicate<LivingEntity> p_25046_) {
        this.mob = p_25040_;
        this.avoidPredicate = p_25042_;
        this.maxDist = p_25043_;
        this.walkSpeedModifier = p_25044_;
        this.sprintSpeedModifier = p_25045_;
        this.predicateOnAvoidEntity = p_25046_;
        this.pathNav = p_25040_.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Nullable
    PrimedTnt getNearestEntity(List<PrimedTnt> p_45983_, double p_45986_, double p_45987_, double p_45988_) {
        double d0 = -1.0;
        PrimedTnt t = null;
        for (PrimedTnt t1 : p_45983_) {
            double d1 = t1.distanceToSqr(p_45986_, p_45987_, p_45988_);
            if (d0 != -1.0 && !(d1 < d0)) continue;
            d0 = d1;
            t = t1;
        }
        return t;
    }

    public boolean canUse() {
        if (this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human
                && club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human)) return false;
        this.toAvoid = this.getNearestEntity(this.mob.level().getEntitiesOfClass(PrimedTnt.class, this.mob.getBoundingBox().inflate((double)this.maxDist, 3.0, (double)this.maxDist)), this.mob.getX(), this.mob.getY(), this.mob.getZ());
        if (this.toAvoid == null) {
            return false;
        }
        Vec3 vec3 = DefaultRandomPos.getPosAway((PathfinderMob)this.mob, (int)16, (int)7, (Vec3)this.toAvoid.position());
        if (vec3 == null) {
            return false;
        }
        if (this.toAvoid.distanceToSqr(vec3.x, vec3.y, vec3.z) < this.toAvoid.distanceToSqr((Entity)this.mob)) {
            return false;
        }
        this.path = this.pathNav.createPath(vec3.x, vec3.y, vec3.z, 0);
        return this.path != null;
    }

    public boolean canContinueToUse() {
        return (!(this.mob instanceof com.craftix.hostile_humans.entity.entities.Human human)
                || !club.someoneice.humangunner.SoldierOrder.isHoldingPosition(human))
                && !this.pathNav.isDone();
    }

    public void start() {
        this.pathNav.moveTo(this.path, this.walkSpeedModifier);
    }

    public void stop() {
        this.toAvoid = null;
    }

    public void tick() {
        if (this.mob.distanceToSqr((Entity)this.toAvoid) < 49.0) {
            this.mob.getNavigation().setSpeedModifier(this.sprintSpeedModifier);
        } else {
            this.mob.getNavigation().setSpeedModifier(this.walkSpeedModifier);
        }
    }
}

