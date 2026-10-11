package club.someoneice.humangunner;

/** Real server-tick deadlines, independent of interrupted goals or shield use. */
final class ArrowAttackCadence {
    static final int CROSSBOW_DELAY_MIN = 72;
    static final int CROSSBOW_DELAY_VARIANCE = 32;
    private int bowInterval = 32;
    private int bowDrawAt = Integer.MIN_VALUE;
    private int bowFireAt = Integer.MIN_VALUE;
    private Object loadedCrossbow;
    private int crossbowFireAt;

    void bowInterval(int interval) { bowInterval = Math.max(1, interval); }
    boolean canDrawBow(int now) { return now >= bowDrawAt; }
    boolean canFireBow(int now) { return now >= bowFireAt; }
    void bowShot(int now) {
        // Existing NPC bow: two cooldown units and three draw units per real tick.
        bowDrawAt = now + (bowInterval + 1) / 2;
        bowFireAt = bowDrawAt + 7;
    }
    void crossbowLoaded(Object weapon, int now, int delay, boolean heavy) {
        if (loadedCrossbow == weapon) return; // Repeated guard/start cannot resample or erase the wait.
        loadedCrossbow = weapon;
        crossbowFireAt = now + crossbowWaitTicks(now, delay, heavy);
    }
    boolean crossbowWaitFinished(Object weapon, int now) {
        return loadedCrossbow == weapon && now >= crossbowFireAt - 1;
    }
    boolean hasLoadedCrossbow(Object weapon) { return loadedCrossbow == weapon; }
    boolean canFireCrossbow(Object weapon, int now) {
        return loadedCrossbow == weapon && now >= crossbowFireAt;
    }
    void crossbowShot() { loadedCrossbow = null; }
    static int crossbowWaitTicks(int loadedAt, int delay, boolean heavy) {
        int ticks = 0;
        do {
            ++ticks;
            int accelerated = heavy ? (((loadedAt + ticks) & 1) == 0 ? 2 : 1) : 4;
            delay = Math.max(1, delay - accelerated) - 1;
        } while (delay > 0);
        return ticks + 1; // READY_TO_ATTACK fires on the following tick.
    }
}
