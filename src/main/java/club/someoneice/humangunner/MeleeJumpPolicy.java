package club.someoneice.humangunner;

/** One roll per ready opportunity, shared by normal and post-defense opportunities. */
final class MeleeJumpPolicy {
    private int nextRoll = Integer.MIN_VALUE;
    boolean mayRoll(int now, int cooldown, boolean grounded, boolean safe) {
        return now >= nextRoll && cooldown <= 0 && grounded && safe;
    }
    void rolled(int now, int interval) { nextRoll = now + Math.max(40, interval); }
    /** Start the weapon upswing after a visible takeoff, aiming short windups near the jump apex. */
    static int attackLeadTicks(int upswing) { return Math.max(3, Math.min(6, 9-Math.max(0, upswing))); }
    static boolean waitingForAttack(int elapsed, boolean grounded, int lead) {
        return grounded || elapsed < lead;
    }
    static int cooldown(double speed, double multiplier, int cap) {
        double effective = speed * multiplier;
        if (!Double.isFinite(effective) || effective <= 0) effective = .1;
        return Math.max(Math.max(1, cap), (int)Math.ceil(20 / Math.max(.1, effective)));
    }
    static boolean reinforced(String faction, boolean core, boolean enabled) {
        return CombatStrategySet.forFaction(faction).reinforcedJump(core, enabled);
    }
}
