package dev.felix.hostilehumans.monitor.client;

import net.minecraft.world.phys.Vec3;

/** Continuous authority packets must not advance a rendered frame to the end of its tick. */
public final class MonitorPresentationTrackTest {
    private static void same(Vec3 a, Vec3 b, String message) {
        if (a.distanceToSqr(b) > 1e-20) throw new AssertionError(message + ": " + a + " / " + b);
    }
    public static void main(String[] args) {
        var track = new MonitorPresentationTrack();
        track.reconcile(new Vec3(0, 64, 0), false);
        track.advance(new Vec3(1, 64.2, .5));
        float[] partials = {0, .1F, .5F, .9F, 1};
        Vec3[] visible = new Vec3[partials.length];
        for (int i = 0; i < partials.length; i++) visible[i] = track.at(partials[i]);
        for (int packet = 0; packet < 20; packet++) {
            track.reconcile(new Vec3(1.01 + packet * .001, 64.21, .49), true);
            for (int i = 0; i < partials.length; i++) same(visible[i], track.at(partials[i]),
                    "every partialTick remains continuous across repeated authority snapshots");
        }
        track.advance(new Vec3(2, 64, 1));
        same(new Vec3(2, 64, 1).add(track.correctionFor(new Vec3(2, 64, 1))), track.at(1),
                "correction decays on simulation ticks, not network packet arrivals");
        for (int tick = 0; tick < 40; tick++) {
            Vec3 end = track.at(1);
            track.advance(new Vec3(3 + tick * .2, 64, 1));
            same(end, track.at(0), "decaying correction remains continuous at every simulation tick boundary");
            // A packet arriving partway through decay must preserve both offset endpoints.
            for (int i = 0; i < partials.length; i++) visible[i] = track.at(partials[i]);
            track.reconcile(new Vec3(3.01 + tick * .2, 64.01, 1), true);
            for (int i = 0; i < partials.length; i++) same(visible[i], track.at(partials[i]),
                    "reconciliation during offset decay preserves the entire rendered trajectory");
        }
        track.reconcile(Vec3.ZERO, false);
        track.advance(Vec3.ZERO);
        track.reconcile(new Vec3(.2, 0, 0), true);
        for (int tick = 0; tick < 12; tick++) {
            Vec3 start = track.at(1);
            track.advance(new Vec3(.2, 0, 0));
            same(start, track.at(0), "stationary correction never jumps at a tick boundary");
            double residual = -.2 * Math.pow(.5, tick + 1);
            same(new Vec3(.2 + residual, 0, 0), track.at(1), "correction keeps its bounded half-life");
            for (float partial : partials) same(track.at(0).lerp(track.at(1), partial), track.at(partial),
                    "offset decays continuously across every render fraction");
        }
        same(track.at(0), track.at(-1), "negative partial tick clamps to the first endpoint");
        same(track.at(1), track.at(2), "oversized partial tick clamps to the last endpoint");
        track.reconcile(Vec3.ZERO, false);
        track.reconcile(new Vec3(1.5, 0, 0), true);
        track.advance(new Vec3(1.5, 0, 0));
        same(Vec3.ZERO, track.at(0), "larger speed corrections preserve the last camera endpoint");
        same(new Vec3(.15, 0, 0), track.at(1), "larger corrections decay by at most 0.15 blocks per tick");
        track.reconcile(new Vec3(100, 80, -20), false);
        for (float partial : partials) same(new Vec3(100, 80, -20), track.at(partial), "discontinuity snaps without crossing intervening walls");
        track.clear();
        if (track.at(.5F) != null) throw new AssertionError("close clears all presentation history");
        System.out.println("MonitorPresentationTrackTest passed");
    }
}
