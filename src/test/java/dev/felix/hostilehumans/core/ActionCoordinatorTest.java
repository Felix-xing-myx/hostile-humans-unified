package dev.felix.hostilehumans.core;

import java.util.EnumSet;
import static dev.felix.hostilehumans.core.ActionCoordinator.Resource.*;
import static dev.felix.hostilehumans.core.ActionCoordinator.Status.*;

/** Authority invariants, independent of Minecraft. Run only after Java compilation is authorized. */
public final class ActionCoordinatorTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ActionCoordinator actions = new ActionCoordinator();
        actions.advance(10);
        var drink = actions.request("drink", EnumSet.of(OFF_HAND, USE_ITEM), 50, false, 30);
        check(actions.acquire(drink), "drink starts");
        var shield = actions.request("shield", EnumSet.of(OFF_HAND, FACING), 100, true, 60);
        check(!actions.acquire(shield), "one current drink is not preempted by pressure");
        check(actions.owner(FACING).isEmpty(), "a failed multi-resource request owns no partial facing");
        check(actions.finish(drink, COMPLETED, "one bottle"), "drink completes once");
        check(!actions.finish(drink, COMPLETED, "late callback"), "completion is idempotent");
        check(actions.acquire(shield), "deferred shield starts after bottle");
        check(actions.pause(shield), "pausable action");
        check(actions.owner(OFF_HAND).isEmpty() && actions.owner(FACING).isEmpty(), "pause releases resources");
        check(actions.acquire(shield), "valid paused action can resume");
        actions.cancelSession("owner edited inventory");
        var next = actions.request("shield", EnumSet.of(OFF_HAND, FACING), 100, true, 20);
        check(actions.acquire(next), "new session starts");
        check(!actions.finish(shield, COMPLETED, "old session"), "old callback cannot end new action");
        check(actions.running(next), "new owner survives old callback");
        check(actions.request("shield", EnumSet.of(OFF_HAND, FACING), 100, true, 999).equals(next),
                "repeated evaluation retains original deadline and token");
        actions.advance(30);
        check(!actions.running(next), "deadline is not extended by evaluation");
        check(actions.owner(OFF_HAND).isEmpty(), "timeout releases ownership");
        var low = actions.request("move", EnumSet.of(BASE_MOVE), 1, true, 20);
        check(actions.acquire(low), "base movement starts");
        var urgent = actions.request("escape", EnumSet.of(BASE_MOVE, FACING), 200, true, 20);
        check(actions.acquire(urgent) && !actions.running(low), "urgent request atomically preempts interruptible move");
        check(actions.feedback().stream().anyMatch(f -> f.token().equals(low) && f.status() == CANCELLED),
                "preempted execution gets explicit cancellation feedback");
        System.out.println("ActionCoordinator invariants passed");
    }
}
