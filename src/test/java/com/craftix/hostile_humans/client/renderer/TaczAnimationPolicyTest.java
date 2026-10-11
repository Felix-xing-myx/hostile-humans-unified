package com.craftix.hostile_humans.client.renderer;

/** Standalone policy checks; no Minecraft, TaCZ or Player Animator runtime required. */
public final class TaczAnimationPolicyTest {
    public static void main(String[] args) {
        check(TaczReloadPlayback.progress(100, 180, 140) == .5, "server reload timeline drives halfway seek");
        check(TaczReloadPlayback.progress(100, 180, 90) == 0, "early packets never seek backwards");
        check(TaczReloadPlayback.progress(100, 180, 200) == .98, "late script completion retains a safe final pose");
        check(TaczReloadPlayback.progress(100, 100, 110) == 0, "invalid timeline is safe");
        check(TaczReloadPlayback.seek(40, .5) == 20, "retracking or weapon return restores progress");
        check(TaczReloadPlayback.seek(40, 1) == 38, "seek cannot deactivate the animation");
        check(Math.abs(TaczReloadPlayback.speed(42, 0, 80) - .5F) < 1.0E-5F,
                "long reload plays at half speed");
        check(TaczReloadPlayback.speed(42, 0, 20) == 2, "short reload can play faster");
        check(TaczReloadPlayback.speed(42, 40, 80) == 0, "last pose waits for server completion");
        check(TaczReloadPlayback.speed(42, 20, 80) < TaczReloadPlayback.speed(42, 20, 20),
                "native extended deadline slows the remaining action without restart");
        float aim = 0;
        for (int tick = 0; tick < 6; tick++) aim = TaczAnimationPolicy.blendAim(aim, true, 1);
        check(Math.abs(aim - 1) < 1.0E-5F, "sustained intent reaches full aim between shots");
        check(TaczAnimationPolicy.blendAim(aim, true, 1) == 1, "shot intervals do not release aim");
        check(TaczAnimationPolicy.blendAim(aim, false, 1) > 0, "release blends instead of snapping");
        check(TaczAnimationPolicy.blendAim(.5F, true, 0) == .5F, "same-age render is idempotent");
        check(TaczAnimationPolicy.blendAim(.5F, true, -1) == .5F, "clock rollback cannot reverse blending");
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
