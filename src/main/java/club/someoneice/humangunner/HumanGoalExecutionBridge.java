package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/** Execution admission only. Vanilla still chooses goals, priorities, flags and interruptibility. */
public final class HumanGoalExecutionBridge {
    private static final int WATCHDOG_TICKS = 40;
    private static final ThreadLocal<Execution> CURRENT = new ThreadLocal<>();
    private HumanGoalExecutionBridge() { }
    public interface SelectorAccess {
        State hostileHumans$executionState();
        void hostileHumans$executionState(State state);
        Map<Goal.Flag, WrappedGoal> hostileHumans$lockedFlags();
    }
    public interface WrappedAccess {
        Execution hostileHumans$execution();
        void hostileHumans$execution(Execution execution);
    }
    public record GoalSnapshot(String goal, int priority, Set<Goal.Flag> flags, boolean entered,
                               boolean executing, String state, ActionCoordinator.Token token) {
        public GoalSnapshot { flags = Set.copyOf(flags); }
    }
    /** Attached to one selector, never to a static strong-reference registry. */
    public static final class State {
        private final WeakReference<Human> human;
        private final Map<WrappedGoal, Execution> executions = new IdentityHashMap<>();
        private long sequence;
        private boolean cancelling;
        private String lastOutcome = "bound";
        private State(Human human) { this.human = new WeakReference<>(human); }
    }
    public static final class Execution {
        private final WeakReference<State> state;
        private final WrappedGoal wrapped;
        private final String name;
        private final long session;
        private final ActionCoordinator actions;
        private ActionCoordinator.Token token;
        private boolean entered, ticking, stopping;
        private String outcome = "admitted";
        private Set<Goal.Flag> flags;
        private Execution(State state, WrappedGoal wrapped, ActionCoordinator actions) {
            this.state = new WeakReference<>(state); this.wrapped = wrapped; this.actions = actions;
            this.session = actions.session();
            this.name = "goal-move:" + (++state.sequence) + ":" + wrapped.getGoal().getClass().getSimpleName();
            this.flags = Set.copyOf(wrapped.getFlags());
        }
    }
    /** Call once registration is ready, before the first server goal tick. Repeated binding is harmless. */
    public static boolean bind(Human human) { return bind(human, human.goalSelector); }
    public static boolean bind(Human human, GoalSelector selector) {
        if (human.level().isClientSide || selector != human.goalSelector || selector == human.targetSelector
                || !(selector instanceof SelectorAccess access)) return false;
        State prior = access.hostileHumans$executionState();
        if (prior != null) return prior.human.get() == human;
        if (selector.getRunningGoals().findAny().isPresent()) return false; // Bind before the first goal tick, never silently adopt running delegates.
        access.hostileHumans$executionState(new State(human));
        return true;
    }
    private static State state(Human human) {
        return human.goalSelector instanceof SelectorAccess access ? access.hostileHumans$executionState() : null;
    }
    private static boolean available(State state) {
        Human human = state.human.get();
        return !state.cancelling && human != null && !human.level().isClientSide
                && human.isAlive() && !human.isRemoved() && !human.isNoAi() && !HumanInventorySession.isEditing(human);
    }
    /** Root cancellation MUST call this before removing/cancelling the shared ActionCoordinator. */
    public static void cancel(Human human, String reason) {
        State state = state(human);
        if (state != null) cancel(state, reason, ((SelectorAccess) human.goalSelector).hostileHumans$lockedFlags());
    }
    /** Token-specific late callbacks cannot stop a replacement execution of the same native Goal. */
    public static boolean cancelExecution(Human human, ActionCoordinator.Token token, String reason) {
        State state = state(human);
        if (state == null || token == null) return false;
        for (Execution execution : new ArrayList<>(state.executions.values())) {
            if (!token.equals(execution.token)) continue;
            stop(execution, reason, ((SelectorAccess) human.goalSelector).hostileHumans$lockedFlags());
            return true;
        }
        return false;
    }
    private static void cancel(State state, String reason, Map<Goal.Flag, WrappedGoal> locks) {
        if (state.cancelling) return;
        state.cancelling = true;
        Throwable failure = null;
        try {
            for (Execution execution : new ArrayList<>(state.executions.values())) {
                try { stop(execution, reason, locks); }
                catch (RuntimeException | Error exception) { if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception); }
            }
        } finally { state.cancelling = false; state.lastOutcome = reason; }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
    public static void beforeSelector(State state, Map<Goal.Flag, WrappedGoal> locks) {
        if (state == null) return;
        if (!available(state)) { cancel(state, "entity unavailable or editing", locks); return; }
        ActionCoordinator actions = HumanBehaviorControl.actions(state.human.get());
        for (Execution execution : new ArrayList<>(state.executions.values())) {
            reconcileReleasedMove(execution, locks);
            if (execution.actions != actions || execution.session != actions.session()
                    || execution.token != null && !actions.running(execution.token))
                stop(execution, "movement execution/session expired", locks);
        }
    }
    /** Invoked after vanilla stopped old flag owners, but BEFORE WrappedGoal marks itself running. */
    public static void start(State state, WrappedGoal wrapped, Map<Goal.Flag, WrappedGoal> locks) {
        if (state == null) { wrapped.start(); return; }
        if (!available(state) || !(wrapped instanceof WrappedAccess access)) {
            unlock(locks, wrapped); state.lastOutcome = "start rejected: entity or mixin unavailable"; return;
        }
        beforeSelector(state, locks);
        if (!available(state)) { unlock(locks, wrapped); return; }
        if (state.executions.containsKey(wrapped)) { state.lastOutcome = "duplicate start rejected"; return; }
        Execution execution = new Execution(state, wrapped, HumanBehaviorControl.actions(state.human.get()));
        if (execution.flags.contains(Goal.Flag.MOVE) && !acquire(execution)) {
            unlock(locks, wrapped); state.lastOutcome = "waiting: BASE_MOVE unavailable"; return;
        }
        state.executions.put(wrapped, execution); access.hostileHumans$execution(execution);
        try { wrapped.start(); }
        catch (RuntimeException | Error failure) {
            try { stop(execution, "goal start failed", locks); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    /** Release a MOVE flag dropped inside a delegate before the next native selection pass. */
    private static void reconcileReleasedMove(Execution execution, Map<Goal.Flag, WrappedGoal> locks) {
        Set<Goal.Flag> flags = Set.copyOf(execution.wrapped.getFlags());
        if (!flags.contains(Goal.Flag.MOVE) && execution.token != null) {
            finish(execution, "dynamic MOVE released");
            locks.remove(Goal.Flag.MOVE, execution.wrapped);
        }
        execution.flags = flags;
    }
    private static boolean acquire(Execution execution) {
        // Native selector already decided priority. This lease cannot independently preempt another movement source.
        execution.token = execution.actions.requestLease(execution.name, EnumSet.of(ActionCoordinator.Resource.BASE_MOVE), 0, false, WATCHDOG_TICKS);
        if (execution.actions.acquire(execution.token)) return true;
        execution.actions.finish(execution.token, ActionCoordinator.Status.CANCELLED, "native goal waiting for BASE_MOVE");
        execution.token = null; return false;
    }
    /** Also handles native tickRunningGoals(false), which may run without a selection pass. */
    public static void tick(State state, WrappedGoal wrapped, Map<Goal.Flag, WrappedGoal> locks) {
        if (state == null) { wrapped.tick(); return; }
        Execution execution = state.executions.get(wrapped);
        if (execution == null || !execution.entered || !available(state)) {
            if (execution != null) stop(execution, "invalid goal tick", locks);
            return;
        }
        Set<Goal.Flag> flags = Set.copyOf(wrapped.getFlags());
        boolean moved = execution.flags.contains(Goal.Flag.MOVE), moves = flags.contains(Goal.Flag.MOVE);
        execution.flags = flags;
        if (moved && !moves) {
            finish(execution, "dynamic MOVE released");
            locks.remove(Goal.Flag.MOVE, wrapped);
        }
        if (moves) {
            WrappedGoal owner = locks.get(Goal.Flag.MOVE);
            if (owner != null && owner != wrapped && owner.isRunning()) {
                execution.outcome = "waiting: dynamic MOVE handoff"; return; // No delegate tick, route theft or second priority arbitration.
            }
            if (execution.token == null && !acquire(execution)) { execution.outcome = "waiting: BASE_MOVE unavailable"; return; }
            if (!execution.actions.running(execution.token)) { stop(execution, "movement lease lost", locks); return; }
            locks.put(Goal.Flag.MOVE, wrapped);
        }
        try { wrapped.tick(); }
        catch (RuntimeException | Error failure) {
            try { stop(execution, "goal tick failed", locks); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public static void startDelegate(WrappedGoal wrapped, Runnable operation) {
        Execution execution = execution(wrapped);
        if (execution == null) { operation.run(); return; }
        if (execution.entered || execution.stopping) return;
        execution.entered = true; // A partially entered start also receives exactly one cleanup.
        scoped(execution, operation);
        execution.outcome = "running";
    }
    public static void tickDelegate(WrappedGoal wrapped, Runnable operation) {
        Execution execution = execution(wrapped);
        if (execution == null) { operation.run(); return; }
        if (!execution.entered || execution.stopping || execution.ticking) return;
        State state = execution.state.get();
        if (state == null || !available(state) || execution.session != execution.actions.session()
                || wrapped.getFlags().contains(Goal.Flag.MOVE) && !execution.actions.running(execution.token)) return;
        execution.ticking = true;
        try {
            scoped(execution, operation);
            Human human = state.human.get();
            if (human != null && human.goalSelector instanceof SelectorAccess access)
                reconcileReleasedMove(execution, access.hostileHumans$lockedFlags());
            if (execution.entered && execution.token != null) execution.actions.heartbeat(execution.token);
            execution.outcome = "running";
        } finally { execution.ticking = false; }
    }
    public static void stopDelegate(WrappedGoal wrapped, Runnable operation) {
        Execution execution = execution(wrapped);
        if (execution == null) { operation.run(); return; }
        if (!execution.entered || execution.stopping) return;
        execution.entered = false; execution.stopping = true;
        try { scoped(execution, operation); }
        finally {
            execution.stopping = false;
            State state = execution.state.get();
            finish(execution, execution.outcome);
            if (state != null) {
                Human human = state.human.get();
                if (human != null && human.goalSelector instanceof SelectorAccess access) unlock(access.hostileHumans$lockedFlags(), wrapped);
                state.executions.remove(wrapped, execution); state.lastOutcome = execution.outcome;
            }
            if (execution(wrapped) == execution) ((WrappedAccess) wrapped).hostileHumans$execution(null);
        }
    }
    private static Execution execution(WrappedGoal wrapped) {
        return wrapped instanceof WrappedAccess access ? access.hostileHumans$execution() : null;
    }
    private static void scoped(Execution execution, Runnable operation) {
        Execution previous = CURRENT.get(); CURRENT.set(execution);
        try { operation.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    private static void stop(Execution execution, String reason, Map<Goal.Flag, WrappedGoal> locks) {
        execution.outcome = reason;
        try { execution.wrapped.stop(); }
        finally {
            finish(execution, reason); unlock(locks, execution.wrapped);
            State state = execution.state.get();
            if (state != null) state.executions.remove(execution.wrapped, execution);
            if (execution(execution.wrapped) == execution) ((WrappedAccess) execution.wrapped).hostileHumans$execution(null);
        }
    }
    private static void finish(Execution execution, String reason) {
        if (execution.token != null) {
            execution.actions.finish(execution.token, ActionCoordinator.Status.CANCELLED, reason);
            execution.token = null;
        }
    }
    private static void unlock(Map<Goal.Flag, WrappedGoal> locks, WrappedGoal wrapped) {
        locks.entrySet().removeIf(entry -> entry.getValue() == wrapped);
    }
    public static boolean bound(Human human) { return state(human) != null; }
    /** Explicitly distinguish a non-MOVE callback from a navigation continuation outside goals. */
    public static boolean insideCallback(Human human) {
        Execution execution = CURRENT.get();
        State state = execution == null ? null : execution.state.get();
        return state != null && state.human.get() == human;
    }
    /** Dynamic flags may change inside tick. Reconcile before the first actual movement write. */
    public static ActionCoordinator.Token admitMovementWrite(Human human) {
        Execution execution = CURRENT.get();
        State state = execution == null ? null : execution.state.get();
        if (state == null || state.human.get() != human || !available(state)
                || execution.stopping || !execution.entered || !execution.wrapped.getFlags().contains(Goal.Flag.MOVE)
                || execution.session != execution.actions.session()) return null;
        var locks = ((SelectorAccess) human.goalSelector).hostileHumans$lockedFlags();
        WrappedGoal owner = locks.get(Goal.Flag.MOVE);
        if (owner != null && owner != execution.wrapped && owner.isRunning()) return null;
        if (execution.token == null && !acquire(execution)) return null;
        if (!execution.actions.running(execution.token)) return null;
        locks.put(Goal.Flag.MOVE, execution.wrapped);
        execution.flags = Set.copyOf(execution.wrapped.getFlags());
        return execution.token;
    }
    /** For root-owned navigation ownership stamps. Null outside an admitted base-movement callback. */
    public static ActionCoordinator.Token currentExecutionToken(Human human) {
        Execution execution = CURRENT.get();
        State state = execution == null ? null : execution.state.get();
        return state != null && state.human.get() == human ? execution.token : null;
    }
    public static boolean ownsBaseMove(Human human, ActionCoordinator.Token token) {
        State state = state(human);
        if (state == null || token == null || !available(state)) return false;
        return state.executions.values().stream().anyMatch(execution -> execution.entered && !execution.stopping
                && execution.wrapped.getFlags().contains(Goal.Flag.MOVE) && token.equals(execution.token) && execution.actions.running(token));
    }
    public static List<GoalSnapshot> snapshot(Human human) {
        State state = state(human);
        if (state == null) return List.of();
        return state.executions.values().stream().map(execution -> new GoalSnapshot(execution.wrapped.getGoal().getClass().getSimpleName(),
                execution.wrapped.getPriority(), execution.flags, execution.entered, execution.ticking, execution.outcome, execution.token)).toList();
    }
    public static String lastOutcome(Human human) { State state = state(human); return state == null ? "unbound" : state.lastOutcome; }
}
