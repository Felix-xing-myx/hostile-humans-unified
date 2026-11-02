package dev.felix.hostilehumans.client.render.tacz;

import java.util.Map;

/** Pure pose ownership checks; authored gun angles are deliberately not duplicated in Java. */
public final class NativeCrouchPlanTest {
    private static final Map<String, Double> CLIPS = Map.of("slide", .75, "slide_idle", 7.3333, "slide_back", .85);
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static Map<String, NativeCrouchPlan.Sample> hold(NativeCrouchPlan plan, double start, double end) {
        Map<String, NativeCrouchPlan.Sample> result = Map.of();
        for (double time = start; time <= end + .00001; time += .05)
            result = plan.sample(time, true, true, false, 0);
        return result;
    }
    public static void main(String[] args) {
        var plan = new NativeCrouchPlan(CLIPS);
        check(plan.sample(0, false, true, false, 0).isEmpty(), "Standing has no tactical pose");
        check(plan.sample(.05, true, true, false, 0).containsKey("slide"), "Crouch enters authored slide");
        var held = hold(plan, .1, 1.5);
        check(held.size() == 1 && held.get("slide_idle").weight() == 1, "Settles into authored slide_idle loop");
        check(plan.sample(1.55, false, true, false, 0).containsKey("slide_back"), "Release runs authored slide_back");
        for (int i = 0; i < 30; i++) plan.sample(1.6 + i * .05, false, true, false, 0);
        check(plan.sample(3.2, false, true, false, 0).isEmpty(), "Release finishes without a residual pose");

        plan.clear();
        hold(plan, 0, 1.5);
        var aiming = plan.sample(1.55, true, true, false, .5F);
        check(aiming.values().stream().allMatch(sample -> sample.weight() <= .5F), "ADS fades stance from every channel");
        check(plan.sample(1.6, true, true, false, 1).isEmpty(), "Complete ADS owns the entire pose");
        check(plan.sample(1.65, true, true, false, 0).isEmpty(), "Leaving complete ADS starts fresh without stale weights");
        check(plan.sample(1.7, true, true, false, 0).containsKey("slide"), "Hip fire can enter again after ADS");

        for (boolean supported : new boolean[]{false, true}) {
            plan.clear();
            hold(plan, 0, 1.5);
            check(plan.sample(1.55, true, supported, true, 0).isEmpty(), "Reload/sprint action takes precedence");
            check(plan.sample(1.6, true, true, false, 0).isEmpty(), "Action exit cannot resurrect old stance");
        }
        check(plan.sample(1.65, true, false, false, 0).isEmpty(), "GunData.canSlide=false disables stance");
        var absent = new NativeCrouchPlan(Map.of("slide", .75));
        check(hold(absent, 0, 1.5).isEmpty(), "Missing native clips preserve the existing presentation");
        plan.clear();
        hold(plan, 0, 1.5);
        check(plan.sample(.1, true, true, false, 0).isEmpty(), "Time rewind resets pose ownership");
        check(plan.sample(Double.NaN, true, true, false, 0).isEmpty(), "Invalid clock does not poison animation");
        check(plan.sample(2, true, true, false, Float.NaN).isEmpty(), "Invalid aim is safely ignored");
        System.out.println("NativeCrouchPlanTest passed");
    }
}
