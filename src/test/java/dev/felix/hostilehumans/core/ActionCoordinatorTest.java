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
        boolean rejected = false;
        try { actions.request("shield", EnumSet.of(MAIN_HAND), 100, true, 999); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected && actions.running(next), "changed request cannot inherit another resource set");
        actions.advance(30);
        check(!actions.running(next), "deadline is not extended by evaluation");
        check(actions.owner(OFF_HAND).isEmpty(), "timeout releases ownership");
        var low = actions.request("move", EnumSet.of(BASE_MOVE), 1, true, 20);
        check(actions.acquire(low), "base movement starts");
        var urgent = actions.request("escape", EnumSet.of(BASE_MOVE, FACING), 200, true, 20);
        check(actions.acquire(urgent) && !actions.running(low), "urgent request atomically preempts interruptible move");
        check(actions.feedback().stream().anyMatch(f -> f.token().equals(low) && f.status() == CANCELLED),
                "preempted execution gets explicit cancellation feedback");
        ActionCoordinator replacement = new ActionCoordinator();
        replacement.advance(30);
        var replacementAction = replacement.request("escape", EnumSet.of(BASE_MOVE, FACING), 200, true, 20);
        check(replacement.acquire(replacementAction), "replacement runtime starts");
        check(!replacement.finish(urgent, COMPLETED, "callback from discarded runtime"),
                "tokens cannot collide after runtime replacement");
        var motion = new ActionCoordinator(); motion.advance(0);
        var base = motion.requestLease("native-route", EnumSet.of(BASE_MOVE), 0, false, 40);
        var ordinary = motion.requestLease("ordinary-hop", EnumSet.of(MOVE_MODIFIER), 20, true, 40);
        check(motion.acquire(base) && motion.acquire(ordinary), "movement modifier composes with independent base route");
        var leap = motion.request("melee-jump", EnumSet.of(MOVE_MODIFIER), 50, true, 35);
        check(motion.acquire(leap) && !motion.running(ordinary), "enhanced melee jump displaces ordinary hopping only");
        check(motion.running(base), "modifier preemption does not cancel base navigation");
        check(motion.owns(base, BASE_MOVE) && !motion.owns(base, MOVE_MODIFIER), "ownership queries read the actual resource owner");
        check(motion.owns(leap, MOVE_MODIFIER) && !motion.owns(ordinary, MOVE_MODIFIER), "displaced token never retains a resource");
        check(!motion.acquire(ordinary), "stale helper cannot regain a cancelled modifier token");
        motion.finish(leap, COMPLETED, "landed");
        var resumed = motion.requestLease("ordinary-hop", EnumSet.of(MOVE_MODIFIER), 20, true, 40);
        check(motion.acquire(resumed) && !resumed.equals(ordinary), "ordinary helper resumes with a fresh admitted token");
        var paused = new ActionCoordinator(); paused.advance(100);
        var task = paused.request("paused-task", EnumSet.of(BASE_MOVE, MAIN_HAND), 20, true, 10);
        check(paused.acquire(task) && paused.pause(task), "pause preserves intent while releasing control");
        var replacementMove = paused.request("other-task", EnumSet.of(BASE_MOVE), 20, true, 30);
        check(paused.acquire(replacementMove), "another task can use released movement");
        check(!paused.acquire(task) && paused.owner(MAIN_HAND).isEmpty(),
                "resume rechecks every resource without taking a free subset");
        paused.advance(110);
        check(!paused.acquire(task) && paused.running(replacementMove),
                "paused deadlines keep running and expiry does not cancel the replacement");
        var lease = paused.requestLease("paused-lease", EnumSet.of(MOVE_MODIFIER), 20, true, 5);
        check(paused.acquire(lease) && paused.pause(lease), "execution lease can pause");
        check(!paused.heartbeat(lease), "paused work cannot renew an execution heartbeat");
        paused.advance(115);
        check(!paused.acquire(lease), "expired paused lease needs a new request");
        paused.closeAdmissions();
        check(paused.pause(replacementMove) && !paused.acquire(replacementMove),
                "closing a behavior session blocks resuming old work during cleanup");
        System.out.println("ActionCoordinator invariants passed");
    }
}
