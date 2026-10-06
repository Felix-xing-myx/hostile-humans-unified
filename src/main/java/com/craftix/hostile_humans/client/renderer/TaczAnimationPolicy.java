package com.craftix.hostile_humans.client.renderer;

/** Pure client playback policy; never schedules attacks or changes gun state. */
final class TaczAnimationPolicy {
    private TaczAnimationPolicy() {}

    static boolean needsReset(int previousTick, int tick, long previousTime, long time,
                              double distanceSqr) {
        return tick < previousTick || tick - previousTick > 20
                || time < previousTime || time - previousTime > 20 || distanceSqr > 16.0D;
    }

    static boolean cooldownStarted(long previous, long current) {
        return previous != Long.MIN_VALUE && current > 0L && current > previous + 2L;
    }

    static float recoil(float age, int firedTick) {
        double elapsed = age - firedTick;
        return elapsed >= 0.0D && elapsed < 6.0D ? (float) (1.0D - elapsed / 6.0D) : 0.0F;
    }

    static float reloadMotion(float elapsed) {
        return (float) (0.5D - 0.5D * Math.cos(Math.max(0.0F, elapsed) * Math.PI / 10.0D));
    }
}
