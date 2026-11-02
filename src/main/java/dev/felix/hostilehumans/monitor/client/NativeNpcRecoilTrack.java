package dev.felix.hostilehumans.monitor.client;

/**
 * Native Scguns/JEG camera motion: 20% kick, 80% recovery at .15 progress per game tick. Integrate
 * the authored curve exactly across frame boundaries; never read/write a native global handler.
 */
final class NativeNpcRecoilTrack {
    static final double DURATION = 1 / (20 * .15), KICK = .20;

    record Delta(float yaw, float pitch) {}

    private double start, amplitude, previous, yawSign;

    void clear() {
        amplitude = previous = 0;
    }

    void shot(double time, double strength, boolean left) {
        if (!Double.isFinite(time) || !Double.isFinite(strength) || strength < 0)
            throw new IllegalArgumentException("recoil");
        start = time;
        amplitude = strength;
        previous = 0;
        yawSign = left ? -1 : 1;
    }

    static double shape(double progress) {
        if (progress <= 0 || progress >= 1) return 0;
        return progress < KICK ? progress / KICK : (1 - progress) / (1 - KICK);
    }

    Delta sample(double time) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("time");
        double offset = amplitude * shape(Math.max(0, (time - start) / DURATION)),
                difference = offset - previous;
        previous = offset;
        if (time - start >= DURATION) amplitude = 0;
        return new Delta((float) (yawSign * difference / 2), (float) -difference);
    }

    static double aim(double previous, double seconds, double speed, boolean aiming) {
        if (!Double.isFinite(previous)
                || !Double.isFinite(seconds)
                || seconds < 0
                || !Double.isFinite(speed)
                || speed <= 0) throw new IllegalArgumentException("aim");
        return Math.max(0, Math.min(1, previous + (aiming ? 1 : -1) * seconds * 20 * speed / 5));
    }

    static double strength(double nativeAmplitude, double reduction, double ads) {
        if (!Double.isFinite(nativeAmplitude)
                || nativeAmplitude < 0
                || !Double.isFinite(reduction)
                || !Double.isFinite(ads)) throw new IllegalArgumentException("strength");
        return Math.max(0, nativeAmplitude * (1 - reduction * Math.max(0, Math.min(1, ads))));
    }
}
