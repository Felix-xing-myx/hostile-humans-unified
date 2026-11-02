package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.HumanMonitorNetwork;
import dev.felix.hostilehumans.monitor.MonitorInputHistory;
import dev.felix.hostilehumans.monitor.MonitorMotionSnapshot;
import net.minecraft.world.phys.Vec3;

/** Session-local prediction and reconciliation. The tracked Human and real player are never moved. */
final class MonitorMovementPrediction {
    private final MonitorInputHistory<HumanMonitorNetwork.Control> history = new MonitorInputHistory<>();
    private MonitorMotionSnapshot authority;
    private MonitorPredictionBody body;
    private final MonitorPresentationTrack presentation = new MonitorPresentationTrack();
    private long lastServerTick = -1, lastInput = -1, resumeAfter = -1, receivedAt;
    private boolean inputEnabled, awaitingAuthority, disabled;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private MonitorPredictionEnvironment.Reason reason = MonitorPredictionEnvironment.Reason.WAITING_FOR_AUTHORITY;

    void clear() {
        history.clear(); authority = null; body = null; presentation.clear();
        lastServerTick = lastInput = resumeAfter = -1; receivedAt = 0; inputEnabled = awaitingAuthority = disabled = false;
        reason = MonitorPredictionEnvironment.Reason.WAITING_FOR_AUTHORITY;
    }
    void accept(Human human, MonitorMotionSnapshot snapshot) {
        if (disabled) return;
        try { acceptSafely(human, snapshot); }
        catch (RuntimeException | LinkageError failure) { disable(failure); }
    }
    private void disable(Throwable failure) {
        if (disabled) return;
        disabled = true; history.clear(); body = null; authority = null; presentation.clear();
        reason = MonitorPredictionEnvironment.Reason.COMPATIBILITY_FAILURE;
        LOGGER.warn("Human monitor prediction disabled for this control generation after a compatibility failure; "
                + "server control and the authoritative camera remain active", failure);
    }
    private void acceptSafely(Human human, MonitorMotionSnapshot snapshot) {
        if (snapshot.serverTick() <= lastServerTick) return;
        lastServerTick = snapshot.serverTick(); receivedAt = System.nanoTime(); authority = snapshot;
        history.acknowledge(snapshot.acknowledged());
        if (body == null) body = new MonitorPredictionBody(human);
        body.reset(snapshot);
        awaitingAuthority = false;
        if (snapshot.acknowledged() >= resumeAfter) resumeAfter = -1;
        var replay = history.replay();
        if (replay == null) { resumeAfter = lastInput; reason = MonitorPredictionEnvironment.Reason.HISTORY_RESET; }
        else if (inputEnabled && resumeAfter < 0) {
            reason = body.available() ? MonitorPredictionEnvironment.Reason.PREDICTING : body.reason();
            for (var input : replay) {
                if (reason != MonitorPredictionEnvironment.Reason.PREDICTING) break;
                if (!body.step(input.input())) {
                    reason = body.reason(); body.reset(snapshot);
                    // A failed intermediate step leaves the remainder causally invalid.
                    // Do not let the next local input resume from an unreplayed baseline.
                    history.discard(); resumeAfter = lastInput; break;
                }
            }
            if (!snapshot.physics().supported()) reason = MonitorPredictionEnvironment.Reason.SERVER_STATE;
        }
        Vec3 error = presentation.correctionFor(body.position());
        Vec3 previousError = presentation.previousCorrectionFor(body.position());
        // Translating both endpoints and opposing the render offset preserves every partialTick.
        boolean smooth = reason == MonitorPredictionEnvironment.Reason.PREDICTING && error.lengthSqr() <= .35 * .35
                && previousError.lengthSqr() <= .35 * .35
                && human.level().noCollision(human, body.getBoundingBox().expandTowards(error)
                        .minmax(body.getBoundingBox().expandTowards(previousError)));
        presentation.reconcile(body.position(), smooth);
    }
    void suspend() {
        if (disabled) return;
        history.discard(); resumeAfter = lastInput; inputEnabled = false; awaitingAuthority = true;
        reason = MonitorPredictionEnvironment.Reason.INACTIVE_INPUT;
    }

    void input(HumanMonitorNetwork.Control input, boolean enabled) {
        if (disabled) return;
        try { inputSafely(input, enabled); }
        catch (RuntimeException | LinkageError failure) { disable(failure); }
    }
    private void inputSafely(HumanMonitorNetwork.Control input, boolean enabled) {
        lastInput = input.sequence(); inputEnabled = enabled;
        if (!enabled) {
            history.discard(); resumeAfter = input.sequence();
            reason = MonitorPredictionEnvironment.Reason.INACTIVE_INPUT;
            return;
        }
        history.add(input.sequence(), input);
        if (authority == null || body == null || resumeAfter >= 0 || awaitingAuthority) return;
        if (System.nanoTime() - receivedAt > 500_000_000L) {
            history.discard(); resumeAfter = input.sequence(); reason = MonitorPredictionEnvironment.Reason.STALE_AUTHORITY;
            return;
        }
        if (body.step(input)) { presentation.advance(body.position()); reason = MonitorPredictionEnvironment.Reason.PREDICTING; }
        else {
            reason = body.reason(); body.reset(authority);
            history.discard(); resumeAfter = input.sequence();
        }
    }
    Vec3 eyePosition(float partialTick) {
        if (disabled || authority == null || !inputEnabled || awaitingAuthority || resumeAfter >= 0
                || reason != MonitorPredictionEnvironment.Reason.PREDICTING
                || System.nanoTime() - receivedAt > 500_000_000L) return null;
        Vec3 feet = presentation.at(partialTick);
        return feet == null ? null : feet.add(0, authority.physics().eyeHeight(), 0);
    }

    /** Ground contact from the same validated prediction that supplies the rendered eye. */
    Boolean onGround(float partialTick) { return eyePosition(partialTick) == null ? null : body.onGround(); }

    String reason() { return reason.name(); }
}
