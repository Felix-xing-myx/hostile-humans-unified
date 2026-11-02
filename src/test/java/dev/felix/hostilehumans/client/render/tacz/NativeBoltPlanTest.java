package dev.felix.hostilehumans.client.render.tacz;

/** Authority edges and authored duration can be tested without a world or gun pack. */
public final class NativeBoltPlanTest {
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    private static void near(NativeBoltPlan.Sample sample, double expected, String why) {
        check(sample != null && Math.abs(sample.seconds() - expected) < .00001, why);
    }
    public static void main(String[] args) {
        var plan = new NativeBoltPlan(1.2667);
        check(plan.sample(0, false, false) == null, "No animation before authoritative bolt");
        near(plan.sample(.1, true, false), 0, "Synchronized rising edge starts clip");
        near(plan.sample(.35, true, false), .25, "Clip advances in native seconds");
        near(plan.sample(.35, true, false), .25, "Repeated render passes do not advance or restart");
        near(plan.sample(1, false, false), .9, "Physical bolt completion does not truncate authored visual tail");
        near(plan.sample(1.35, false, false), 1.25, "Longer authored tail remains visible");
        check(plan.sample(1.4, false, false) == null, "PLAY_ONCE_STOP finishes at resource duration");
        near(plan.sample(2, true, false), 0, "A subsequent bolt gets a fresh clip");
        check(plan.sample(4, true, false) == null, "Long physical bolt cannot loop the completed clip");
        check(plan.sample(5, true, false) == null, "Held synchronized flag cannot replay clip");
        plan.sample(5.1, false, false);
        near(plan.sample(5.2, true, false), 0, "False-to-true re-arms the next bolt");
        check(plan.sample(5.3, true, true) == null, "Reload/draw/melee interruption cancels bolt pose");
        check(plan.sample(5.4, false, false) == null, "Interrupted tail never returns");
        near(plan.sample(6, true, false), 0, "New bolt can follow interruption");
        plan.clear();
        check(plan.sample(6.1, false, false) == null, "Changing observation or gun clears all state");
        near(plan.sample(7, true, false), 0, "New observation may start from currently synchronized bolt");
        near(plan.sample(1, true, false), 0, "Rewound game clock starts a fresh observation");
        check(plan.sample(Double.NaN, true, false) == null, "Invalid clock resets safely");
        check(!new NativeBoltPlan(Double.NaN).supported(), "Malformed duration is unsupported");
        check(new NativeBoltPlan(0).sample(0, true, false) == null, "Missing canonical bolt retains fallback");
        System.out.println("NativeBoltPlanTest passed");
    }
}
