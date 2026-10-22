package dev.felix.hostilehumans.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entity-local action authority. Requests acquire their complete resource set or nothing. */
public final class ActionCoordinator {
    public enum Resource { BASE_MOVE, MOVE_MODIFIER, FACING, MAIN_HAND, OFF_HAND, USE_ITEM, ATTACK, INVENTORY }
    public enum Status { WAITING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
    public record Token(long session, long sequence, String action) { }
    public record Feedback(Token token, Status status, String reason) { }
    /** Immutable observation at the coordinator's already advanced clock; observing never advances it. */
    public record Snapshot(Token token, Status status, java.util.Set<Resource> resources, int priority, boolean interruptible,
                           long remainingTicks, boolean lease, long leaseTicks, long lastHeartbeat) {
        public Snapshot { resources = java.util.Set.copyOf(resources); }
    }
    private static final int HISTORY_LIMIT = 24;
    private static final class Action {
        final Token token;
        final EnumSet<Resource> resources;
        final int priority;
        final boolean interruptible;
        final long deadline;
        final long leaseTicks;
        long leaseDeadline, lastHeartbeat = Long.MIN_VALUE;
        Status status = Status.WAITING;
        Action(Token token, EnumSet<Resource> resources, int priority, boolean interruptible, long deadline, long leaseTicks) {
            this.token = token; this.resources = resources.clone(); this.priority = priority;
            this.interruptible = interruptible; this.deadline = deadline; this.leaseTicks = leaseTicks; this.leaseDeadline = deadline;
        }
    }
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final EnumMap<Resource, Action> owners = new EnumMap<>(Resource.class);
    private final ArrayDeque<Feedback> history = new ArrayDeque<>();
    private static final java.util.concurrent.atomic.AtomicLong GENERATIONS = new java.util.concurrent.atomic.AtomicLong();
    private long session = GENERATIONS.incrementAndGet(), sequence, now;
    private boolean admissionsClosed;

    public void advance(long tick) {
        now = tick;
        for (Action action : new ArrayList<>(actions.values()))
            if (tick >= expires(action)) end(action, Status.FAILED, action.leaseTicks > 0 ? "execution lease heartbeat expired" : "action timeout");
    }

    /** Repeated evaluation keeps the original token and deadline, rather than restarting an action. */
    public Token request(String name, EnumSet<Resource> resources, int priority,
                         boolean interruptible, long maximumTicks) {
        return requestInternal(name, resources, priority, interruptible, maximumTicks, 0);
    }

    /** A liveness lease, not an action-duration deadline. Only successful execution may heartbeat it. */
    public Token requestLease(String name, EnumSet<Resource> resources, int priority,
                              boolean interruptible, long watchdogTicks) {
        if (watchdogTicks < 1 || watchdogTicks > 1200) throw new IllegalArgumentException("lease watchdog");
        return requestInternal(name, resources, priority, interruptible, watchdogTicks, watchdogTicks);
    }

    private Token requestInternal(String name, EnumSet<Resource> resources, int priority,
                                  boolean interruptible, long maximumTicks, long leaseTicks) {
        if (name == null || name.isBlank() || maximumTicks < 1) throw new IllegalArgumentException("action request");
        Action existing = actions.get(name);
        if (existing != null) {
            if (!existing.resources.equals(resources) || existing.priority != priority
                    || existing.interruptible != interruptible || (existing.leaseTicks > 0) != (leaseTicks > 0))
                throw new IllegalArgumentException("active action request changed: " + name);
            return existing.token; // Neither repeated action requests nor lease requests renew time.
        }
        Token token = new Token(session, ++sequence, name);
        Action action = new Action(token, resources, priority, interruptible, after(maximumTicks), leaseTicks);
        actions.put(name, action);
        return token;
    }

    public boolean heartbeat(Token token) {
        Action action = current(token);
        if (action == null || action.leaseTicks == 0 || action.status != Status.RUNNING || now >= action.leaseDeadline) return false;
        action.lastHeartbeat = now;
        action.leaseDeadline = after(action.leaseTicks);
        return true;
    }

    public List<Snapshot> snapshot() {
        return actions.values().stream().map(action -> new Snapshot(action.token, action.status, action.resources,
                action.priority, action.interruptible, remaining(expires(action)), action.leaseTicks > 0, action.leaseTicks, action.lastHeartbeat)).toList();
    }
    private long remaining(long deadline) { return deadline <= now ? 0 : deadline - now < 0 ? Long.MAX_VALUE : deadline - now; }
    private long after(long ticks) { return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks; }
    private static long expires(Action action) { return action.leaseTicks > 0 ? action.leaseDeadline : action.deadline; }

    public boolean acquire(Token token) {
        if (admissionsClosed) return false;
        Action action = current(token);
        if (action == null) return false;
        if (action.status == Status.RUNNING) return true;
        List<Action> displaced = new ArrayList<>();
        for (Resource resource : action.resources) {
            Action owner = owners.get(resource);
            if (owner == null || owner == action) continue;
            if (!owner.interruptible || owner.priority >= action.priority) return false;
            if (!displaced.contains(owner)) displaced.add(owner);
        }
        // No ownership changes happen before every requested resource passes validation.
        for (Action owner : displaced) end(owner, Status.CANCELLED, "preempted by " + token.action());
        for (Resource resource : action.resources) owners.put(resource, action);
        action.status = Status.RUNNING;
        return true;
    }

    public boolean running(Token token) {
        Action action = current(token);
        return action != null && action.status == Status.RUNNING;
    }

    public boolean pause(Token token) {
        Action action = current(token);
        if (action == null || action.status != Status.RUNNING) return false;
        release(action); action.status = Status.PAUSED;
        return true;
    }

    public boolean finish(Token token, Status status, String reason) {
        if (status == Status.RUNNING || status == Status.WAITING || status == Status.PAUSED)
            throw new IllegalArgumentException("terminal status required");
        Action action = current(token);
        if (action == null) return false; // Late callbacks cannot settle another action/session.
        end(action, status, reason);
        return true;
    }

    public void cancelSession(String reason) {
        for (Action action : new ArrayList<>(actions.values())) end(action, Status.CANCELLED, reason);
        session = GENERATIONS.incrementAndGet();
    }

    /** Closing admission preserves old tokens only for their exact cleanup; this coordinator is never reopened. */
    public void closeAdmissions() { admissionsClosed = true; }

    public String owner(Resource resource) {
        Action action = owners.get(resource);
        return action == null ? "" : action.token.action();
    }
    public List<Feedback> feedback() { return List.copyOf(history); }
    public long session() { return session; }

    private Action current(Token token) {
        if (token == null || token.session() != session) return null;
        Action action = actions.get(token.action());
        return action != null && action.token.equals(token) ? action : null;
    }
    private void release(Action action) {
        for (Resource resource : action.resources) owners.remove(resource, action);
    }
    private void end(Action action, Status status, String reason) {
        release(action); actions.remove(action.token.action(), action); action.status = status;
        history.addLast(new Feedback(action.token, status, reason == null ? "" : reason));
        while (history.size() > HISTORY_LIMIT) history.removeFirst();
    }
}
