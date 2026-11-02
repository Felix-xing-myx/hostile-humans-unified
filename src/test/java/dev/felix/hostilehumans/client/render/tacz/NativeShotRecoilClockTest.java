package dev.felix.hostilehumans.client.render.tacz;

/**
 * Continuous-time cases: sub-tick frames, paused time, native tail, recoil deltas and lifecycle
 * reset.
 */
public final class NativeShotRecoilClockTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void near(double a, double b) {
        check(Math.abs(a - b) < 1e-5, "Expected " + a + " ~= " + b);
    }

    public static void main(String[] args) {
        var clock = new NativeFrameClock();
        near(clock.sample(1_000_000_000L, false), 0);
        near(clock.sample(1_008_333_333L, false), .008333333);
        near(clock.sample(8_000_000_000L, true), .008333333);
        near(clock.sample(8_008_333_333L, false), .016666666);
        var shot = new NativeShotTimeline();
        check(shot.sample(0, .2) == null, "No fabricated initial shot");
        shot.shot(.016);
        near(shot.sample(.016, .2).seconds(), 0);
        check(shot.sample(.024, .2).seconds() > 0, "120fps advances between native 20Hz ticks");
        near(shot.sample(.196, .2).weight(), .5);
        check(shot.sample(.216, .2) == null, "Tail ends at native duration");
        shot.shot(.22);
        near(shot.sample(.22, .2).seconds(), 0);
        for (int fps : new int[] {20, 60, 144}) {
            var recoil = new NativeRecoilTrack();
            recoil.shot(0, ms -> Math.min(1, ms / 100), ms -> 2 * Math.min(1, ms / 100));
            double pitch = 0, yaw = 0;
            for (int i = 0; i <= fps; i++) {
                var d = recoil.sample((double) i / fps);
                pitch += d.pitch();
                yaw += d.yaw();
            }
            near(pitch, -1);
            near(yaw, -2);
            var stable = recoil.sample(5);
            near(stable.pitch(), 0);
            near(stable.yaw(), 0);
            recoil.shot(5, ms -> Math.min(1, ms / 100), ms -> 0);
            near(recoil.sample(5).pitch(), 0);
            near(recoil.sample(5.05).pitch(), -.5);
            recoil.clear();
            near(recoil.sample(6).pitch(), 0);
        }
        System.out.println("Native shot/recoil frame clock passed");
    }
}
