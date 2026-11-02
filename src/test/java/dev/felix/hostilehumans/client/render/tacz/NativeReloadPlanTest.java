package dev.felix.hostilehumans.client.render.tacz;

import java.util.Map;

/** Executable without Minecraft: resource clip selection and authoritative clock invariants. */
public final class NativeReloadPlanTest {
    public static void main(String[] args) {
        var magazine = Map.of("reload_empty", 3.05D, "reload_tactical", 2.4D,
                "reload_empty_xmag", 3.45D, "reload_tactical_xmag", 2.6667D);
        var empty = NativeReloadPlan.create(magazine, request(true, 0, 20, false));
        check(empty.sample(0).animation().equals("reload_empty"), "empty reload");
        var tactical = NativeReloadPlan.create(magazine, request(false, 1, 3, false));
        check(tactical.sample(0).animation().equals("reload_tactical_xmag"), "extended tactical");
        check(NativeReloadPlan.variant(Map.of("reload_empty", 3D, "reload_empty_xmag_23", 4D), "reload_empty", 3)
                .equals("reload_empty_xmag_23"), "grouped magazine variant");
        check(NativeReloadPlan.variant(Map.of("reload_empty", 3D, "reload_empty_xmag_2", 4D), "reload_empty", 3)
                .equals("reload_empty_xmag_2"), "authored level 2 fallback for level 3");
        var clips = Map.of("reload_intro", .5D, "reload_intro_empty", 1.8D, "reload_loop", .6667D,
                "reload_loop_2", .7D, "reload_end", .7167D);
        var manual = NativeReloadPlan.create(clips, request(true, 0, 5, true));
        check(manual.segments().size() == 5, "intro + double + double + single + end");
        check(manual.segments().get(1).animation().equals("reload_loop_2"), "paired feeding");
        check(manual.segments().get(3).animation().equals("reload_loop"), "single last round");
        check(manual.sample(manual.duration()).animation().equals("reload_end"), "holds ending until authority completes");
        check(manual.sample(manual.duration()).seconds() < .7167, "no out of bounds terminal keyframe");
        var scriptedMagazine = NativeReloadPlan.create(clips, request(true, 0, 5, false));
        check(scriptedMagazine.cyclic() && scriptedMagazine.supported(), "M1014 declares magazine but real clips define a cycle");
        check(Double.isNaN(scriptedMagazine.presentationTime(1, .67, null)), "cycle awaits real script timing rather than obsolete data cooldown");
        var feeding = new NativeReloadPlan.Phase(1, false, 0, 3.87, 0);
        double middle = scriptedMagazine.presentationTime(1, .67, feeding);
        check(scriptedMagazine.sample(middle).animation().equals("reload_intro_empty"), "obsolete .67s cooldown cannot finish intro prematurely");
        near(scriptedMagazine.presentationTime(3.87 / 2, .67, feeding), scriptedMagazine.feedingDuration()/2,
                "feeding maps its real script deadline independently");
        check(scriptedMagazine.sample(scriptedMagazine.presentationTime(100, .67, feeding)).animation().startsWith("reload_loop"),
                "feeding holds final loop until authoritative finishing");
        var finishing = new NativeReloadPlan.Phase(2, true, 3.87, 4.23, 5);
        check(scriptedMagazine.sample(scriptedMagazine.presentationTime(3.6, .67, finishing)).animation().startsWith("reload_loop"),
                "early FINISHING notification reserves the remainder of the last feeding loop");
        var endMid = scriptedMagazine.sample(scriptedMagazine.presentationTime(4.05, .67, finishing));
        check(endMid.animation().equals("reload_end"), "actual finishing phase enters end clip");
        near(endMid.seconds(), .7167/2, "authored .7167s ending maps actual .36s finishing window");
        var earlyStop = new NativeReloadPlan.Phase(2, true, 2, 2.36, 1);
        check(scriptedMagazine.sample(scriptedMagazine.presentationTime(2, .67, earlyStop)).animation().equals("reload_end"),
                "partial supply/interrupt with finishing skips unused loops");
        var tacticalCycle = NativeReloadPlan.create(clips, request(false, 0, 1, false));
        check(tacticalCycle.segments().get(0).animation().equals("reload_intro"), "partial magazine uses tactical intro");
        check(tacticalCycle.segments().get(1).animation().equals("reload_loop"), "one missing round never uses double loop");
        check(!NativeReloadPlan.hasCanonicalClips(Map.of("reload_loop", 1D)), "incomplete cycle cannot claim an audio event");
        check(!NativeReloadPlan.hasCanonicalClips(Map.of("custom_reload", 1D)), "unknown clip names cannot steal legacy one-shot");
        check(NativeReloadPlan.hasCanonicalClips(clips), "real cycle capability independent of script filename and namespace");
        double work = NativeReloadPlan.checkpointWork(10, 2, 100, 105, false);
        check(work == 20, "admitted work rate");
        check(NativeReloadPlan.checkpointWork(10, 2, 100, 120, false) == 30, "network extrapolation bounded to ten ticks");
        check(NativeReloadPlan.checkpointWork(10, 2, 100, 105, true) == 10, "pause does not progress work");
        check(NativeReloadPlan.checkpointWork(Double.NaN, 2, 100, 105, false) == 0, "invalid work rejected");
        near(empty.presentationTime(1, 2), empty.duration() / 2, "arbitrary NPC reload duration maps native clip midpoint");
        check(!NativeReloadPlan.create(Map.of("custom_reload", 2D), request(true, 0, 1, false)).supported(), "unknown naming uses fallback");
        check(!NativeReloadPlan.create(Map.of("reload_empty", 2D), request(false, 0, 1, false)).supported(), "partial ammo does not use empty-only clip");
        var bounded = NativeReloadPlan.create(clips, request(false, 0, Integer.MAX_VALUE, true));
        check(bounded.segments().size() <= 514, "malformed metadata cannot allocate unbounded loop plan");
        System.out.println("NativeReloadPlanTest passed");
    }
    private static NativeReloadPlan.Request request(boolean empty, int level, int missing, boolean manual) {
        return new NativeReloadPlan.Request(empty, level, missing, manual, "SEMI");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-6, message); }
}
