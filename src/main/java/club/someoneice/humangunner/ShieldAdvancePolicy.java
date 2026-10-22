package club.someoneice.humangunner;

/** Blue-team advance sampling consumes shared facts; never acquires movement or scans targets. */
public final class ShieldAdvancePolicy {
    public record Rules(int window, int batch, double probability, int interruption, int cooldown, int maximum) {
        public static Rules defaults() { return new Rules(200, 10, .7, 40, 200, 400); }
        public Rules {
            if (window < 1 || batch < 1 || probability < 0 || probability > 1
                    || interruption < 1 || cooldown < 1 || maximum < 1) throw new IllegalArgumentException("advance rules");
        }
    }
    public record Saved(int cooldown, int window, int batch, int invalid) { }
    private final Rules rules;
    private int cooldown, window, batch, invalid, execution;
    private boolean pending, active;
    public ShieldAdvancePolicy() { this(Rules.defaults()); }
    public ShieldAdvancePolicy(Rules rules) { this.rules = rules; }
    /** elapsed is bounded observed loaded time, never an unload catch-up. */
    public boolean observe(boolean eligible, int elapsed, java.util.function.DoubleSupplier random) {
        int dt = Math.max(0, Math.min(rules.batch(), elapsed));
        if (active) {
            execution += dt;
            if (!eligible || execution >= rules.maximum()) end();
            return false;
        }
        if (cooldown > 0) { cooldown = Math.max(0, cooldown-dt); return false; }
        if (!eligible) {
            invalid += dt;
            if (invalid >= rules.interruption() && (window > 0 || pending)) fail();
            return false;
        }
        invalid = 0;
        if (pending) return true;
        int effective = Math.min(dt, rules.window()-window);
        window += effective; batch += effective;
        if (batch >= rules.batch() || window >= rules.window()) {
            double chance = 1-Math.pow(1-rules.probability(), (double)batch/rules.window());
            batch = 0;
            if (random.getAsDouble() < chance) { pending = true; return true; }
        }
        if (window >= rules.window()) fail();
        return false;
    }
    public boolean begin(boolean eligible) {
        if (!pending || !eligible || cooldown > 0) return false;
        pending = false; active = true; execution = 0;
        return true;
    }
    public boolean active() { return active; }
    public void end() { if (active || pending) fail(); }
    private void fail() { active = pending = false; window = batch = invalid = execution = 0; cooldown = rules.cooldown(); }
    public Saved save() {
        // An interrupted execution is not resurrected after reloading.
        return active || pending ? new Saved(rules.cooldown(), 0, 0, 0) : new Saved(cooldown, window, batch, invalid);
    }
    public void load(Saved value) {
        active = pending = false; execution = 0;
        cooldown = Math.max(0, Math.min(rules.cooldown(), value.cooldown()));
        window = Math.max(0, Math.min(rules.window()-1, value.window()));
        batch = Math.max(0, Math.min(rules.batch()-1, value.batch()));
        invalid = Math.max(0, Math.min(rules.interruption()-1, value.invalid()));
    }
    public void clear() { active = pending = false; cooldown = window = batch = invalid = execution = 0; }
}
