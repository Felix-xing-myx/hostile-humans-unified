package dev.felix.hostilehumans.monitor.client;

/** Aspect-preserving contain geometry, shared by pack images and display-independent tests. */
public record MonitorIconFit(float x, float y, float scale) {
    public static MonitorIconFit contain(int width, int height, int boxWidth, int boxHeight) {
        if (width <= 0 || height <= 0 || boxWidth <= 0 || boxHeight <= 0)
            throw new IllegalArgumentException("Invalid image geometry");
        float scale = Math.min((float) boxWidth / width, (float) boxHeight / height);
        return new MonitorIconFit(
                (boxWidth - width * scale) / 2, (boxHeight - height * scale) / 2, scale);
    }
}
