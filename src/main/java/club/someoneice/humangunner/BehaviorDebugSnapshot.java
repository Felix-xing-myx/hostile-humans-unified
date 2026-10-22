package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.felix.hostilehumans.core.ActionCoordinator;

/** Bounded server-authored values only. Capture never creates authority, advances time or resolves weapon handles. */
public record BehaviorDebugSnapshot(long tick, boolean authorityPresent, long session, String bridge,
        List<Action> actions, List<Feedback> feedback, List<RunningGoal> goals, List<ActionProgressSnapshot> progress, boolean truncated) {
    public static final int MAX_ACTIONS = 12, MAX_FEEDBACK = 8, MAX_GOALS = 12, MAX_PROGRESS = 4;
    public static final int MAX_RESOURCES = 8, MAX_FLAGS = 4;
    public static final int NAME_LENGTH = 96, STATE_LENGTH = 128, REASON_LENGTH = 160;
    public static final BehaviorDebugSnapshot EMPTY = new BehaviorDebugSnapshot(0, false, 0, "unbound", List.of(), List.of(), List.of(), List.of(), false);
    public BehaviorDebugSnapshot {
        bridge = text(bridge, STATE_LENGTH);
        actions = bounded(actions, MAX_ACTIONS); feedback = bounded(feedback, MAX_FEEDBACK);
        goals = bounded(goals, MAX_GOALS); progress = bounded(progress, MAX_PROGRESS);
    }
    public record Action(long session, long sequence, String name, String status, List<String> resources, List<String> heldResources,
                         int priority, boolean interruptible, long remainingTicks, boolean lease, long leaseTicks, long lastHeartbeat) {
        public Action {
            name = text(name, NAME_LENGTH); status = text(status, 16);
            resources = bounded(resources, MAX_RESOURCES); resources.forEach(value -> text(value, 24));
            heldResources = bounded(heldResources, MAX_RESOURCES); heldResources.forEach(value -> text(value, 24));
            if (remainingTicks < 0 || leaseTicks < 0) throw new IllegalArgumentException("Negative action timing");
        }
    }
    public record Feedback(long session, long sequence, String name, String status, String reason) {
        public Feedback { name = text(name, NAME_LENGTH); status = text(status, 16); reason = text(reason, REASON_LENGTH); }
    }
    public record RunningGoal(String source, String name, int priority, List<String> flags, boolean entered,
                              boolean executing, String state, long tokenSession, long tokenSequence) {
        public RunningGoal {
            source = text(source, 24); name = text(name, NAME_LENGTH); state = text(state, STATE_LENGTH);
            flags = bounded(flags, MAX_FLAGS); flags.forEach(value -> text(value, 16));
        }
    }
    private static <T> List<T> bounded(List<T> values, int max) {
        if (values.size() > max) throw new IllegalArgumentException("Oversized behavior snapshot");
        return List.copyOf(values);
    }
    private static String text(String value, int max) {
        if (value == null || value.length() > max) throw new IllegalArgumentException("Oversized behavior text");
        return value;
    }
    private static String clip(String value, int max) {
        if (value == null) return "";
        int end = Math.min(value.length(), max);
        if (end > 0 && end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }
    public static BehaviorDebugSnapshot capture(Human human) {
        if (human.level().isClientSide) return EMPTY;
        ActionCoordinator authority = HumanBehaviorControl.peekActions(human);
        var active = authority == null ? List.<ActionCoordinator.Snapshot>of() : authority.snapshot();
        var history = authority == null ? List.<ActionCoordinator.Feedback>of() : authority.feedback();
        var actions = active.stream().limit(MAX_ACTIONS).map(value -> new Action(value.token().session(), value.token().sequence(),
                clip(value.token().action(), NAME_LENGTH), value.status().name(), value.resources().stream().sorted().map(Enum::name).toList(),
                value.resources().stream().filter(resource -> authority.owner(resource).equals(value.token().action())).sorted().map(Enum::name).toList(),
                value.priority(), value.interruptible(), value.remainingTicks(), value.lease(), value.leaseTicks(), value.lastHeartbeat())).toList();
        List<Feedback> feedback = new ArrayList<>();
        for (int i = Math.max(0, history.size() - MAX_FEEDBACK); i < history.size(); i++) {
            var value = history.get(i);
            feedback.add(new Feedback(value.token().session(), value.token().sequence(), clip(value.token().action(), NAME_LENGTH),
                    value.status().name(), clip(value.reason(), REASON_LENGTH)));
        }
        Collections.reverse(feedback);
        String bridge = HumanGoalExecutionBridge.lastOutcome(human);
        List<RunningGoal> goals = new ArrayList<>(); boolean cut = false;
        if (bridge.equals("unbound")) {
            var running = human.goalSelector.getRunningGoals().limit(MAX_GOALS + 1).toList();
            cut = running.size() > MAX_GOALS;
            for (var goal : running.subList(0, Math.min(MAX_GOALS, running.size())))
                goals.add(new RunningGoal("native_unbound", clip(goal.getGoal().getClass().getSimpleName(), NAME_LENGTH), goal.getPriority(),
                        goal.getFlags().stream().sorted().map(Enum::name).toList(), true, false, "native running; bridge unbound", 0, 0));
        } else {
            var running = HumanGoalExecutionBridge.snapshot(human);
            cut = running.size() > MAX_GOALS;
            for (var goal : running.subList(0, Math.min(MAX_GOALS, running.size())))
                goals.add(new RunningGoal("bridge", clip(goal.goal(), NAME_LENGTH), goal.priority(), goal.flags().stream().sorted().map(Enum::name).toList(),
                        goal.entered(), goal.executing(), clip(goal.state(), STATE_LENGTH), goal.token() == null ? 0 : goal.token().session(),
                        goal.token() == null ? 0 : goal.token().sequence()));
        }
        List<ActionProgressSnapshot> progress = new ArrayList<>();
        progress.addAll(RecoverySupplies.progress(human));
        var shield = human.humanGunner$peekShieldDefense();
        if (shield != null) progress.addAll(shield.rangedChargeProgress());
        progress.addAll(GunSupport.get().reloadProgress(human));
        if (human.isUsingItem() && progress.stream().noneMatch(p -> p.kind().equals("recovery"))) {
            var item = human.getUseItem();
            progress.add(new ActionProgressSnapshot("native_use", ActionProgressSnapshot.item(item),
                    ActionProgressSnapshot.location(human, item), "running", human.level().getGameTime(),
                    Math.max(0, human.getTicksUsingItem()), (long)Math.max(0, human.getTicksUsingItem()) + Math.max(0, human.getUseItemRemainingTicks()),
                    1, Math.max(0, human.getTicksUsingItem()), Math.max(0, human.getUseItemRemainingTicks()), "原生实际使用计时"));
        }
        cut |= progress.size() > MAX_PROGRESS;
        progress = progress.stream().limit(MAX_PROGRESS).toList();
        return new BehaviorDebugSnapshot(human.level().getGameTime(), authority != null, authority == null ? 0 : authority.session(),
                clip(bridge, STATE_LENGTH), actions, feedback, goals, progress, cut || active.size() > MAX_ACTIONS || history.size() > MAX_FEEDBACK);
    }
}
