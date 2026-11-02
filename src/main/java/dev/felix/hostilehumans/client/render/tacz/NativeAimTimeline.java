package dev.felix.hostilehumans.client.render.tacz;

/** One continuous presentation curve, sampled by FOV, gun pose and mouse input.
 * Targets come from the server; sampling never changes gameplay or advances a second animation clock.
 */
final class NativeAimTimeline {
    record Sample(float aim, double magnification) {}
    private static final double AIM_RESPONSE_SECONDS = 0.045;
    private static final double ZOOM_RESPONSE_SECONDS = 0.070;
    private double anchorTime = Double.NaN;
    private float anchorAim, targetAim;
    private double anchorZoom = 1, targetZoom = 1;

    Sample sample(double seconds, float aim, double zoom) {
        aim = Float.isFinite(aim) ? Math.max(0, Math.min(1, aim)) : 0;
        zoom = Double.isFinite(zoom) && zoom > 0 ? Math.max(1, zoom) : 1;
        if (!Double.isFinite(seconds)) return new Sample(0, 1);
        if (!Double.isFinite(anchorTime) || seconds < anchorTime) {
            anchorTime = seconds;
            anchorAim = targetAim = aim;
            anchorZoom = targetZoom = zoom;
        }
        Sample current = at(seconds);
        if (targetAim != aim || targetZoom != zoom) {
            anchorTime = seconds;
            anchorAim = current.aim();
            anchorZoom = current.magnification();
            targetAim = aim;
            targetZoom = zoom;
        }
        return current;
    }

    private Sample at(double seconds) {
        double elapsed = Math.max(0, seconds - anchorTime);
        float aim = (float) (targetAim + (anchorAim - targetAim) * Math.exp(-elapsed / AIM_RESPONSE_SECONDS));
        double zoom = targetZoom + (anchorZoom - targetZoom) * Math.exp(-elapsed / ZOOM_RESPONSE_SECONDS);
        return new Sample(aim, zoom);
    }
}
