package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared combat decision, not a second MOVE goal. Callers retain attacks and animation.
 * NORMAL lets the existing weapon controller move; all other results reserve movement
 * until next tick while still permitting attacks. Only this module's own route is stopped.
 * Speculative searches are bounded, server-only and never load chunks or alter terrain.
 */
public final class CombatTerrainNavigation {
    public enum Decision {
        NORMAL, ROUTE, PENDING, WAIT, FIRE;
        public boolean ownsMovement() { return this != NORMAL; }
    }
    private record Visit(BlockPos end, int until) {}
    private static final class State {
        UUID target;
        ResourceKey<Level> dimension;
        ItemStack weapon;
        Vec3 targetPosition, lastPosition;
        int failures, lastFailure = -10000, vetoTick = -10000, progressTick, nextSearch, nextValidation, ring, cursor;
        boolean cover, partial;
        Path route, approach;
        List<Vec3> candidates;
        final Deque<Visit> visited = new ArrayDeque<>();
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private CombatTerrainNavigation() {}

    public static void blocked(Human human, HumanTerrainSafety.Assessment assessment) {
        if (human.level().isClientSide || assessment.safe()) return;
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        if (human.getNavigation() instanceof HumanNavigation ground)
            ground.avoidTransition(assessment.from(), assessment.to(), human.level().getGameTime() + 60);
        if (human.isFleeing || human.isInLava()) return; // Survival retains its own planner.
        LivingEntity target = human.getTarget();
        State state = eligible(human, target) ? state(human, target)
                : STATES.computeIfAbsent(human, ignored -> new State());
        state.vetoTick = human.tickCount;
        fail(human, state);
        human.getPersistentData().putString("hostile_humans_unified:terrain_obstruction", assessment.reason().name());
    }

    public static void failed(Human human, LivingEntity target) {
        if (!eligible(human, target)) return;
        fail(human, state(human, target));
    }

    private static void fail(Human human, State state) {
        if (human.tickCount - state.lastFailure >= 10) {
            state.failures = Math.min(10, state.failures + 1);
            state.lastFailure = human.tickCount;
        }
    }

    public static boolean hasWork(Human human, LivingEntity target) {
        State state = STATES.get(human);
        return eligible(human, target) && state != null && target.getUUID().equals(state.target)
                && (state.route != null || state.candidates != null || state.failures >= 2
                || human.tickCount - state.vetoTick < CombatTerrainPolicy.MEMORY_TICKS);
    }

    public static boolean ownsRoute(Human human) {
        State state = STATES.get(human);
        return state != null && state.route != null && MovementContinuity.ownsRoute(human, state.route);
    }

    private static boolean eligible(Human human, LivingEntity target) {
        return !human.level().isClientSide && target != null && target.isAlive() && target.level() == human.level()
                && human.isAlive() && !human.isNoAi() && !human.isSleeping() && !human.isPassenger()
                && !human.isFleeing && !human.isInWater() && !human.isInLava() && !human.onClimbable()
                && !human.isSeekingShore() && !human.isShoreTransitionPending()
                && !SoldierOrder.isHoldingPosition(human) && human.canAttack(target);
    }

    private static State state(Human human, LivingEntity target) {
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (state.target != null && (!target.getUUID().equals(state.target)
                || !state.dimension.equals(human.level().dimension()) || state.weapon != human.getMainHandItem()
                || human.tickCount < state.progressTick)) {
            release(human, state);
            state = new State();
            STATES.put(human, state);
        }
        if (state.target == null) {
            state.target = target.getUUID(); state.dimension = human.level().dimension();
            state.weapon = human.getMainHandItem(); state.targetPosition = target.position();
            state.lastPosition = human.position(); state.progressTick = human.tickCount;
        }
        if (target.position().distanceToSqr(state.targetPosition) > 16) {
            // A moving opponent invalidates samples, not the safe detour or failure history.
            state.targetPosition = target.position(); state.candidates = null;
        }
        state.visited.removeIf(visit -> visit.until() <= human.tickCount);
        return state;
    }

    private static void release(Human human, State state) {
        if (MovementContinuity.ownsRoute(human, state.route) || MovementContinuity.ownsRoute(human, state.approach))
            human.getNavigation().stop();
        state.route = null; state.approach = null; state.candidates = null;
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.COMBAT_TERRAIN);
    }

    public static void clear(Human human) {
        State state = STATES.remove(human);
        if (state != null) release(human, state);
    }

    /** Run on the server even when a weapon goal has stopped or a survival goal took over. */
    public static void validate(Human human) {
        State state = STATES.get(human);
        if (state == null) return;
        LivingEntity target = human.getTarget();
        if (!eligible(human, target) || state.target != null && (!target.getUUID().equals(state.target)
                || !human.level().dimension().equals(state.dimension)
                || human.getMainHandItem() != state.weapon)) clear(human);
    }

    /** Ordinary pursuit reports the installed route; an accepted partial route is not success yet. */
    static void accepted(Human human, LivingEntity target) {
        if (!eligible(human, target)) return; // Water/survival routes never become combat-owned.
        Path path = human.getNavigation().getPath();
        if (path == null) return;
        State state = state(human, target);
        state.approach = path; state.partial = !path.canReach();
        if (!state.partial) state.failures = 0;
    }

    private static boolean visited(State state, BlockPos end) {
        for (Visit visit : state.visited) if (visit.end().distSqr(end) <= 4) return true;
        return false;
    }

    private static void remember(Human human, State state, Path route) {
        if (route == null || route.getEndNode() == null) return;
        var node = route.getEndNode();
        while (state.visited.size() >= 16) state.visited.removeFirst();
        state.visited.addLast(new Visit(new BlockPos(node.x, node.y, node.z), human.tickCount + 100));
    }

    static boolean usefulPath(Human human, LivingEntity target, Path path) {
        if (path == null || path.isDone() || path.getEndNode() == null
                || !HumanTerrainSafety.safePath(human, path) || !allowedPath(human, path)) return false;
        if (path.canReach()) return true; // Complete detours may initially lead away from the enemy.
        var end = path.getEndNode();
        Vec3 destination = new Vec3(end.x + .5, end.y, end.z + .5);
        return CombatTerrainPolicy.partialUseful(human.position().distanceToSqr(destination),
                human.distanceToSqr(target), target.distanceToSqr(destination), visited(state(human, target), BlockPos.containing(destination)));
    }

    /** canFire means the caller's actual weapon, ammunition, range and sight permit an answer. */
    public static Decision update(Human human, LivingEntity target, double speed, boolean canFire) {
        if (!eligible(human, target)) { clear(human); return Decision.NORMAL; }
        State state = state(human, target);
        if (state.approach != null) {
            Path current = human.getNavigation().getPath();
            if (MovementContinuity.ownsRoute(human, state.approach) && current != null && current.isDone()) {
                if (state.partial) { remember(human, state, current); fail(human, state); }
                state.approach = null;
            }
        }
        boolean moved = human.position().distanceToSqr(state.lastPosition) >= .09;
        if (moved) { state.lastPosition = human.position(); state.progressTick = human.tickCount; }
        boolean problem = CombatTerrainPolicy.needsAlternative(state.failures,
                state.approach == null && state.route == null ? 0 : human.tickCount - state.progressTick,
                human.tickCount - state.vetoTick < CombatTerrainPolicy.MEMORY_TICKS);
        if (CombatTerrainPolicy.holdFire(canFire, problem || state.route != null || state.candidates != null)) {
            if (state.approach != null && MovementContinuity.ownsRoute(human, state.approach))
                human.getNavigation().stop();
            state.approach = null;
            release(human, state); clearInput(human);
            return mark(human, Decision.FIRE);
        }
        if (state.route != null) {
            Path current = human.getNavigation().getPath();
            boolean owned = MovementContinuity.ownsRoute(human, state.route) && current != null;
            boolean valid = owned && !current.isDone();
            if (valid && human.tickCount >= state.nextValidation) {
                valid = HumanTerrainSafety.safePath(human, current) && allowedPath(human, current);
                state.nextValidation = human.tickCount + 10;
            }
            if (valid) valid = allowedPosition(human, current.getNextEntityPos(human));
            if (owned && !current.isDone()
                    && human.tickCount - state.progressTick < CombatTerrainPolicy.STALL_TICKS
                    && valid) {
                clearInput(human); human.getNavigation().setSpeedModifier(speed);
                return mark(human, Decision.ROUTE);
            }
            boolean arrived = owned && current.isDone();
            remember(human, state, owned ? current : state.route); release(human, state);
            if (arrived && !state.cover) {
                // Retry the original target from the new frontier, not this waypoint again.
                state.failures = 0; state.vetoTick = -10000; state.approach = null;
                state.nextSearch = human.tickCount + 20; state.progressTick = human.tickCount;
                // A reachable waypoint is not proof of reaching the enemy. Widen the
                // next detour if direct pursuit fails again instead of circling locally.
                state.ring = (state.ring + 1) % CombatTerrainPolicy.RADII.length;
                return Decision.NORMAL;
            }
            fail(human, state); state.nextSearch = human.tickCount + (arrived ? 40 : 10);
            if (arrived) { state.cover = false; state.ring = 0; }
        }
        if (!problem && state.candidates == null) return Decision.NORMAL;
        if (state.approach != null && MovementContinuity.ownsRoute(human, state.approach)) {
            Path current = human.getNavigation().getPath();
            // Budget waiting may retain an old route only while it is safe and actually progressing.
            if (human.tickCount - state.progressTick >= CombatTerrainPolicy.STALL_TICKS
                    || current == null || !HumanTerrainSafety.safePath(human, current) || !allowedPath(human, current)) {
                if (current != null && !current.isDone() && human.getNavigation() instanceof HumanNavigation ground)
                    ground.avoidWaypoint(current.getNextNodePos(), human.level().getGameTime() + 100);
                human.getNavigation().stop(); state.approach = null;
            }
        }
        clearInput(human);
        if (human.tickCount < state.nextSearch) return mark(human, Decision.WAIT);
        if (state.candidates == null) {
            state.candidates = candidates(human, target, state.cover ? 8 : CombatTerrainPolicy.RADII[state.ring]);
            state.cursor = 0;
        }
        int budget = OptionalPathBudget.claim(human, OptionalPathBudget.Kind.COMBAT_TERRAIN, 2);
        if (budget == 0) return mark(human, Decision.PENDING);
        Path best = null; double bestScore = Double.NEGATIVE_INFINITY;
        for (int work = 0; work < budget && state.cursor < state.candidates.size(); work++) {
            Vec3 point = state.candidates.get(state.cursor++);
            if (visited(state, BlockPos.containing(point)) || !allowedPosition(human, point)
                    || state.cover && !covered(human, target, point)) continue;
            Path path = human.getNavigation() instanceof HumanNavigation ground
                    ? ground.createFreshPath(BlockPos.containing(point)) : human.getNavigation().createPath(BlockPos.containing(point), 0);
            if (path == null || !path.canReach() || path.isDone() || path.getEndNode() == null
                    || !HumanTerrainSafety.safePath(human, path) || !allowedPath(human, path)) continue;
            var end = path.getEndNode();
            Vec3 actual = new Vec3(end.x + .5, end.y, end.z + .5);
            if (actual.distanceToSqr(human.position()) < 4 || state.cover && !covered(human, target, actual)) continue;
            double score = -path.getNodeCount() * .5 - Math.sqrt(target.distanceToSqr(actual)) * .2;
            if (score > bestScore) { best = path; bestScore = score; }
        }
        if (best != null && human.getNavigation().moveTo(best, speed)) {
            // moveTo may retain an equivalent existing Path instead of the submitted object.
            state.route = human.getNavigation().getPath(); state.approach = null; state.progressTick = human.tickCount;
            state.nextValidation = human.tickCount + 10;
            state.lastPosition = human.position(); state.candidates = null;
            RangedFiringPosition.cancelVisibleSearch(human);
            return mark(human, Decision.ROUTE);
        }
        if (state.cursor < state.candidates.size()) return mark(human, Decision.PENDING);
        state.candidates = null;
        if (!state.cover && ++state.ring < CombatTerrainPolicy.RADII.length) return mark(human, Decision.PENDING);
        state.cover = !state.cover && underFire(human, target);
        state.ring = 0;
        state.nextSearch = human.tickCount + (state.cover ? 1 : 40);
        return mark(human, Decision.WAIT);
    }

    private static Decision mark(Human human, Decision decision) {
        human.getPersistentData().putString("hostile_humans_unified:combat_terrain_state", decision.name());
        return decision;
    }

    private static void clearInput(Human human) {
        human.clearRangedStrafeMotion(); human.setXxa(0); human.setZza(0);
        MovementContinuity.cancelStep(human);
    }

    private static boolean underFire(Human human, LivingEntity target) {
        return human.getLastHurtByMob() == target && human.tickCount - human.getLastHurtByMobTimestamp() < 100
                || HumanGunner.recentGunfireUntil(human) >= human.tickCount
                || target instanceof Mob mob && target instanceof RangedAttackMob && mob.getTarget() == human;
    }

    private static boolean covered(Human human, LivingEntity target, Vec3 feet) {
        if (!loadedSightRegion(human, target.getEyePosition(), feet)) return false;
        Vec3 eye = feet.add(0, human.getEyeHeight(), 0);
        return human.level().clip(new ClipContext(target.getEyePosition(), eye,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType() != HitResult.Type.MISS
                && human.level().clip(new ClipContext(target.getEyePosition(), feet.add(0, human.getBbHeight() * .45, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, human)).getType() != HitResult.Type.MISS;
    }

    /** Level.clip reads world blocks: reject unknown chunks before casting either cover ray. */
    private static boolean loadedSightRegion(Human human, Vec3 from, Vec3 to) {
        int minX = Math.min((int)Math.floor(from.x) >> 4, (int)Math.floor(to.x) >> 4);
        int maxX = Math.max((int)Math.floor(from.x) >> 4, (int)Math.floor(to.x) >> 4);
        int minZ = Math.min((int)Math.floor(from.z) >> 4, (int)Math.floor(to.z) >> 4);
        int maxZ = Math.max((int)Math.floor(from.z) >> 4, (int)Math.floor(to.z) >> 4);
        if ((long)(maxX - minX + 1) * (maxZ - minZ + 1) > 256) return false;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                if (!human.level().hasChunk(x, z)) return false;
        return true;
    }

    private static List<Vec3> candidates(Human human, LivingEntity target, int radius) {
        List<Vec3> positions = new ArrayList<>(16);
        double bearing = Math.atan2(target.getZ() - human.getZ(), target.getX() - human.getX());
        // Include the rear half: valid routes may first move away from the target.
        for (int distance : new int[]{Math.max(3, radius / 2), radius}) {
            for (int turn : new int[]{0, 1, -1, 2, -2, 3, -3, 4}) {
                double angle = bearing + turn * Math.PI / 4;
                positions.addAll(standingPositions(human, human.getX() + Math.cos(angle) * distance,
                        human.getZ() + Math.sin(angle) * distance));
            }
        }
        return positions;
    }

    private static List<Vec3> standingPositions(Human human, double x, double z) {
        List<Vec3> positions = new ArrayList<>(3);
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!human.level().hasChunk(bx >> 4, bz >> 4)) return positions;
        // Nearest height first; keep up to three layers so a canopy cannot hide the ground.
        for (int offset : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, -5, -6, -7, -8, -9, -10, -11, -12}) {
            int y = human.getBlockY() + offset;
            BlockPos floor = new BlockPos(bx, y - 1, bz);
            if (human.level().isOutsideBuildHeight(floor)) continue;
            if (RetreatPathSafety.isHazard(human, floor) || !human.level().getFluidState(floor).isEmpty()) continue;
            var shape = human.level().getBlockState(floor).getCollisionShape(human.level(), floor);
            double top = Double.NEGATIVE_INFINITY;
            for (var box : shape.toAabbs())
                if (box.minX <= .5 && box.maxX >= .5 && box.minZ <= .5 && box.maxZ >= .5)
                    top = Math.max(top, floor.getY() + box.maxY);
            if (!Double.isFinite(top)) continue;
            Vec3 feet = new Vec3(bx + .5, top, bz + .5);
            var bounds = human.getBoundingBox().move(feet.subtract(human.position()));
            if (!human.level().getWorldBorder().isWithinBounds(BlockPos.containing(feet))) continue;
            boolean safe = true;
            for (BlockPos occupied : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                    BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ)))
                if (human.level().isOutsideBuildHeight(occupied) || !human.level().hasChunkAt(occupied)
                        || !human.level().getWorldBorder().isWithinBounds(occupied) || RetreatPathSafety.isHazard(human, occupied)
                        || !human.level().getFluidState(occupied).isEmpty()) { safe = false; break; }
            if (safe && human.level().noCollision(human, bounds) && !positions.contains(feet)) {
                positions.add(feet);
                if (positions.size() == 3) break;
            }
        }
        return positions;
    }

    private static boolean allowedPosition(Human human, Vec3 point) {
        return SoldierOrder.allowsCombatPosition(human, point);
    }
    private static boolean allowedPath(Human human, Path path) {
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var node = path.getNode(index);
            if (!allowedPosition(human, new Vec3(node.x + .5, node.y, node.z + .5))) return false;
        }
        return true;
    }

    static boolean canApproachForMelee(Human human, LivingEntity target) {
        if (!eligible(human, target)) return false;
        if (CombatTerrainPolicy.meleeAllowed(MeleeCombatRange.canStrike(human, target), false)) return true;
        if (!BasicNavigationBudget.tryAcquire(human, 1)) return false;
        Path path = human.getNavigation() instanceof HumanNavigation ground
                ? ground.createFreshPath(target, 0) : human.getNavigation().createPath(target, 0);
        if (path == null || !path.canReach() || path.getEndNode() == null || !usefulPath(human, target, path)) return false;
        var end = path.getEndNode();
        return CombatTerrainPolicy.meleeAllowed(false,
                target.distanceToSqr(end.x + .5, end.y, end.z + .5) <= MeleeCombatRange.reachSqr(human, target) + 1);
    }
}
