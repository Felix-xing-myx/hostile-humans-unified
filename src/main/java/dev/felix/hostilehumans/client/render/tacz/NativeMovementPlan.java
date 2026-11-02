package dev.felix.hostilehumans.client.render.tacz;

import java.util.LinkedHashMap;
import java.util.Map;

/** Independent visual state, with bounded crossfades and distance-driven loops. No game/action authority. */
public final class NativeMovementPlan {
    private static final float AIMED_MOVEMENT_SCALE = 0.2F;

    /** Visual movement only. Smooth ADS entry; firing/reload channels have their own ownership. */
    public static float movementScale(float aim) {
        float progress = Float.isFinite(aim) ? Math.max(0, Math.min(1, aim)) : 0;
        float eased = progress * progress * (3 - 2 * progress);
        return 1 - (1 - AIMED_MOVEMENT_SCALE) * eased;
    }
    public record Sample(double seconds, float weight) {}
    private final Map<String, Double> lengths;
    private final Map<String, Float> weights = new LinkedHashMap<>();
    private double last = Double.NaN, runChanged = Double.NEGATIVE_INFINITY;
    private boolean wasRunning, ending;

    public NativeMovementPlan(Map<String, Double> lengths) {
        this.lengths = lengths;
        for (String name : new String[]{"idle", "walk_forward", "walk_backward", "walk_sideway", "walk_aiming",
                "run_start", "run", "run_hold", "run_end"}) if (lengths.getOrDefault(name, 0D) > 0) weights.put(name, 0F);
    }

    public Map<String, Sample> sample(double seconds, double distance, boolean moving, boolean sprinting,
            boolean grounded, float aim, double forward, double side, boolean busy, boolean firing) {
        double dt = Double.isFinite(last) ? Math.max(0, Math.min(0.25, seconds - last)) : 0;
        if (seconds < last) { weights.replaceAll((key, value) -> 0F); wasRunning = ending = false; runChanged = Double.NEGATIVE_INFINITY; }
        last = seconds;
        boolean running = moving && sprinting && aim < 0.05F && !busy && !firing;
        if (running != wasRunning) {
            ending = wasRunning && !running && !busy && !firing && aim < 0.05F;
            wasRunning = running; runChanged = seconds;
        }
        if (busy || firing || aim >= 0.05F) ending = false;
        double sinceChange = Math.max(0, seconds - runChanged);
        String selected = "idle";
        if (running) selected = sinceChange < lengths.getOrDefault("run_start", 0D) ? "run_start"
                : !grounded ? "run_hold" : "run";
        else if (ending && grounded && sinceChange < lengths.getOrDefault("run_end", 0D)) selected = "run_end";
        else if (moving && grounded && !firing) selected = aim > 0.5F ? "walk_aiming"
                : Math.abs(side) > Math.abs(forward) ? "walk_sideway" : forward < 0 ? "walk_backward" : "walk_forward";
        if (!weights.containsKey(selected)) selected = weights.containsKey("idle") ? "idle" : "";
        double fade = !busy && !firing && !grounded && (selected.equals("idle") || selected.equals("run_hold")) ? .6 : .2;
        Map<String, Sample> result = new LinkedHashMap<>();
        for (var entry : weights.entrySet()) {
            float target = !busy && entry.getKey().equals(selected) ? 1 : 0;
            float old = entry.getValue();
            float weight = (float) (old + Math.copySign(Math.min(Math.abs(target - old), dt / fade), target - old));
            entry.setValue(weight);
            if (weight <= 0) continue;
            String name = entry.getKey();
            double length = lengths.get(name);
            double sample = name.equals("run_start") || name.equals("run_end") ? Math.min(sinceChange, length)
                    : name.startsWith("walk_") || name.equals("run") ? ((distance % 2 + 2) % 2) / 2 * length
                    : seconds % length;
            float amplitude = name.startsWith("walk_") || name.startsWith("run") ? movementScale(aim) : 1;
            result.put(name, new Sample(sample, weight * amplitude));
        }
        return result;
    }
}
