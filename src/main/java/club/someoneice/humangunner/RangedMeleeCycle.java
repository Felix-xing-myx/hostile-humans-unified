package club.someoneice.humangunner;

/** Active-goal ticks, so defensive interruptions do not count as time spent fighting. */
final class RangedMeleeCycle {
    private enum Phase { FIGHT, SEARCH, SEPARATE }
    private Phase phase = Phase.FIGHT;
    private int fightTicks, phaseTicks, stalledTicks, retryTicks;

    void tick(boolean maySeparate, boolean progressed) {
        if (retryTicks > 0) retryTicks--;
        if (phase == Phase.FIGHT) {
            fightTicks++;
            if (maySeparate && fightTicks >= 80 && retryTicks == 0) {
                phase = Phase.SEARCH;
                phaseTicks = 0;
            }
            return;
        }
        if (!maySeparate) {
            interrupt();
            return;
        }
        if (phase == Phase.SEARCH) {
            if (++phaseTicks >= 40) retry();
        } else {
            stalledTicks = progressed ? 0 : stalledTicks + 1;
            if (++phaseTicks >= 80 || stalledTicks >= 20) retry();
        }
    }

    boolean searching() { return phase == Phase.SEARCH; }
    boolean separating() { return phase == Phase.SEPARATE; }

    void beginSeparation() {
        phase = Phase.SEPARATE;
        phaseTicks = stalledTicks = 0;
    }

    void retry() {
        phase = Phase.FIGHT;
        fightTicks = phaseTicks = stalledTicks = 0;
        retryTicks = 60;
    }

    void interrupt() {
        if (phase != Phase.FIGHT) retry();
    }

    void reset() {
        phase = Phase.FIGHT;
        fightTicks = phaseTicks = stalledTicks = retryTicks = 0;
    }
}
