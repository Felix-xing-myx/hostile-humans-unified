package dev.felix.hostilehumans.monitor.client;

/** Aspect-preserving contain geometry, shared by pack images and display-independent tests. */
public record MonitorIconFit(float x, float y, float scale) {
    public static MonitorIconFit contain(int width, int height, int boxWidth, int boxHeight) {
        if (width <= 0 || height <= 0 || boxWidth <= 0 || boxHeight <= 0)
            throw new IllegalArgumentException("Invalid image geometry");
        float scale = Math.min((float) boxWidth / width, (float) boxHeight / height);
        return new MonitorIconFit(
                Math.max(0F, (boxWidth - width * scale) / 2F),
                Math.max(0F, (boxHeight - height * scale) / 2F), scale);
    }

    /**
     * Contain geometry snapped to a whole number (or a whole divisor) so pack art keeps square
     * pixels. An arbitrary 0.297 scale resampled the HUD texture unevenly and read as blur.
     */
    public static MonitorIconFit containSnapped(int width, int height, int boxWidth, int boxHeight) {
        MonitorIconFit raw = contain(width, height, boxWidth, boxHeight);
        float scale =
                raw.scale() >= 1F
                        ? Math.max(1F, (float) Math.floor(raw.scale()))
                        : 1F / (float) Math.max(1, (int) Math.ceil(1F / raw.scale()));
        return new MonitorIconFit(
                Math.max(0F, (boxWidth - width * scale) / 2F),
                Math.max(0F, (boxHeight - height * scale) / 2F), scale);
    }
}
