package dev.felix.hostilehumans.client.render.tacz;

/** No Minecraft world needed: probes continuity, shared sampling and frame-rate independence. */
public final class NativeAimTimelineTest {
    private static void near(double a, double b, double tolerance, String why) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a - b) > tolerance) throw new AssertionError(why + ": " + a + " != " + b);
    }
    private static NativeAimTimeline.Sample run(int fps) {
        var timeline = new NativeAimTimeline();
        timeline.sample(0, 0, 1);
        for (int tick = 0; tick < 20; tick++) {
            double start = tick / 20D, end = (tick + 1) / 20D;
            float target = tick < 10 ? Math.min(1, tick / 4F) : 0;
            double zoom = tick < 6 ? 4 : 12;
            var before = timeline.sample(start, target, zoom);
            // Calling from the gun, FOV and mouse at the same instant must not advance it three times.
            near(before.aim(), timeline.sample(start, target, zoom).aim(), 0, "shared sample");
            for (int frame = 1; frame < fps; frame++) {
                double time = start + frame / (double) fps;
                if (time >= end) break;
                timeline.sample(time, target, zoom);
            }
        }
        return timeline.sample(1, 0, 12);
    }
    public static void main(String[] args) {
        var timeline = new NativeAimTimeline();
        timeline.sample(0, 0, 4);
        near(timeline.sample(.05, 1, 4).aim(), 0, 0, "ADS target arrival is continuous");
        float last = 0;
        for (int i = 1; i <= 24; i++) {
            float next = timeline.sample(.05 + i / 240D, 1, 4).aim();
            if (next <= last) throw new AssertionError("ADS changes every render frame, not every tick");
            last = next;
        }
        var before = timeline.sample(.2, 1, 4);
        var changed = timeline.sample(.2, 0, 16);
        near(before.aim(), changed.aim(), 0, "release has no jump");
        near(before.magnification(), changed.magnification(), 0, "zoom mode change has no jump");
        var frozen = timeline.sample(.2, 0, 16);
        near(changed.aim(), frozen.aim(), 0, "paused time is frozen");
        var reference = run(30);
        for (int fps : new int[]{60, 144, 240}) {
            var sample = run(fps);
            near(reference.aim(), sample.aim(), 1e-7, "aim is frame-rate independent");
            near(reference.magnification(), sample.magnification(), 1e-12, "zoom is frame-rate independent");
        }
        var invalid = new NativeAimTimeline().sample(0, Float.NaN, Double.NaN);
        near(invalid.aim(), 0, 0, "invalid aim fails neutral");
        near(invalid.magnification(), 1, 0, "invalid zoom fails neutral");
        System.out.println("NativeAimTimelineTest passed");
    }
}
