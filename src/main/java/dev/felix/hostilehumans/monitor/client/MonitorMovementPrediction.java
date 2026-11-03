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
    private boolean inputEnabled, awaitingAuthority, disabled, usingPrediction;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private MonitorPredictionEnvironment.Reason reason = MonitorPredictionEnvironment.Reason.WAITING_FOR_AUTHORITY;

    void clear() {
        history.clear(); authority = null; body = null; presentation.clear();
        lastServerTick = lastInput = resumeAfter = -1; receivedAt = 0; inputEnabled = awaitingAuthority = disabled = usingPrediction = false;
        reason = MonitorPredictionEnvironment.Reason.WAITING_FOR_AUTHORITY;
    }
    void accept(Human human, MonitorMotionSnapshot snapshot) {
        if (disabled) return;
        try { acceptSafely(human, snapshot); }
        catch (RuntimeException | LinkageError failure) { disable(failure); }
    }
    private void disable(Throwable failure) {
        if (disabled) return;
        disabled = true;
        history.clear();
        body = null;
        authority = null;
        clearPresentation();
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
        if (usingPrediction && reason == MonitorPredictionEnvironment.Reason.PREDICTING)
            reconcile(human, body.position(), body.getBoundingBox());
    }
    private void reconcile(Human human, Vec3 position, net.minecraft.world.phys.AABB bounds) {
        Vec3 error = presentation.correctionFor(position);
        Vec3 previousError = presentation.previousCorrectionFor(position);
        // Translating both endpoints and opposing the render offset preserves every partialTick.
        // A speed change alters prediction error, not the camera's interpolation source.
        // Real teleports and corrections through collision still snap.
        boolean smooth = error.lengthSqr() <= 4 && previousError.lengthSqr() <= 4
                && human.level().noCollision(human, bounds.expandTowards(error)
                        .minmax(bounds.expandTowards(previousError)));
        presentation.reconcile(position, smooth);
    }
    void suspend() {
        if (disabled) return;
        history.discard();
        resumeAfter = lastInput;
        inputEnabled = false;
        awaitingAuthority = true;
        clearPresentation();
        reason = MonitorPredictionEnvironment.Reason.INACTIVE_INPUT;
    }

    private void clearPresentation() {
        usingPrediction = false;
        presentation.clear();
    }

    void input(Human human, HumanMonitorNetwork.Control input, boolean enabled) {
        if (disabled) return;
        try {
            boolean predicted = inputSafely(human, input, enabled);
            if (!predicted) {
                Vec3 tracked = human.position();
                if (usingPrediction || presentation.correctionFor(tracked).lengthSqr() > 4)
                    reconcile(human, tracked, human.getBoundingBox());
                presentation.advance(tracked);
            }
            usingPrediction = predicted;
        }
        catch (RuntimeException | LinkageError failure) { disable(failure); }
    }
    private boolean inputSafely(Human human, HumanMonitorNetwork.Control input, boolean enabled) {
        lastInput = input.sequence(); inputEnabled = enabled;
        if (!enabled) {
            history.discard(); resumeAfter = input.sequence();
            reason = MonitorPredictionEnvironment.Reason.INACTIVE_INPUT;
            return false;
        }
        history.add(input.sequence(), input);
        if (authority == null || body == null || resumeAfter >= 0 || awaitingAuthority) return false;
        if (System.nanoTime() - receivedAt > 500_000_000L) {
            history.discard(); resumeAfter = input.sequence(); reason = MonitorPredictionEnvironment.Reason.STALE_AUTHORITY;
            return false;
        }
        Vec3 start = body.position();
        var startBounds = body.getBoundingBox();
        if (body.step(input)) {
            if (!usingPrediction) reconcile(human, start, startBounds);
            presentation.advance(body.position());
            reason = MonitorPredictionEnvironment.Reason.PREDICTING;
            return true;
        }
        reason = body.reason();
        body.reset(authority);
        history.discard();
        resumeAfter = input.sequence();
        return false;
    }
    Vec3 eyePosition(Human human, float partialTick) {
        if (disabled) return null;
        Vec3 feet = presentation.at(partialTick);
        return feet == null ? null : feet.add(0, usingPrediction && authority != null
                ? authority.physics().eyeHeight() : human.getEyeHeight(), 0);
    }

    /** Fall back to tracked ground contact while the presentation follows authoritative movement. */
    Boolean onGround() { return usingPrediction && body != null ? body.onGround() : null; }

    String reason() { return reason.name(); }
}
