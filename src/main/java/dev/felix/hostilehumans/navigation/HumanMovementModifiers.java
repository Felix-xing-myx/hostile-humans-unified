package dev.felix.hostilehumans.navigation;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.HumanGoalExecutionBridge;
import dev.felix.hostilehumans.inventory.HumanInventorySession;

import dev.felix.hostilehumans.behavior.ActionCoordinator;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/** Admits the existing sprint/hop helper as a modifier; never owns or replaces the base route. */
public final class HumanMovementModifiers {
    private record Lease(ActionCoordinator actions, ActionCoordinator.Token token, ActionCoordinator.Token base) { }
    private record Context(Human human, Lease lease) { }
    private static final Map<Human, Lease> ACTIVE = new WeakHashMap<>();
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private HumanMovementModifiers() { }
    public static ActionCoordinator.Token execute(Human human, Runnable operation) {
        if (!available(human)) return null;
        boolean bound = HumanGoalExecutionBridge.bound(human);
        var base = HumanGoalExecutionBridge.baseMoveOwner(human);
        if (bound && base == null) return null;
        var actions = HumanBehaviorControl.actions(human);
        Lease previousLease = ACTIVE.get(human);
        if (previousLease != null && (previousLease.actions() != actions || !actions.running(previousLease.token())))
            ACTIVE.remove(human, previousLease);
        var token = actions.requestLease("movement:ordinary-modifier", EnumSet.of(ActionCoordinator.Resource.MOVE_MODIFIER), 20, true, 40);
        if (!actions.acquire(token)) {
            actions.finish(token, ActionCoordinator.Status.CANCELLED, "movement modifier unavailable"); return null;
        }
        Lease lease = new Lease(actions, token, base); ACTIVE.put(human, lease);
        Context previous = CURRENT.get(); CURRENT.set(new Context(human, lease));
        try {
            operation.run();
            if (ACTIVE.get(human) == lease && actions.running(token)) actions.heartbeat(token);
            return token;
        } catch (RuntimeException | Error failure) {
            clear(human, token, "movement modifier failed"); throw failure;
        } finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static ActionCoordinator.Token currentToken(Human human) {
        Context context = CURRENT.get();
        return context != null && context.human() == human ? context.lease().token() : null;
    }
    public static ActionCoordinator.Token currentBase(Human human) {
        Context context = CURRENT.get();
        return context != null && context.human() == human ? context.lease().base() : null;
    }
    /** Enhanced melee jumps use their own already-admitted modifier, without acquiring the ordinary helper. */
    public static void withToken(Human human, ActionCoordinator.Token token, Runnable operation) {
        var actions = HumanBehaviorControl.peekActions(human);
        if (actions == null || !modifierValid(human, token)) return;
        Lease lease = new Lease(actions, token, HumanGoalExecutionBridge.baseMoveOwner(human));
        Context previous = CURRENT.get(); CURRENT.set(new Context(human, lease));
        try { operation.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    public static boolean modifierValid(Human human, ActionCoordinator.Token token) {
        var actions = HumanBehaviorControl.peekActions(human);
        return available(human) && token != null && actions != null
                && actions.owns(token, ActionCoordinator.Resource.MOVE_MODIFIER);
    }
    public static boolean clear(Human human, ActionCoordinator.Token expected, String reason) {
        Lease lease = ACTIVE.get(human);
        if (lease == null || expected == null || !lease.token().equals(expected)) return false;
        boolean owned = lease.actions() == HumanBehaviorControl.peekActions(human) && lease.actions().running(lease.token())
                && (!HumanGoalExecutionBridge.bound(human) || HumanGoalExecutionBridge.ownsBaseMove(human, lease.base()));
        ACTIVE.remove(human, lease);
        lease.actions().finish(lease.token(), ActionCoordinator.Status.CANCELLED, reason);
        return owned;
    }
    public static void cancel(Human human, String reason) {
        Lease lease = ACTIVE.remove(human);
        if (lease != null) lease.actions().finish(lease.token(), ActionCoordinator.Status.CANCELLED, reason);
    }
    private static boolean available(Human human) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !human.isSleeping() && !human.isPassenger() && !HumanInventorySession.isEditing(human);
    }
}
