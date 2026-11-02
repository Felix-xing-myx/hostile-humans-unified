package dev.felix.hostilehumans.common;

import dev.felix.hostilehumans.behavior.ActionCoordinator;

import java.util.EnumSet;
import static dev.felix.hostilehumans.behavior.ActionCoordinator.Resource.*;

/** Pure liveness/deadline regressions. Not executed until compilation is authorized. */
public final class MovementAuthorityLeaseTest {
    public static void main(String[] args) {
        var actions = new ActionCoordinator(); actions.advance(10);
        var ordinary = actions.request("ordinary", EnumSet.of(ATTACK), 20, true, 5);
        check(actions.acquire(ordinary) && !actions.heartbeat(ordinary), "ordinary action cannot renew by heartbeat");
        actions.advance(14);
        check(actions.request("ordinary", EnumSet.of(ATTACK), 20, true, 1000).equals(ordinary), "same ordinary token");
        var view = actions.snapshot();
        check(view.size() == 1 && view.get(0).remainingTicks() == 1 && !view.get(0).lease(), "observation uses existing clock");
        try { view.get(0).resources().clear(); throw new AssertionError("mutable resources"); }
        catch (UnsupportedOperationException expected) { }
        check(actions.snapshot().equals(view), "observing does not advance or renew anything");
        actions.advance(15); check(!actions.running(ordinary), "repeated request did not postpone action deadline");
        var lease = actions.requestLease("route", EnumSet.of(BASE_MOVE), 0, false, 10);
        check(!actions.heartbeat(lease), "waiting request is not execution");
        check(actions.acquire(lease), "native-selected route begins");
        for (int tick = 16; tick < 5000; tick++) {
            actions.advance(tick); check(actions.running(lease), "long legal route retains the same lease");
            check(actions.heartbeat(lease), "actual successful execution renews liveness");
            check(actions.requestLease("route", EnumSet.of(BASE_MOVE), 0, false, 500).equals(lease), "evaluation cannot replace lease identity");
        }
        var snapshot = actions.snapshot().get(0);
        check(snapshot.token().equals(lease) && snapshot.lease() && snapshot.leaseTicks() == 10
                && snapshot.remainingTicks() == 10 && snapshot.lastHeartbeat() == 4999, "bounded liveness is distinct from route duration");
        actions.advance(5008); actions.requestLease("route", EnumSet.of(BASE_MOVE), 0, false, 500);
        actions.advance(5009); check(!actions.running(lease) && !actions.heartbeat(lease), "evaluation cannot resurrect stalled execution");
        check(actions.owner(BASE_MOVE).isEmpty(), "watchdog releases authority");
        var next = actions.requestLease("route", EnumSet.of(BASE_MOVE), 0, false, 10);
        check(actions.acquire(next), "next admitted route begins");
        check(!actions.finish(lease, ActionCoordinator.Status.CANCELLED, "late stop") && actions.running(next), "old route cannot end replacement");
        check(actions.pause(next) && !actions.heartbeat(next), "paused execution cannot renew liveness");
        actions.cancelSession("unload"); check(!actions.heartbeat(next), "old session cannot heartbeat");
        var closing = actions.requestLease("closing route", EnumSet.of(BASE_MOVE), 0, false, 10);
        check(actions.acquire(closing), "route before cleanup");
        actions.closeAdmissions();
        check(!actions.heartbeat(closing) && !actions.acquire(closing), "cleanup cannot renew or reacquire even an existing lease");
        var reentrant = actions.request("cleanup callback", EnumSet.of(ATTACK), 999, true, 10);
        check(!actions.acquire(reentrant), "cleanup callback cannot start a fresh action");
        check(actions.running(closing), "old authority remains available to exact route cleanup");
        check(actions.finish(closing, ActionCoordinator.Status.CANCELLED, "own route released"), "old cleanup can settle its token");
        actions.cancelSession("cleanup complete");
        check(!actions.acquire(actions.request("stale coordinator", EnumSet.of(ATTACK), 999, true, 10)), "closed coordinator never reopens");
        System.out.println("MovementAuthorityLeaseTest passed");
    }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
