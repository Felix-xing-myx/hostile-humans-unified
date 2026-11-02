package dev.felix.hostilehumans.client.render.tacz;

import java.util.function.DoubleUnaryOperator;

final class NativeRecoilTrack {
    record Delta(float pitch, float yaw) {}

    private DoubleUnaryOperator pitch, yaw;
    private double start, previousPitch, previousYaw;

    void clear() {
        pitch = yaw = null;
        previousPitch = previousYaw = 0;
    }

    void shot(double now, DoubleUnaryOperator pitch, DoubleUnaryOperator yaw) {
        this.start = now;
        this.pitch = pitch;
        this.yaw = yaw;
        previousPitch = previousYaw = 0;
    }

    Delta sample(double now) {
        if (pitch == null || yaw == null) return new Delta(0, 0);
        double ms = Math.max(0, (now - start) * 1000);
        double p = pitch.applyAsDouble(ms), y = yaw.applyAsDouble(ms);
        if (!Double.isFinite(p) || !Double.isFinite(y)) {
            clear();
            return new Delta(0, 0);
        }
        var delta = new Delta((float) (previousPitch - p), (float) (previousYaw - y));
        previousPitch = p;
        previousYaw = y;
        return delta;
    }
}
