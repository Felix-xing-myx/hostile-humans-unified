package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared runtime for the active combat behavior session; future hubs reuse this authority. */
public final class HumanBehaviorControl {
    private static final Map<Human, ActionCoordinator> SESSIONS = new WeakHashMap<>();
    private HumanBehaviorControl() { }

    public static ActionCoordinator actions(Human human) {
        ActionCoordinator actions = SESSIONS.computeIfAbsent(human, ignored -> new ActionCoordinator());
        actions.advance(human.level().getGameTime());
        return actions;
    }

    public static void beforeTick(Human human) {
        if (human.level().isClientSide) return;
        MovementSpeedController.tickLimits(human);
        MovementSpeedController.limit(human, MovementSpeedController.Limit.FOOD, 0.8,
                RecoverySupplies.isEatingFood(human));
        if (!human.isAlive() || human.isRemoved() || human.isNoAi() || HumanInventorySession.isEditing(human)) {
            MovementSpeedController.clearLimits(human);
            cancel(human, "entity unavailable or editing");
            return;
        }
        ActionCoordinator actions = SESSIONS.get(human);
        if (actions == null) return;
        actions.advance(human.level().getGameTime());
        if (!human.isAlive() || human.isRemoved() || human.isNoAi()) actions.cancelSession("entity unavailable");
    }

    public static void cancel(Human human, String reason) {
        RecoverySupplies.clear(human, reason);
        HumanCombatDirector.clear(human);
        RetreatRecoveryPolicy.clearEscape(human);
        MovementSpeedController.clearLimits(human);
        ActionCoordinator actions = SESSIONS.remove(human);
        if (actions != null) actions.cancelSession(reason);
    }
}
