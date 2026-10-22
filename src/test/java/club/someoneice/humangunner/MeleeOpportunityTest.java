package club.someoneice.humangunner;

/** Pure readiness/throttle boundaries; no Minecraft boot or test execution requested. */
public final class MeleeOpportunityTest {
    public static void main(String[] args) {
        var policy = new MeleeJumpPolicy();
        check(!policy.mayRoll(0, 1, true, true), "weapon must actually be ready");
        check(!policy.mayRoll(0, 0, false, true), "airborne does not roll");
        check(!policy.mayRoll(0, 0, true, false), "unsafe opportunity does not roll");
        check(policy.mayRoll(0, 0, true, true), "first ready grounded safe opportunity is eligible");
        policy.rolled(0, 1);
        check(!policy.mayRoll(39, 0, true, true), "requested shorter interval cannot bypass forty ticks");
        check(policy.mayRoll(40, 0, true, true), "exact forty tick boundary reopens");
        policy.rolled(40, 100);
        check(!policy.mayRoll(139, 0, true, true) && policy.mayRoll(140, 0, true, true), "long explicit cooldown is retained");
        check(MeleeJumpPolicy.reinforced("crimson", true, true), "red actual core weapon may reinforce");
        check(MeleeJumpPolicy.reinforced("ashen", true, true), "gray actual core weapon may reinforce");
        check(!MeleeJumpPolicy.reinforced("azure", true, true), "other faction has only first approach");
        check(!MeleeJumpPolicy.reinforced("ashen", false, true), "noncore weapon does not inherit profession");
        check(!MeleeJumpPolicy.reinforced("ashen", true, false), "disabled actual ability does not reinforce");
        check(MeleeJumpPolicy.cooldown(1.6, 1, 1) == 13, "vanilla panel sword speed rounds safely to ticks");
        check(MeleeJumpPolicy.cooldown(1.6, 1.25, 1) == 10, "effective attack speed applies once");
        check(MeleeJumpPolicy.cooldown(20, 2, 2) == 2, "native backend interval cap remains authoritative");
        check(MeleeJumpPolicy.cooldown(Double.NaN, 1, 1) == 200, "invalid speed fails conservatively");
        System.out.println("MeleeOpportunityTest: 16 checks passed");
    }
    private static void check(boolean pass, String reason) { if (!pass) throw new AssertionError(reason); }
}
