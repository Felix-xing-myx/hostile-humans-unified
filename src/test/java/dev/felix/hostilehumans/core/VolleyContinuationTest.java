package dev.felix.hostilehumans.core;

/** Actual lifecycle tests: flinch/guard, denied lease, expiry, cancellation and no duplicate release. */
public final class VolleyContinuationTest {
    private static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        VolleyContinuation shot = new VolleyContinuation(104, 40, 2);
        check(!shot.canAttempt(103), "never release before original four-tick delay");
        check(shot.canAttempt(104), "first scheduled opportunity");
        shot.attempted(104, false); // Shield or a busy ATTACK/INVENTORY lease, no projectile published.
        check(!shot.terminal() && !shot.canAttempt(105) && shot.canAttempt(106), "short obstruction retains one throttled continuation");
        shot.attempted(106, false);
        check(shot.canAttempt(112), "may resume after attention/guard is released");
        shot.attempted(112, true);
        check(shot.state() == VolleyContinuation.State.COMPLETED && !shot.canAttempt(113), "one successful publication only");
        shot.attempted(114, false);
        check(shot.state() == VolleyContinuation.State.COMPLETED, "late failure cannot reopen a completed volley");

        VolleyContinuation blocked = new VolleyContinuation(4, 40, 2);
        for (int tick = 4; tick < 44; tick += 2) {
            check(blocked.canAttempt(tick), "bounded retry");
            blocked.attempted(tick, false);
        }
        check(!blocked.canAttempt(44) && blocked.state() == VolleyContinuation.State.EXPIRED, "repeated pressure cannot extend the fixed deadline");
        blocked.attempted(45, true);
        check(blocked.state() == VolleyContinuation.State.EXPIRED, "expired continuation cannot publish later");

        VolleyContinuation cancelled = new VolleyContinuation(4, 40, 2);
        cancelled.cancel(); // Target/weapon/session changed or entity is no longer available.
        check(!cancelled.canAttempt(4) && cancelled.state() == VolleyContinuation.State.CANCELLED, "hard cancellation is terminal");
        VolleyContinuation skipped = new VolleyContinuation(4, 40, 2);
        check(!skipped.canAttempt(200), "unloaded/paused tick gaps do not create a delayed burst");
        VolleyContinuation other = new VolleyContinuation(4, 40, 2);
        check(other.canAttempt(4), "another actor/volley owns independent timing");
    }
}
