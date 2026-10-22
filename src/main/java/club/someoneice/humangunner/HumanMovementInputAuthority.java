package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import dev.felix.hostilehumans.core.ActionCoordinator;
import dev.felix.hostilehumans.core.InputExecutionLease;

/** Exact source of delayed native inputs; no separate priority system or autonomous movement. */
public final class HumanMovementInputAuthority {
    public enum Channel { WANTED, STRAFE, WATER_HOLD, SHORE, SHORE_SEARCH }
    private final InputExecutionLease<Channel, ActionCoordinator.Token> sources = new InputExecutionLease<>(Channel.class);

    public boolean request(Human human, Channel channel, boolean clearing) {
        if (!HumanGoalExecutionBridge.bound(human)) return true;
        if (clearing) return clear(human, channel);
        var token = HumanNavigationOwnership.writeToken(human);
        if (token == null) return false;
        return sources.request(channel, token, true, value -> HumanGoalExecutionBridge.ownsBaseMove(human, value));
    }
    /** Old stop callbacks may clear only their own input, including after their lease expired. */
    public boolean clear(Human human, Channel channel) {
        boolean gated = HumanGoalExecutionBridge.bound(human);
        var caller = HumanNavigationOwnership.currentToken(human);
        if (gated && sources.source(channel) == null) caller = HumanNavigationOwnership.writeToken(human);
        return sources.clear(channel, caller, gated, value -> HumanGoalExecutionBridge.ownsBaseMove(human, value));
    }
    public boolean live(Human human, Channel channel) {
        return sources.live(channel, HumanGoalExecutionBridge.bound(human), value -> HumanGoalExecutionBridge.ownsBaseMove(human, value));
    }
    public ActionCoordinator.Token source(Channel channel) { return sources.source(channel); }
    public void cancel() { sources.cancel(); }
    public boolean execute(Human human, Channel channel, Runnable operation) {
        return HumanNavigationOwnership.continueExecution(human, sources.source(channel), operation);
    }
    /** Execution-side retirement after validation, never exposed as an unscoped movement write. */
    public void discard(Channel channel) { sources.discard(channel); }
}
