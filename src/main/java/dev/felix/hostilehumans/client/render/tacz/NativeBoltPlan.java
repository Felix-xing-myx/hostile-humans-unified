package dev.felix.hostilehumans.client.render.tacz;

/** A synchronized bolt edge starts the authored clip once, as TaCZ's INPUT_BOLT does.
 * The physical bolt interval and the visual clip length are deliberately independent. */
public final class NativeBoltPlan {
    public record Sample(double seconds) {}
    private final double length;
    private boolean wasBolting;
    private double start = Double.NaN, last = Double.NaN;

    public NativeBoltPlan(double length) {
        this.length = Double.isFinite(length) && length > 0 ? length : 0;
    }

    public boolean supported() { return length > 0; }

    public Sample sample(double seconds, boolean bolting, boolean interrupted) {
        if (!supported() || !Double.isFinite(seconds) || interrupted) {
            clear();
            return null;
        }
        if (seconds < last) clear();
        last = seconds;
        if (bolting && !wasBolting) start = seconds;
        wasBolting = bolting;
        if (!Double.isFinite(start)) return null;
        double elapsed = Math.max(0, seconds - start);
        if (elapsed >= length) { start = Double.NaN; return null; }
        return new Sample(elapsed);
    }

    public void clear() {
        wasBolting = false;
        start = last = Double.NaN;
    }
}
