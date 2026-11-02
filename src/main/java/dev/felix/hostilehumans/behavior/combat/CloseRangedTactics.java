package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunCustody;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.ArrowAttackCadence;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedShotClearance;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedTerrainCombat;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.behavior.combat.retreat.RetreatPathSafety;
import dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat;
import dev.felix.hostilehumans.navigation.OptionalPathBudget;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

/** Shared local ranged/melee cycle; weapon-specific custody owns all stack exchanges. */
public final class CloseRangedTactics {
    static final double RADIUS = 8.0D;
    static final int MELEE_TICKS = 60;
    static final int RANGED_TRIAL_TICKS = 100;
    static final int FAILED_TRIAL_HOLD_TICKS = 100;
    private record Hit(int tick, float damage) {}
    private static final class State {
        UUID target;
        Vec3 anchor;
        int lastTick;
        int meleeSince;
        int retryAfter;
        int rangedUntil;
        int defendUntil;
        int side = 1;
        final ArrayDeque<Hit> hits = new ArrayDeque<>();
        State(Human human) {
            target = human.getTarget() == null ? null : human.getTarget().getUUID();
            anchor = human.position();
            lastTick = meleeSince = human.tickCount;
        }
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private CloseRangedTactics() {}

    public static void clear(Human human) { STATES.remove(human); }

    private static State state(Human human) {
        State state = STATES.get(human);
        UUID target = human.getTarget() == null ? null : human.getTarget().getUUID();
        if (state == null || !java.util.Objects.equals(state.target, target)
                || human.tickCount - state.lastTick > 120) {
            state = new State(human);
            STATES.put(human, state);
        }
        state.lastTick = human.tickCount;
        return state;
    }

    public static void beginMelee(Human human) {
        State state = state(human);
        state.meleeSince = human.tickCount;
        state.rangedUntil = 0;
        state.hits.clear();
    }

    public static boolean keepDefense(Human human) {
        State state = STATES.get(human);
        return state != null && !human.isFleeing && human.getTarget() != null && human.getTarget().isAlive()
                && human.getTarget().getUUID().equals(state.target)
                && human.tickCount < state.defendUntil;
    }

    static boolean allowsMeleeEntry(Human human) {
        State state = STATES.get(human);
        return state == null || human.getTarget() == null || !human.getTarget().getUUID().equals(state.target)
                || human.tickCount >= state.rangedUntil;
    }

    static void returnedToGun(Human human) {
        if (human.getTarget() == null || human.distanceToSqr(human.getTarget()) > 36) return;
        State state = state(human);
        state.rangedUntil = human.tickCount + RANGED_TRIAL_TICKS;
        state.hits.clear();
    }

    public static void returnedToBow(Human human) {
        returnedToGun(human);
        State state = STATES.get(human);
        if (state == null || human.getTarget() == null || human.distanceToSqr(human.getTarget()) > 36) return;
        var weapon = human.getMainHandItem();
        if (weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
            boolean heavy = SpartanRangedCompat.isHeavyCrossbow(weapon);
            int rate = heavy ? 2 : 4;
            int loading = net.minecraft.world.item.CrossbowItem.isCharged(weapon) ? 0
                    : (net.minecraft.world.item.CrossbowItem.getChargeDuration(weapon) + rate - 1) / rate;
            int wait = ArrowAttackCadence.crossbowWaitTicks(human.tickCount + loading,
                    ArrowAttackCadence.CROSSBOW_DELAY_MIN + ArrowAttackCadence.CROSSBOW_DELAY_VARIANCE - 1, heavy);
            state.rangedUntil = human.tickCount + Math.max(RANGED_TRIAL_TICKS, loading + wait + 20);
        }
    }

    static boolean tryBow(Human human, LivingEntity target) {
        if (!mayTryRanged(human, target) || human.isInWater() || human.isInLava()
                || human.isSeekingShore() || human.isShoreTransitionPending() || human.onClimbable()
                || RangedTerrainCombat.holdsMelee(human) || !RangedShotClearance.canShoot(human, target)) return false;
        human.hhu$shieldDefense().releaseCombat();
        return RangedWeaponCustody.restorePreferred(human, "close_bow_trial");
    }

    public static boolean allowsLocalPath(Human human, Path path) {
        return bounded(human, state(human), path);
    }

    private static boolean mayTryRanged(Human human, LivingEntity target) {
        State state = state(human);
        return target != null && target.isAlive() && human.canAttack(target) && !human.isFleeing
                && human.tickCount - state.meleeSince >= MELEE_TICKS
                && human.tickCount >= state.retryAfter && !keepDefense(human)
                && !CloseDefenseHold.shouldHold(human) && human.hhu$pendingMeleeTicks(target) < 0
                && !human.hhu$shieldDefense().shouldSuppressRecovery()
                && human.getSensing().hasLineOfSight(target);
    }

    static boolean tryGun(Human human, LivingEntity target) {
        // Gun custody uses reload completion and the faction's 4.5 block hold,
        // rather than bow/crossbow's timed close-range trial.
        return GunCustody.restorePrimaryGun(human,"close_gun_return");
    }

    /** Called with final health loss, after the shared hostile-source validation. */
    public static void damage(Human human, float damage) {
        State state = STATES.get(human);
        if (state == null || human.tickCount >= state.rangedUntil
                || !(GunSupport.isGunItem(human.getMainHandItem())
                || RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem()))
                || human.getTarget() == null || !human.getTarget().getUUID().equals(state.target)
                || human.distanceToSqr(human.getTarget()) > 36
                || !(damage > 0) || !Float.isFinite(damage)) return;
        while (!state.hits.isEmpty() && human.tickCount - state.hits.peekFirst().tick() > 20)
            state.hits.removeFirst();
        if (state.hits.size() >= 64) state.hits.removeFirst();
        state.hits.addLast(new Hit(human.tickCount, damage));
        double total = 0;
        for (Hit hit : state.hits) total += hit.damage();
        if (state.hits.size() >= 2 && total >= human.getMaxHealth() * 0.20D
                || state.hits.size() >= 3 && total >= human.getMaxHealth() * 0.10D) {
            state.rangedUntil = 0;
            state.defendUntil = human.tickCount + FAILED_TRIAL_HOLD_TICKS;
            state.retryAfter = state.defendUntil;
            state.hits.clear();
        }
    }

    /** Two budgeted path probes at most; no ever-receding random retreat destination. */
    static void maneuver(Human human, LivingEntity target) {
        State state = state(human);
        if (SoldierOrder.isHoldingPosition(human) || human.isFleeing || human.isSeekingShore()) return;
        Path current = human.getNavigation().getPath();
        if (current != null && !current.isDone() && bounded(human, state, current)
                && !human.getNavigation().isStuck()) return;
        human.getNavigation().stop();
        int budget = OptionalPathBudget.claim(human, OptionalPathBudget.Kind.RETREAT, 2);
        Vec3 away = human.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 0.01D) away = new Vec3(1, 0, 0);
        Vec3 side = new Vec3(-away.z, 0, away.x);
        for (int i = 0; i < budget; i++) {
            Vec3 point = human.position().add(side.scale(3.0D * state.side)).add(away.scale(0.75D));
            state.side = -state.side;
            if (!inside(state, point)) {
                Vec3 offset = point.subtract(state.anchor).multiply(1, 0, 1).normalize().scale(RADIUS - 1);
                point = new Vec3(state.anchor.x + offset.x, human.getY(), state.anchor.z + offset.z);
            }
            if (!SoldierOrder.allowsCombatPosition(human, point)) continue;
            Path path = human.getNavigation().createPath(BlockPos.containing(point), 0);
            if (bounded(human, state, path) && RetreatPathSafety.isSafe(human, target, path, false)
                    && RetreatPathSafety.tryMove(human, path, 1.0D)) return;
        }
        // Failure means fight here; no unbounded local backstep fallback.
    }

    private static boolean inside(State state, Vec3 point) {
        double x = point.x - state.anchor.x, z = point.z - state.anchor.z;
        return x * x + z * z <= RADIUS * RADIUS;
    }

    private static boolean bounded(Human human, State state, Path path) {
        if (path == null || !path.canReach() || !SoldierOrder.allowsCombatPath(human, path)) return false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 point = Vec3.atBottomCenterOf(path.getNodePos(i));
            if (!inside(state, point)) return false;
        }
        return true;
    }
}
