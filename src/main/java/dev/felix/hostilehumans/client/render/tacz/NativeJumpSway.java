package dev.felix.hostilehumans.client.render.tacz;

/** Observation-owned counterpart of TaCZ's player jump/landing sway. No player statics or worker threads. */
public final class NativeJumpSway {
    // Native references: upward 0.42 blocks/tick, landing -0.1; pulse release 300/150 ms.
    // Its spring advances 50 ms every ~6 ms. Express that cadence in real seconds here.
    private static final double OMEGA = 2 * Math.PI * 0.28 * (0.05 / 0.006);
    private static final double RESPONSE = 0.65 / OMEGA;
    private double last = Double.NaN, lastY, fallVelocity, progress, target, position, velocity, result;
    private double takeoffUntil = Double.NEGATIVE_INFINITY;
    private boolean lastGrounded;

    public double sample(double seconds, double y, boolean grounded, boolean enabled) {
        if (!Double.isFinite(seconds) || !Double.isFinite(y)) { clear(); return 0; }
        if (!enabled) { clear(); return 0; }
        if (seconds == last) return result;
        double dt = seconds - last;
        if (!Double.isFinite(last) || dt <= 0 || dt > .25 || Math.abs(y - lastY) > 2) {
            clear(); last = seconds; lastY = y; lastGrounded = grounded; return 0;
        }
        double currentVertical = (y - lastY) / (dt * 20); // blocks per simulation tick
        progress = Math.max(0, progress - dt / (grounded ? .15 : .3));
        if (lastGrounded && !grounded) { takeoffUntil = seconds + .1; fallVelocity = 0; }
        // Contact flags can change on a frame whose interpolated position is still the old tick endpoint.
        if (!grounded && seconds <= takeoffUntil && currentVertical > .005) {
            progress = Math.min(1, currentVertical / .42);
            takeoffUntil = Double.NEGATIVE_INFINITY;
        }
        if (!grounded) fallVelocity = Math.min(fallVelocity, currentVertical);
        else if (!lastGrounded) {
            progress = Math.min(1, Math.max(0, -Math.min(fallVelocity, currentVertical)) / .1);
            fallVelocity = 0; takeoffUntil = Double.NEGATIVE_INFINITY;
        }
        double nextTarget = -2 * progress;
        double derivative = (nextTarget - target) / dt;
        int steps = Math.max(1, (int) Math.ceil(dt * 240)); // at most 60 small closed-form steps
        double h = dt / steps, decay = Math.exp(-OMEGA * h);
        for (int i = 1; i <= steps; i++) {
            double drive = target + (nextTarget - target) * (i - .5) / steps + RESPONSE * derivative;
            double displacement = position - drive;
            double coefficient = velocity + OMEGA * displacement;
            position = drive + (displacement + coefficient * h) * decay;
            velocity = (velocity - OMEGA * coefficient * h) * decay;
        }
        target = nextTarget; last = seconds; lastY = y; lastGrounded = grounded;
        // Match native root.offsetY += -spring / 16, including its one-step presentation lead.
        result = Math.max(-.125, Math.min(.125, -(position + .006 * velocity) / 16));
        return result;
    }

    private void clear() {
        last = Double.NaN; lastY = fallVelocity = progress = target = position = velocity = result = 0;
        takeoffUntil = Double.NEGATIVE_INFINITY;
        lastGrounded = false;
    }
}
