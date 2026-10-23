package club.someoneice.humangunner;

/** One roll per ready opportunity, shared by normal and post-defense opportunities. */
final class MeleeJumpPolicy {
    private int nextRoll = Integer.MIN_VALUE;
    boolean mayRoll(int now, int cooldown, boolean grounded, boolean safe) {
        return now >= nextRoll && cooldown <= 0 && grounded && safe;
    }
    void rolled(int now, int interval) { nextRoll = now + Math.max(40, interval); }
    static int cooldown(double speed, double multiplier, int cap) {
        double effective = speed * multiplier;
        if (!Double.isFinite(effective) || effective <= 0) effective = .1;
        return Math.max(Math.max(1, cap), (int)Math.ceil(20 / Math.max(.1, effective)));
    }
    static boolean reinforced(String faction, boolean core, boolean enabled) {
        return CombatStrategySet.forFaction(faction).reinforcedJump(core, enabled);
    }
}
