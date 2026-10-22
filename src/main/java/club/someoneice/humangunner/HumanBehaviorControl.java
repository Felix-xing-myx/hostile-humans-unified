package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import java.util.Map;
import java.util.WeakHashMap;

/** Shared runtime for the active combat behavior session; future hubs reuse this authority. */
public final class HumanBehaviorControl {
    private static final Map<Human, ActionCoordinator> SESSIONS = new WeakHashMap<>();
    private static final java.util.Set<Human> CANCELLING = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private HumanBehaviorControl() { }

    /** Observation only: never constructs a session, advances a deadline or renews an execution lease. */
    public static ActionCoordinator peekActions(Human human) { return SESSIONS.get(human); }

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
        MeleeActionControl.tick(human);
        ActionCoordinator actions = SESSIONS.get(human);
        if (actions == null) return;
        actions.advance(human.level().getGameTime());
        if (!human.isAlive() || human.isRemoved() || human.isNoAi()) actions.cancelSession("entity unavailable");
    }

    public static void cancel(Human human, String reason) {
        if (!CANCELLING.add(human)) return;
        ActionCoordinator actions = SESSIONS.computeIfAbsent(human, ignored -> new ActionCoordinator());
        actions.closeAdmissions();
        Throwable failure = null;
        try {
            // Old movement callbacks may release their own route, but cannot acquire a new action in cleanup.
            Runnable[] cleanup = {
                    () -> HumanGoalExecutionBridge.cancel(human, reason),
                    () -> human.humanGunner$cancelMovementExecution(),
                    () -> MovementContinuity.clear(human),
                    () -> GunCustody.cancelBackgroundReloads(human),
                    () -> MeleeActionControl.clear(human, reason),
                    () -> NpcDualWield.clear(human),
                    () -> human.humanGunner$cancelMeleeAttackForDefense(),
                    () -> RecoverySupplies.clear(human, reason),
                    () -> HumanCombatDirector.clear(human),
                    () -> RetreatRecoveryPolicy.clearEscape(human),
                    () -> MovementSpeedController.clearLimits(human)
            };
            for (Runnable step : cleanup) {
                try { step.run(); }
                catch (RuntimeException | Error exception) {
                    if (failure == null) failure = exception; else if (failure != exception) failure.addSuppressed(exception);
                }
            }
        } finally {
            try { actions.cancelSession(reason); }
            finally { SESSIONS.remove(human, actions); CANCELLING.remove(human); }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
}
