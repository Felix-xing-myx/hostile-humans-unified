package dev.felix.hostilehumans.monitor.client;

/**
 * Authored native motion, not gameplay feel: partition invariance, recovery, ADS and replacing a
 * shot.
 */
public final class NativeNpcRecoilTrackTest {
    private static void near(double a, double b) {
        if (Math.abs(a - b) > 2e-6) throw new AssertionError(a + " != " + b);
    }

    private static double at(int fps, double target) {
        var t = new NativeNpcRecoilTrack();
        t.shot(0, 4, true);
        double pitch = 0;
        for (int frame = 1; frame / (double) fps < target; frame++)
            pitch += t.sample(frame / (double) fps).pitch();
        pitch += t.sample(target).pitch();
        return pitch;
    }

    public static void main(String[] args) {
        for (int fps : new int[] {10, 15, 30, 60, 144, 240}) {
            near(at(fps, .1), -3.5);
            near(at(fps, NativeNpcRecoilTrack.DURATION), 0);
        }
        var t = new NativeNpcRecoilTrack();
        t.shot(0, 4, true);
        var kick = t.sample(NativeNpcRecoilTrack.DURATION * .2);
        near(kick.pitch(), -4);
        near(kick.yaw(), -2);
        var recover = t.sample(NativeNpcRecoilTrack.DURATION);
        near(recover.pitch(), 4);
        near(recover.yaw(), 2);
        near(t.sample(1).pitch(), 0);
        t.shot(2, 4, false);
        var prior = t.sample(2 + NativeNpcRecoilTrack.DURATION * .1);
        near(prior.pitch(), -2);
        t.shot(2.05, 2, true);
        near(t.sample(2.05).pitch(), 0);
        near(t.sample(2.05 + NativeNpcRecoilTrack.DURATION * .2).pitch(), -2);
        t.clear();
        near(t.sample(9).pitch(), 0);
        near(NativeNpcRecoilTrack.aim(0, .25, 1, true), 1);
        near(NativeNpcRecoilTrack.aim(1, .25, 1, false), 0);
        near(NativeNpcRecoilTrack.aim(0, .25, .5, true), .5);
        near(NativeNpcRecoilTrack.aim(.7, 0, 1, true), .7);
        near(NativeNpcRecoilTrack.strength(4, .5, 0), 4);
        near(NativeNpcRecoilTrack.strength(4, .5, .5), 3);
        near(NativeNpcRecoilTrack.strength(4, .5, 1), 2);
        near(NativeNpcRecoilTrack.strength(4, 2, 1), 0);
        System.out.println("Native NPC recoil curve/ADS/frame partition/replacement tests passed");
    }
}
