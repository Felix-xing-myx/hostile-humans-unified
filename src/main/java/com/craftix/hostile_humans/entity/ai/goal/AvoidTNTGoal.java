package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.InteractionSafetyPolicy;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Explosion escape uses the current land/water navigator and owns only its own route. */
public class AvoidTNTGoal extends Goal {
    protected final PathfinderMob mob;
    protected final float maxDist;
    private final double sprintSpeedModifier;
    @Nullable protected PrimedTnt toAvoid;
    @Nullable protected Path path;
    private PathNavigation assignedNavigation;
    private int nextSearchTick;
    private int retries;
    private Vec3 lastPosition = Vec3.ZERO;
    private int stalledTicks;
    private boolean started;

    public AvoidTNTGoal(PathfinderMob mob, float distance, double walk, double sprint) {
        this(mob, entity -> true, distance, walk, sprint, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
    }

    // Keep the old constructor signature; LivingEntity predicates cannot apply to PrimedTnt.
    public AvoidTNTGoal(PathfinderMob mob, Predicate<LivingEntity> ignored, float distance,
            double walk, double sprint, Predicate<LivingEntity> alsoIgnored) {
        this.mob = mob;
        this.maxDist = distance;
        this.sprintSpeedModifier = sprint;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Nullable PrimedTnt getNearestEntity(List<PrimedTnt> candidates, double x, double y, double z) {
        PrimedTnt nearest = null;
        double best = Double.MAX_VALUE;
        for (PrimedTnt candidate : candidates) {
            double distance = candidate.distanceToSqr(x, y, z);
            if (candidate.isAlive() && !candidate.isRemoved() && distance < best
                    && InteractionSafetyPolicy.escapeTnt(true, distance)) {
                nearest = candidate;
                best = distance;
            }
        }
        return nearest;
    }

    @Override public boolean canUse() {
        if (mob.tickCount < nextSearchTick) return false;
        double radius = Math.max(maxDist, Math.sqrt(InteractionSafetyPolicy.TNT_SAFE_DISTANCE_SQUARED));
        toAvoid = getNearestEntity(mob.level().getEntitiesOfClass(PrimedTnt.class,
                mob.getBoundingBox().inflate(radius)), mob.getX(), mob.getY(), mob.getZ());
        if (!unsafe()) return false;
        retries = 0;
        return searchRoute();
    }

    private boolean unsafe() {
        return toAvoid != null && InteractionSafetyPolicy.escapeTnt(
                toAvoid.isAlive() && !toAvoid.isRemoved(), toAvoid.distanceToSqr(mob));
    }

    private boolean searchRoute() {
        nextSearchTick = mob.tickCount + InteractionSafetyPolicy.TNT_RETRY_DELAY;
        retries++;
        PathNavigation navigation = mob.getNavigation();
        for (int attempt = 0; attempt < 4; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPosAway(mob, 12, 5, toAvoid.position());
            if (candidate == null || toAvoid.distanceToSqr(candidate)
                    < InteractionSafetyPolicy.TNT_SAFE_DISTANCE_SQUARED) continue;
            Path replacement = navigation.createPath(candidate.x, candidate.y, candidate.z, 0);
            if (replacement == null || !replacement.canReach()) continue;
            if (assignedNavigation != null && assignedNavigation != navigation
                    && assignedNavigation.getPath() == path) assignedNavigation.stop();
            path = replacement;
            assignedNavigation = navigation;
            if (started) navigation.moveTo(path, sprintSpeedModifier);
            stalledTicks = 0;
            return true;
        }
        return false;
    }

    @Override public boolean canContinueToUse() {
        return unsafe() && (retries < InteractionSafetyPolicy.TNT_MAX_RETRIES
                || ownsCurrentRoute() && !mob.getNavigation().isDone() && stalledTicks < 20);
    }

    private boolean ownsCurrentRoute() {
        return mob instanceof Human human
                ? club.someoneice.humangunner.MovementContinuity.ownsRoute(human, path)
                : path != null && mob.getNavigation().getPath() == path;
    }

    @Override public void start() {
        started = true;
        if (mob instanceof Human human) {
            human.explosionThreat = toAvoid;
            human.isFleeing = true;
        }
        // Navigator may have changed between selection and start.
        if (assignedNavigation != mob.getNavigation()
                && retries < InteractionSafetyPolicy.TNT_MAX_RETRIES) searchRoute();
        if (assignedNavigation == mob.getNavigation()) mob.getNavigation().moveTo(path, sprintSpeedModifier);
        lastPosition = mob.position();
        stalledTicks = 0;
    }

    @Override public void stop() {
        if (ownsCurrentRoute()) mob.getNavigation().stop();
        if (assignedNavigation != null && assignedNavigation != mob.getNavigation()
                && assignedNavigation.getPath() == path) assignedNavigation.stop();
        if (started && mob instanceof Human human && human.explosionThreat == toAvoid) {
            human.explosionThreat = null;
            human.isFleeing = false;
        }
        if (started) mob.setSprinting(false);
        started = false;
        toAvoid = null;
        path = null;
        assignedNavigation = null;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        if (!unsafe()) return;
        stalledTicks = mob.position().distanceToSqr(lastPosition) < 0.0025D ? stalledTicks + 1 : 0;
        lastPosition = mob.position();
        if ((assignedNavigation != mob.getNavigation() || mob.getNavigation().isDone()
                || !ownsCurrentRoute() || stalledTicks >= 20)
                && InteractionSafetyPolicy.retryTnt(mob.tickCount, nextSearchTick, retries)) searchRoute();
        if (ownsCurrentRoute()) {
            mob.getNavigation().setSpeedModifier(sprintSpeedModifier);
            mob.setSprinting(true);
        }
    }
}
