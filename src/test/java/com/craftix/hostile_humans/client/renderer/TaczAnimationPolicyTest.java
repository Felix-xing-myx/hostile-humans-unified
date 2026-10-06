package com.craftix.hostile_humans.client.renderer;

/** Standalone policy checks; no Minecraft, TaCZ or Player Animator runtime required. */
public final class TaczAnimationPolicyTest {
    public static void main(String[] args) {
        check(!reset(100, 101, 500, 501, 0.04D), "ordinary movement retains playback");
        check(!reset(100, 100, 500, 500, 0.0D), "multiple frames do not reset playback");
        check(reset(100, 3, 500, 501, 0.0D), "entity clock reset rebuilds layers");
        check(reset(100, 101, 500, 5, 0.0D), "world clock reset rebuilds layers");
        check(reset(100, 121, 500, 501, 0.0D), "render gap expires an old one-shot");
        check(reset(100, 101, 500, 521, 0.0D), "world gap expires paused playback");
        check(reset(100, 101, 500, 501, 25.0D), "teleport rebuilds layers");
        check(!TaczAnimationPolicy.cooldownStarted(Long.MIN_VALUE, 100), "tracking is not a shot");
        check(TaczAnimationPolicy.cooldownStarted(0, 100), "rising synchronized cooldown is a shot");
        check(!TaczAnimationPolicy.cooldownStarted(100, 50), "cooldown decay is not a shot");
        check(TaczAnimationPolicy.recoil(20, 20) == 1.0F, "new shot has recoil");
        check(TaczAnimationPolicy.recoil(26, 20) == 0.0F, "recoil releases after six ticks");
        check(TaczAnimationPolicy.recoil(2, 20) == 0.0F, "clock reset never retains recoil");
        check(TaczAnimationPolicy.reloadMotion(0) == 0.0F, "reload starts at rest");
        check(Math.abs(TaczAnimationPolicy.reloadMotion(10) - 1.0F) < 1.0E-5F,
                "reload gesture reaches magazine phase");
        System.out.println("PASS: TaCZ animation lifecycle and fallback policy");
    }

    private static boolean reset(int previousTick, int tick, long previousTime, long time, double distance) {
        return TaczAnimationPolicy.needsReset(previousTick, tick, previousTime, time, distance);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
