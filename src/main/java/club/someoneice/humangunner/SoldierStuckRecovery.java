package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Last-resort order recovery, independent of combat goal ownership; never installs a route. */
final class SoldierStuckRecovery {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        Vec3 origin;
        int progressTick, nextProbe, failures, peaceUntil, cooldownUntil;
    }
    private SoldierStuckRecovery() {}
    static void clear(Human human) { STATES.remove(human); }
    static boolean recovering(Human human) {
        State state = STATES.get(human);
        return state != null && human.tickCount < state.peaceUntil;
    }
    static boolean due(int stationaryTicks, double destinationDistanceSquared, boolean follow) {
        return stationaryTicks >= 200 && destinationDistanceSquared > (follow ? 6.25 : 2.25);
    }
    static boolean eligibleOrder(SoldierOrder order) {
        return order == SoldierOrder.FOLLOW || order == SoldierOrder.BODYGUARD
                || order == SoldierOrder.GUARD || order == SoldierOrder.PATROL;
    }
    static void tick(Human human) {
        if (!(human.level() instanceof ServerLevel) || !human.hasOwner() || !human.isAlive()
                || !eligibleOrder(SoldierOrder.get(human)) || human.isNoAi() || human.isSleeping()
                || human.isPassenger() || human.isFleeing || human.isInWater() || human.isInLava()
                || human.onClimbable() || human.isSeekingShore() || human.isShoreTransitionPending()
                || HumanGunner.isManualInventoryOpen(human)
                || human.explosionThreat != null) {
            clear(human);
            return;
        }
        // Cheap position sampling; do not create a path or inspect nearby entities each tick.
        if (Math.floorMod(human.tickCount + human.getId(), 10) != 0) return;
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (state.origin == null || human.tickCount < state.progressTick
                || state.origin.distanceToSqr(human.position()) > 4) {
            state.origin = human.position(); state.progressTick = human.tickCount; state.failures = 0;
        }
        // Jumping against the wall must not restart the ten-second deadline.
        // Wait for a grounded tick and finish any recovery item before teleporting.
        if (!human.onGround() || RecoverySupplies.hasHandCustody(human)) return;
        if (human.tickCount < state.cooldownUntil || human.tickCount < state.nextProbe) return;
        boolean follow = SoldierOrder.get(human) == SoldierOrder.FOLLOW
                || SoldierOrder.get(human) == SoldierOrder.BODYGUARD;
        ServerPlayer owner = follow ? BodyguardProtection.owner(human) : null;
        if (follow && owner == null) { clear(human); return; }
        BlockPos post = follow ? owner.blockPosition() : SoldierOrder.recoveryPost(human);
        Vec3 desired = follow ? owner.position() : Vec3.atBottomCenterOf(post);
        if (!due(human.tickCount - state.progressTick, human.position().distanceToSqr(desired), follow)) {
            state.failures = 0;
            return;
        }
        // Resolve a collision-checked, loaded destination before testing reachability.
        Vec3 landing = null;
        for (int attempt = 0; attempt < 12 && landing == null; attempt++) {
            int dx = attempt == 0 ? 0 : human.getRandom().nextInt(7) - 3;
            int dz = attempt == 0 ? 0 : human.getRandom().nextInt(7) - 3;
            Vec3 candidate = follow ? SafePositions.nearOwner(human, owner, post.getX() + dx, post.getZ() + dz)
                    : SafePositions.nearPost(human, post, post.getX() + dx, post.getZ() + dz);
            // Do not count a landing back inside the same hole as a successful rescue.
            if (candidate != null && candidate.distanceToSqr(human.position()) > 4) landing = candidate;
        }
        if (landing == null) { state.nextProbe = human.tickCount + 40; return; }
        // A deferred FIFO grant is neither a failed path nor proof of entrapment.
        if (!BasicNavigationBudget.tryAcquire(human, 1)) return;
        var path = human.getNavigation().createPath(BlockPos.containing(landing), 0);
        BasicNavigationBudget.cancel(human);
        state.nextProbe = human.tickCount + 20;
        if (path != null && path.canReach()) { state.failures = 0; return; }
        if (++state.failures < 2) return;

        human.forgetSoldierTarget();
        PursuitNavigation.clear(human);
        BodyguardProtection.clear(human);
        SoldierOrder.stopMovement(human);
        SoldierFollowNavigation.clear(human);
        MovementContinuity.cancelStep(human);
        human.clearRangedStrafeMotion();
        human.guardCombatState.reset();
        human.teleportTo(landing.x, landing.y, landing.z);
        human.getNavigation().stop();
        human.getMoveControl().setWantedPosition(landing.x, landing.y, landing.z, 0);
        human.setDeltaMovement(Vec3.ZERO);
        human.fallDistance = 0;
        state.origin = human.position(); state.progressTick = human.tickCount; state.failures = 0;
        state.peaceUntil = human.tickCount + 40;
        state.cooldownUntil = human.tickCount + 200;
    }
}
