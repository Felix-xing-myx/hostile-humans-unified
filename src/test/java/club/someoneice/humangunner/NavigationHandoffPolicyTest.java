package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.PassedWaypointPolicy;
import dev.felix.hostilehumans.core.PickupCombatWindow;

/** Target-gap and crossed-node regressions; physical collision still needs a live world. */
final class NavigationHandoffPolicyTest {
    private static int checks;
    static void run() {
        PickupCombatWindow window = new PickupCombatWindow();
        check(window.mayCollect(0), "new idle human may collect");
        window.targetChanged(10, false, false);
        check(window.mayCollect(10), "repeated empty assignment is not combat");
        window.targetChanged(20, false, true);
        check(!window.mayCollect(20), "acquisition excludes idle work");
        window.targetChanged(30, true, false);
        check(!window.mayCollect(30) && !window.mayCollect(49), "retarget gap remains reserved");
        window.targetChanged(35, false, false);
        check(window.mayCollect(50), "empty selector polling does not postpone idle forever");
        window.targetChanged(60, false, true);
        window.targetChanged(66, true, true);
        check(!window.mayCollect(85) && window.mayCollect(86), "target switch starts a fresh short grace");
        window.targetChanged(100, true, false);
        check(window.mayCollect(1), "new entity timeline cannot inherit old idle blockade");

        for (double base : new double[]{0, 1_000_000, -1_000_000}) {
            check(PassedWaypointPolicy.crossed(base - .2, 0, base + .2, 0, base, 0, base + 1, 0, .45),
                    "flat forward overshoot survives translation");
            check(PassedWaypointPolicy.crossed(0, base - .2, 0, base + .2, 0, base, 0, base + 1, .45),
                    "z direction behaves like x direction");
        }
        check(!PassedWaypointPolicy.crossed(-.4, 0, -.1, 0, 0, 0, 1, 0, .45), "not yet across node");
        check(!PassedWaypointPolicy.crossed(.3, 0, .6, 0, 0, 0, 1, 0, .45), "old missed crossing cannot skip blindly");
        check(!PassedWaypointPolicy.crossed(.2, 0, -.2, 0, 0, 0, 1, 0, .45), "backward motion cannot advance");
        check(!PassedWaypointPolicy.crossed(-.2, .8, .2, .8, 0, 0, 1, 0, .45), "parallel lane is not this node");
        check(!PassedWaypointPolicy.crossed(-.2, -.8, .2, .8, 0, 0, 1, 0, .45), "crossing sideways cannot cut a corner");
        check(!PassedWaypointPolicy.crossed(-3, 0, 3, 0, 0, 0, 1, 0, .45), "teleport-sized movement excluded");
        check(!PassedWaypointPolicy.crossed(-.2, 0, .2, 0, 0, 0, 5, 0, .45), "nonadjacent jump excluded");
        check(!PassedWaypointPolicy.crossed(Double.NaN, 0, .2, 0, 0, 0, 1, 0, .45), "nonfinite data excluded");
        System.out.println("NavigationHandoffPolicyTest: " + checks + " checks passed; live handoff/jumps unverified");
    }
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
