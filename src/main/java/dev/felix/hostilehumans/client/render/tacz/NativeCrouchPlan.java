package dev.felix.hostilehumans.client.render.tacz;

import java.util.LinkedHashMap;
import java.util.Map;

/** TaCZ's authored slide track is its crouched hip-fire stance, not a camera roll.
 * Durations come from the loaded animation resources; fades follow the default Lua slide state. */
public final class NativeCrouchPlan {
    public record Sample(double seconds, float weight) {}
    private final Map<String, Double> lengths;
    private final Map<String, Float> weights = new LinkedHashMap<>();
    private final Map<String, Double> positions = new LinkedHashMap<>();
    private double last = Double.NaN, changed;
    private String selected = "";
    private boolean crouched;

    public NativeCrouchPlan(Map<String, Double> lengths) {
        this.lengths = lengths;
        for (String name : new String[]{"slide", "slide_idle", "slide_back"}) {
            weights.put(name, 0F);
            positions.put(name, 0D);
        }
    }

    /** Busy actions own their complete native pose. Full ADS always has exactly zero stance weight. */
    public Map<String, Sample> sample(double seconds, boolean crouching, boolean canSlide, boolean busy, float aim) {
        if (!Double.isFinite(seconds) || !Float.isFinite(aim) || busy || !canSlide || aim >= 1
                || length("slide") <= 0 || length("slide_idle") <= 0 || length("slide_back") <= 0) {
            clear();
            return Map.of();
        }
        if (seconds < last) clear();
        double dt = Double.isFinite(last) ? Math.max(0, Math.min(.25, seconds - last)) : 0;
        last = seconds;
        boolean wanted = crouching && aim <= 0;
        if (wanted != crouched) {
            crouched = wanted;
            selected = wanted ? "slide" : "slide_back";
            changed = seconds;
        }
        double elapsed = Math.max(0, seconds - changed);
        if (selected.equals("slide") && elapsed >= length("slide")) {
            selected = "slide_idle";
            changed += length("slide");
            elapsed = Math.max(0, seconds - changed);
        } else if (selected.equals("slide_back") && elapsed >= length("slide_back")) {
            selected = "";
        }
        if (!selected.isEmpty()) positions.put(selected, selected.equals("slide_idle")
                ? elapsed % length(selected) : Math.min(elapsed, length(selected)));
        double fade = selected.equals("slide") ? .5 : selected.equals("slide_idle") ? .4 : .2;
        float hip = 1 - Math.max(0, aim);
        Map<String, Sample> result = new LinkedHashMap<>();
        for (var entry : weights.entrySet()) {
            float target = entry.getKey().equals(selected) ? 1 : 0;
            float old = entry.getValue();
            float weight = (float) (old + Math.copySign(Math.min(Math.abs(target - old), dt / fade), target - old));
            entry.setValue(weight);
            if (weight > 0) result.put(entry.getKey(), new Sample(positions.get(entry.getKey()), weight * hip));
        }
        return result;
    }

    private double length(String name) {
        double value = lengths.getOrDefault(name, 0D);
        return Double.isFinite(value) && value > 0 ? value : 0;
    }

    public void clear() {
        weights.replaceAll((key, value) -> 0F);
        positions.replaceAll((key, value) -> 0D);
        last = Double.NaN;
        selected = "";
        crouched = false;
    }
}
