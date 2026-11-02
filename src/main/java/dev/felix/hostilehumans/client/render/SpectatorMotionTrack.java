package dev.felix.hostilehumans.client.render;

/** Render-only clock and gait. Inputs are interpolated camera positions, never simulation mutations. */
public final class SpectatorMotionTrack {
    public record Frame(double seconds, double distance, double speed, double dx, double dz, double bob) {}
    private double lastTime = Double.NaN, origin, x, z, distance, speed, bob;
    private Frame frame = new Frame(0, 0, 0, 0, 0, 0);

    public Frame sample(double tick, double px, double pz, boolean grounded) {
        if (!Double.isFinite(tick) || !Double.isFinite(px) || !Double.isFinite(pz)) return frame;
        if (tick == lastTime) return frame; // World, native hands and fallback share exactly one sample.
        double dt = tick - lastTime, dx = px - x, dz = pz - z;
        if (!Double.isFinite(lastTime) || dt <= 0 || dt > 5 || dx * dx + dz * dz > 16) {
            origin = tick; distance = speed = bob = 0; dx = dz = 0; dt = 0;
        }
        double horizontal = Math.hypot(dx, dz);
        double target = dt > 0 ? horizontal / dt : 0;
        double blend = 1 - Math.pow(0.6, dt);
        speed += (target - speed) * blend;
        bob += ((grounded ? Math.min(0.1, target) : 0) - bob) * blend;
        distance += horizontal * 0.6; // Vanilla walk-distance units; continuous between ticks.
        x = px; z = pz; lastTime = tick;
        frame = new Frame((tick - origin) / 20, distance, speed, dx, dz, bob);
        return frame;
    }
}
