package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bounded firing-lane attempts, safe orbit choices and a sustained cramped-terrain melee lease. */
public final class RangedTerrainCombat {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final class State {
        UUID target;
        int blockedSince, failedSearches, blockedSteps, meleeSince, clearSince;
        boolean terrainMelee;
    }
    private RangedTerrainCombat() {}
    public static void clear(Human human) { STATES.remove(human); }

    private static State state(Human human, LivingEntity target) {
        State state = STATES.get(human);
        if (state == null || !target.getUUID().equals(state.target)) {
            state = new State(); state.target = target.getUUID(); state.blockedSince = human.tickCount;
            STATES.put(human, state);
        }
        return state;
    }

    public static void observe(Human human, LivingEntity target, boolean visible) {
        State state = state(human, target);
        if (visible) { state.blockedSince = human.tickCount; state.failedSearches = 0; }
    }

    public static double minimumFiringRange(Human human, double normal) {
        State state = STATES.get(human);
        return state != null && human.tickCount - state.blockedSince >= 20 ? Math.min(4.0D, normal) : normal;
    }

    public static void searchCompleted(Human human, LivingEntity target, boolean success) {
        State state = state(human, target);
        if (success) { state.failedSearches = 0; return; }
        state.failedSearches++;
        CombatTerrainNavigation.failed(human, target);
        if (RangedTerrainPolicy.switchAfterFailures(human.tickCount - state.blockedSince, state.failedSearches)
                && beginTerrainMelee(human, target, state)) return;
        // A finished search with no lane is different from waiting for the shared budget.
        // Approach cover along a real safe path; never stand forever at the old firing point.
        // The next weapon tick advances the shared planner; do not install a competing route here.
    }

    private static boolean beginTerrainMelee(Human human, LivingEntity target, State state) {
        if (human.isFleeing || human.isInWater() || SoldierOrder.isHoldingPosition(human)
                || human.distanceToSqr(target) > 24.0D * 24.0D || !human.canAttack(target)
                || !CombatTerrainNavigation.canApproachForMelee(human, target)) return false;
        if (!RangedWeaponCustody.beginCloseMelee(human)) return false;
        state.terrainMelee = true; state.meleeSince = human.tickCount; state.clearSince = 0;
        RangedFiringPosition.cancelVisibleSearch(human);
        human.clearRangedStrafeMotion();
        return true;
    }

    /** Select native obstacle pursuit rather than the short close-defense backpedal goal. */
    public static boolean isTerrainMelee(Human human) {
        State state = STATES.get(human);
        return state != null && state.terrainMelee && RangedWeaponCustody.isActive(human);
    }

    static boolean holdsMelee(Human human) {
        State state = STATES.get(human);
        LivingEntity target = human.getTarget();
        if (state == null || !state.terrainMelee) return false;
        if (target == null || !target.isAlive() || !target.getUUID().equals(state.target)
                || human.isFleeing || SoldierOrder.isHoldingPosition(human)) { STATES.remove(human); return false; }
        // Finish at least a full attack cycle; periodically retry ranged if a target stays unreachable.
        if (human.tickCount - state.meleeSince < 80) return true;
        boolean open = human.distanceToSqr(target) >= 8.0D * 8.0D
                && human.getSensing().hasLineOfSight(target);
        if (!open) state.clearSince = 0;
        else if (state.clearSince == 0) state.clearSince = human.tickCount;
        if (!RangedTerrainPolicy.keepMelee(human.tickCount - state.meleeSince,
                state.clearSince == 0 ? 0 : human.tickCount - state.clearSince)) { STATES.remove(human); return false; }
        return true;
    }

    public static void strafe(Human human, float forward, float right) {
        if (forward == 0 && right == 0) { human.getMoveControl().strafe(0, 0); return; }
        double yaw = Math.toRadians(human.getYRot());
        float[][] choices = {{forward, right}, {forward, -right}, {forward, 0}, {0, right}, {0, -right}};
        for (float[] choice : choices) {
            if (choice[0] == 0 && choice[1] == 0) continue;
            Vec3 direction = new Vec3(choice[1] * Math.cos(yaw) - choice[0] * Math.sin(yaw), 0,
                    choice[0] * Math.cos(yaw) + choice[1] * Math.sin(yaw)).normalize().scale(1.25D);
            if (HumanTerrainSafety.safeStep(human, direction)
                    && human.level().noCollision(human, human.getBoundingBox().expandTowards(direction))) {
                human.getMoveControl().strafe(choice[0], choice[1]);
                State state = STATES.get(human); if (state != null) state.blockedSteps = 0;
                return;
            }
        }
        human.clearRangedStrafeMotion();
        human.setZza(0);
        LivingEntity target = human.getTarget();
        if (target != null) {
            State state = state(human, target);
            if (RangedTerrainPolicy.shouldReportBlockedSteps(++state.blockedSteps))
                CombatTerrainNavigation.failed(human, target);
        }
    }
}
