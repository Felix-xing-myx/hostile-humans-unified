package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.HumanNavigation;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Water pursuit watchdog. Reuses the existing shore planner and lease, never a second MOVE goal. */
public final class WaterCombatNavigation {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID target;
        int progressTick, nextSearch;
        double targetDistance;
        Vec3 anchor, targetSample;
        BlockPos avoid;
        Path route;
        final ShoreProgressTracker progress = new ShoreProgressTracker();
    }
    private WaterCombatNavigation() { }

    /** Survival movement can release this pursuit's shore lease without touching other shore owners. */
    public static void clear(Human human) {
        State state = STATES.get(human);
        if (state == null) return;
        release(human, state);
        STATES.remove(human);
    }

    /** Surface-only combat must not select a navigator whose nodes require diving. */
    public static boolean surfaceCombat(Human human) {
        LivingEntity target = human.getTarget();
        return human.isInWater() && !human.isFleeing && !human.isSeekingShore()
                && target != null && target.isAlive();
    }

    public static boolean isMeleePursuit(Human human, LivingEntity target) {
        return human.isInWater() && target != null && target == human.getTarget() && target.isAlive() && human.canAttack(target)
                && !human.isFleeing && !GunSupport.isGunItem(human.getMainHandItem())
                && !CombatWeaponPolicy.isRangedWeapon(human.getMainHandItem())
                && !(human.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem)
                && !(human.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem);
    }

    /** Ordinary and shielded melee share pursuit; neither installs a second movement goal. */
    public static boolean controlMelee(Human human, LivingEntity target, double speed) {
        if (human.level().isClientSide || human.isNoAi() || human.isSleeping() || human.isPassenger()
                || !isMeleePursuit(human, target) || SoldierOrder.isHoldingPosition(human)) return false;
        PursuitNavigation.update(human, target, speed);
        return true;
    }

    public static boolean ownsRoute(Human human) {
        State state = STATES.get(human);
        return state != null && state.route != null && human.isSeekingShore()
                && MovementContinuity.ownsRoute(human, state.route);
    }

    /** Called from ordinary water approach; true reserves movement for a reachable dry exit. */
    public static boolean update(Human human, LivingEntity target, double maximumRange, double speed) {
        if (human.level().isClientSide || target == null || !target.isAlive() || !human.canAttack(target)
                || !human.isInWater() || human.isFleeing || SoldierOrder.isHoldingPosition(human)) return false;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (!target.getUUID().equals(state.target)) {
            release(human, state);
            state.target = target.getUUID();
            state.progressTick = human.tickCount; state.nextSearch = human.tickCount;
            state.targetDistance = target.position().subtract(human.position()).horizontalDistance();
            state.anchor = human.position(); state.targetSample = target.position();
            state.avoid = null;
        }
        boolean canFight = isMeleePursuit(human, target) ? MeleeCombatRange.canStrike(human, target)
                : human.distanceToSqr(target) <= maximumRange * maximumRange && human.getSensing().hasLineOfSight(target);
        if (canFight) {
            if (ownsRoute(human)) release(human, state);
            state.progressTick = human.tickCount;
            state.targetDistance = target.position().subtract(human.position()).horizontalDistance();
            state.anchor = human.position(); state.targetSample = target.position();
            OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
            return false; // A stationary unit already able to attack is not stuck.
        }
        if (ownsRoute(human)) return true;
        if (human.isSeekingShore()) return false; // Another shore owner retains its lease.
        // Circling is displacement, but not progress toward the opponent.
        Vec3 toward = target.position().subtract(human.position()).multiply(1, 0, 1).normalize();
        Vec3 moved = human.position().subtract(state.anchor).multiply(1, 0, 1);
        boolean chasing = WaterCombatPolicy.chasingMovingTarget(
                target.position().subtract(state.targetSample).horizontalDistanceSqr(), moved.dot(toward),
                moved.x * toward.z - moved.z * toward.x);
        if (WaterCombatPolicy.closingProgress(state.targetDistance,
                target.position().subtract(human.position()).horizontalDistance()) || chasing) {
            state.progressTick = human.tickCount;
            state.targetDistance = target.position().subtract(human.position()).horizontalDistance();
            state.anchor = human.position(); state.targetSample = target.position();
        }
        if (human.tickCount - state.progressTick < CombatTerrainPolicy.STALL_TICKS
                || human.tickCount < state.nextSearch) return false;
        if (OptionalPathBudget.claimFull(human, OptionalPathBudget.Kind.WATER_PURSUIT, 4) != 4) return false;
        Path path = human.findNearestShorePath(state.avoid);
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
        state.nextSearch = human.tickCount + 40;
        if (path == null || !path.canReach() || !HumanTerrainSafety.safePath(human, path)
                || !allowed(human, path) || !human.startCombatShoreDetour(path, speed)) return false;
        state.route = human.getNavigation().getPath();
        state.progress.reset(human.tickCount);
        human.getPersistentData().putString("hostile_humans_unified:combat_terrain_state", "WATER_DETOUR");
        return true;
    }

    private static boolean allowed(Human human, Path path) {
        return SoldierOrder.allowsCombatPath(human, path);
    }

    /** Validate even when a weapon goal yields movement or the target is no longer valid. */
    public static void tick(Human human) {
        if (human.level().isClientSide) return;
        State state = STATES.get(human);
        if (state == null) return;
        LivingEntity target = human.getTarget();
        if (target == null || !target.isAlive() || !target.getUUID().equals(state.target)
                || target.level() != human.level() || !human.canAttack(target) || !human.isAlive()
                || !human.isInWater() || human.isFleeing || human.isNoAi() || human.isSleeping()
                || human.isPassenger() || SoldierOrder.isHoldingPosition(human)) {
            release(human, state); STATES.remove(human); return;
        }
        if (state.route == null) return;
        Path live = human.getNavigation().getPath();
        boolean owned = ownsRoute(human);
        if (owned && live != null && !live.isDone()) {
            Vec3 next = live.getNextEntityPos(human);
            state.progress.observe(human.tickCount, live.getNextNodeIndex(),
                    next.subtract(human.position()).horizontalDistance());
            if (!state.progress.isStalled(human.tickCount)) return;
            if (human.getNavigation() instanceof HumanNavigation ground)
                ground.avoidWaypoint(live.getNextNodePos(), human.level().getGameTime() + 100);
        }
        state.avoid = state.route.getTarget().immutable();
        release(human, state);
        state.nextSearch = human.tickCount + 10;
        state.progressTick = human.tickCount - CombatTerrainPolicy.STALL_TICKS;
        state.targetDistance = target.position().subtract(human.position()).horizontalDistance();
        state.anchor = human.position(); state.targetSample = target.position();
    }

    private static void release(Human human, State state) {
        if (ownsRoute(human)) human.stopSeekingShore();
        state.route = null;
        OptionalPathBudget.cancel(human, OptionalPathBudget.Kind.WATER_PURSUIT);
    }
}
