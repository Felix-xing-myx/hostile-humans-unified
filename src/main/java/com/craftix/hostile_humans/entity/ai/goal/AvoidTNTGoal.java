package com.craftix.hostile_humans.entity.ai.goal;

import com.craftix.hostile_humans.entity.ai.InteractionSafetyPolicy;
import club.someoneice.humangunner.OptionalPathBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
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
    private boolean fleeingBeforeStart;
    private boolean sprintingBeforeStart;
    private boolean changedSprint;
    private int fallbackUntilTick;
    private PrimedTnt sessionThreat;

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
        double radius = Math.max(maxDist, Math.sqrt(InteractionSafetyPolicy.TNT_SAFE_DISTANCE_SQUARED));
        toAvoid = getNearestEntity(mob.level().getEntitiesOfClass(PrimedTnt.class,
                mob.getBoundingBox().inflate(radius)), mob.getX(), mob.getY(), mob.getZ());
        if (!unsafe()) return false;
        // A scheduler interruption cannot replenish one TNT's fallback/search allowance.
        if (sessionThreat != toAvoid) {
            sessionThreat = toAvoid;
            fallbackUntilTick = mob.tickCount + 100;
            nextSearchTick = mob.tickCount;
            retries = 0;
        } else if (mob.tickCount >= fallbackUntilTick) {
            return false;
        }
        return true;
    }

    private boolean unsafe() {
        return toAvoid != null && InteractionSafetyPolicy.escapeTnt(
                toAvoid.isAlive() && !toAvoid.isRemoved(), toAvoid.distanceToSqr(mob));
    }

    private boolean searchRoute() {
        int budget = mob instanceof Human human
                ? OptionalPathBudget.claim(human, OptionalPathBudget.Kind.EXPLOSION, 4) : 4;
        if (budget == 0) {
            nextSearchTick = mob.tickCount + 1;
            return false;
        }
        nextSearchTick = mob.tickCount + InteractionSafetyPolicy.TNT_RETRY_DELAY;
        retries++;
        PathNavigation navigation = mob.getNavigation();
        try {
            for (int attempt = 0; attempt < budget; attempt++) {
                Vec3 candidate = DefaultRandomPos.getPosAway(mob, 12, 5, toAvoid.position());
                if (candidate == null || toAvoid.distanceToSqr(candidate)
                        < InteractionSafetyPolicy.TNT_SAFE_DISTANCE_SQUARED) continue;
                Path replacement = navigation.createPath(candidate.x, candidate.y, candidate.z, 0);
                if (replacement == null || !replacement.canReach()) continue;
                Path previous = ownsCurrentRoute() ? navigation.getPath() : null;
                if (!navigation.moveTo(replacement, sprintSpeedModifier)) {
                    // Vanilla may discard its current path when rejecting a replacement.
                    if (previous != null && !previous.isDone()) navigation.moveTo(previous, sprintSpeedModifier);
                    continue;
                }
                Path accepted = navigation.getPath();
                if (accepted == null || accepted.isDone()) {
                    if (previous != null && !previous.isDone()) navigation.moveTo(previous, sprintSpeedModifier);
                    continue;
                }
                if (assignedNavigation != null && assignedNavigation != navigation
                        && assignedNavigation.getPath() == path) assignedNavigation.stop();
                path = accepted;
                assignedNavigation = navigation;
                stalledTicks = 0;
                return true;
            }
            return false;
        } finally {
            if (mob instanceof Human human) OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.EXPLOSION);
        }
    }

    @Override public boolean canContinueToUse() {
        return unsafe() && (mob.tickCount < fallbackUntilTick
                || ownsCurrentRoute() && !mob.getNavigation().isDone() && stalledTicks < 20);
    }
    private boolean ownsCurrentRoute() {
        return mob instanceof Human human
                ? club.someoneice.humangunner.MovementContinuity.ownsRoute(human, path)
                : path != null && mob.getNavigation().getPath() == path;
    }

    @Override public void start() {
        started = true;
        sprintingBeforeStart = mob.isSprinting();
        changedSprint = false;
        if (mob instanceof Human human) {
            fleeingBeforeStart = human.isFleeing;
            human.explosionThreat = toAvoid;
            human.isFleeing = true;
        }
        // MOVE takeover abandons pursuit/following once; only TNT escape routes are retained later.
        if (!ownsCurrentRoute()) mob.getNavigation().stop();
        if (InteractionSafetyPolicy.retryTnt(mob.tickCount, nextSearchTick, retries)) searchRoute();
        lastPosition = mob.position();
        stalledTicks = 0;
    }

    @Override public void stop() {
        boolean ownsThreat = !(mob instanceof Human human) || human.explosionThreat == toAvoid;
        if (ownsCurrentRoute()) mob.getNavigation().stop();
        if (assignedNavigation != null && assignedNavigation != mob.getNavigation()
                && assignedNavigation.getPath() == path) assignedNavigation.stop();
        if (started && mob instanceof Human human && human.explosionThreat == toAvoid) {
            human.explosionThreat = null;
            human.isFleeing = fleeingBeforeStart;
        }
        if (started && ownsThreat && changedSprint) mob.setSprinting(sprintingBeforeStart);
        if (mob instanceof Human human) OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.EXPLOSION);
        started = false;
        toAvoid = null;
        path = null;
        assignedNavigation = null;
        changedSprint = false;
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void tick() {
        if (!unsafe()) return;
        stalledTicks = mob.position().distanceToSqr(lastPosition) < 0.0025D ? stalledTicks + 1 : 0;
        lastPosition = mob.position();
        if ((assignedNavigation != mob.getNavigation() || mob.getNavigation().isDone()
                || !ownsCurrentRoute() || stalledTicks >= 20)
                && InteractionSafetyPolicy.retryTnt(mob.tickCount, nextSearchTick, retries)) searchRoute();
        if (ownsCurrentRoute() && !mob.getNavigation().isDone() && stalledTicks < 20) {
            mob.getNavigation().setSpeedModifier(sprintSpeedModifier);
            if (!mob.isSprinting()) {
                mob.setSprinting(true);
                changedSprint = true;
            }
        } else if (mob.tickCount < fallbackUntilTick) {
            safeLocalFallback();
        }
    }

    private void safeLocalFallback() {
        if (mob.isInWater()) {
            if (mob instanceof Human human) human.continueWaterRetreatFallback(toAvoid, sprintSpeedModifier);
            return;
        }
        if (!mob.onGround()) return;
        Vec3 away = mob.position().subtract(toAvoid.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01D) return;
        away = away.normalize().scale(0.65D);
        for (double angle : new double[] {0, Math.PI / 4, -Math.PI / 4}) {
            Vec3 offset = new Vec3(away.x * Math.cos(angle) - away.z * Math.sin(angle), 0,
                    away.x * Math.sin(angle) + away.z * Math.cos(angle));
            Vec3 destination = mob.position().add(offset);
            if (toAvoid.distanceToSqr(destination) <= toAvoid.distanceToSqr(mob)) continue;
            if (!mob.level().noCollision(mob, mob.getBoundingBox().expandTowards(offset))) continue;
            // Check both the midpoint and destination: no gaps, fire, hazardous supports or fluids.
            if (!safeFooting(mob.position().add(offset.scale(0.5D))) || !safeFooting(destination)) continue;
            if (mob instanceof Human human) {
                club.someoneice.humangunner.MovementContinuity.requestEscapeStep(human, destination);
            } else {
                mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, sprintSpeedModifier);
            }
            return;
        }
    }

    private boolean safeFooting(Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        if (!mob.level().hasChunkAt(feet) || !mob.level().hasChunkAt(feet.below())) return false;
        var ground = mob.level().getBlockState(feet.below());
        if (!ground.isFaceSturdy(mob.level(), feet.below(), Direction.UP)) return false;
        for (BlockPos pos : new BlockPos[] {feet.below(), feet, feet.above()}) {
            var state = mob.level().getBlockState(pos);
            var type = state.getBlockPathType(mob.level(), pos, mob);
            if (type != null && (type.name().startsWith("DAMAGE") || type.name().startsWith("DANGER")
                    || type.name().equals("LAVA") || type.name().equals("POWDER_SNOW"))) return false;
            if (!mob.level().getFluidState(pos).isEmpty() || state.is(BlockTags.FIRE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                    || state.is(Blocks.POWDER_SNOW)) return false;
        }
        return true;
    }
}
