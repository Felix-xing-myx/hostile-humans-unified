package com.craftix.hostile_humans.client.renderer;

/** Pure visual math. Actual completion always belongs to the synchronized reload state. */
final class TaczReloadPlayback {
    private TaczReloadPlayback() { }

    static double progress(long start, long end, double now) {
        if (end <= start || !Double.isFinite(now)) return 0;
        return Math.max(0, Math.min(.98, (now - start) / (end - start)));
    }

    static int seek(int stopTick, double progress) {
        return Math.max(0, Math.min(Math.max(0, stopTick - 2), (int) Math.floor(stopTick * progress)));
    }

    static float speed(int stopTick, int currentTick, double remainingTicks) {
        // Reserve the last keyframe until the server ends or cancels the reload.
        if (currentTick >= stopTick - 2) return 0;
        return (float) Math.max(0, Math.min(8, (stopTick - 2.0 - currentTick) / Math.max(1, remainingTicks)));
    }
}
