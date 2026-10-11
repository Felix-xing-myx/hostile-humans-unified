package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Server-side schedule from the native Gecko animation data, without loading client classes. */
final class ScgunsReloadPlan {
    record Step(int ticks, int rounds) { }
    private ScgunsReloadPlan() { }

    static List<Step> create(JsonObject animations, String prefix, boolean manual, int missing,
                             int amount, int nativeTicks, int baseTicks) {
        List<Step> steps = new ArrayList<>();
        if (missing <= 0) return List.of();
        double scale = Math.max(1, nativeTicks) / (double) Math.max(1, baseTicks);
        if (manual) {
            add(steps, length(animations, prefix + "reload_start", 0) * scale, 0);
            double loop = length(animations, prefix + "reload_loop", Math.max(1, baseTicks));
            int batch = Math.max(1, amount);
            for (int left = missing; left > 0; left -= batch)
                add(steps, loop * scale, Math.min(batch, left));
            add(steps, length(animations, prefix + "reload_stop", 0) * scale, 0);
        } else {
            add(steps, length(animations, prefix + "reload", Math.max(1, baseTicks)) * scale, missing);
        }
        return List.copyOf(steps);
    }

    private static void add(List<Step> steps, double ticks, int rounds) {
        if (ticks > 0 || rounds > 0) steps.add(new Step(Math.max(1, (int) Math.ceil(ticks - .001)), rounds));
    }

    private static double length(JsonObject animations, String key, double fallback) {
        if (animations == null || !animations.has(key) || !animations.get(key).isJsonObject()) return fallback;
        JsonObject animation = animations.getAsJsonObject(key);
        if (!animation.has("animation_length")) return fallback;
        try {
            double seconds = animation.get("animation_length").getAsDouble();
            return Double.isFinite(seconds) && seconds > 0 && seconds <= 300 ? seconds * 20 : fallback;
        } catch (RuntimeException invalid) { return fallback; }
    }
}
