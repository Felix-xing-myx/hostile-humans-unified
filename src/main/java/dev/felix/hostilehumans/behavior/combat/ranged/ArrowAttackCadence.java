package dev.felix.hostilehumans.behavior.combat.ranged;

/** Real server-tick deadlines, independent of interrupted goals or shield use. */
public final class ArrowAttackCadence {
    public static final int CROSSBOW_DELAY_MIN = 72;
    public static final int CROSSBOW_DELAY_VARIANCE = 32;
    private int bowInterval = 32;
    private int bowDrawAt = Integer.MIN_VALUE;
    private int bowFireAt = Integer.MIN_VALUE;
    private Object loadedCrossbow;
    private int crossbowFireAt;

    void bowInterval(int interval) { bowInterval = Math.max(1, interval); }
    public boolean canDrawBow(int now) { return now >= bowDrawAt; }
    public boolean canFireBow(int now) { return now >= bowFireAt; }
    public void bowShot(int now) { bowShot(now, 1); }
    void bowShot(int now, double attackRate) {
        // Scale the whole real-time cycle; a +40% rate divides time by 1.4.
        double rate = Math.max(.01, attackRate);
        int cooldown = (bowInterval + 1) / 2;
        bowDrawAt = now + (int) Math.ceil(cooldown / rate);
        bowFireAt = now + (int) Math.ceil((cooldown + 7) / rate);
    }
    public void crossbowLoaded(Object weapon, int now, int delay, boolean heavy) {
        if (loadedCrossbow == weapon) return; // Repeated guard/start cannot resample or erase the wait.
        loadedCrossbow = weapon;
        crossbowFireAt = now + crossbowWaitTicks(now, delay, heavy);
    }
    public boolean crossbowWaitFinished(Object weapon, int now) {
        return loadedCrossbow == weapon && now >= crossbowFireAt - 1;
    }
    public boolean hasLoadedCrossbow(Object weapon) { return loadedCrossbow == weapon; }
    public boolean canFireCrossbow(Object weapon, int now) {
        return loadedCrossbow == weapon && now >= crossbowFireAt;
    }
    public void crossbowShot() { loadedCrossbow = null; }
    void crossbowReadyNow(Object weapon, int now) {
        loadedCrossbow = weapon;
        crossbowFireAt = now;
    }
    public static int crossbowWaitTicks(int loadedAt, int delay, boolean heavy) {
        int ticks = 0;
        do {
            ++ticks;
            int accelerated = heavy ? (((loadedAt + ticks) & 1) == 0 ? 2 : 1) : 4;
            delay = Math.max(1, delay - accelerated) - 1;
        } while (delay > 0);
        return ticks + 1; // READY_TO_ATTACK fires on the following tick.
    }
}
