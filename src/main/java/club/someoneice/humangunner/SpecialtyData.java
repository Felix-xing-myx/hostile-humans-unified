package club.someoneice.humangunner;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable specialty definitions prepared and published by the central definition reload owner. */
public final class SpecialtyData {
    public record Label(String id, Map<SpecialtyAttributes.Dimension, Double> thresholds) {
        public Label { thresholds = Map.copyOf(thresholds); }
    }
    public static final class Definitions {
        private final double[] costs, caps;
        private final List<Label> labels;
        Definitions(double[] costs, double[] caps, List<Label> labels) {
            this.costs = costs.clone(); this.caps = caps.clone(); this.labels = List.copyOf(labels);
        }
        public double[] costs() { return costs.clone(); }
        public double[] caps(double fallback) {
            double[] result = caps.clone();
            for (int i = 0; i < result.length; i++) if (Double.isNaN(result[i])) result[i] = fallback;
            return result;
        }
        public List<Label> labels() { return labels; }
    }
    private static final ResourceLocation DIMENSIONS = new ResourceLocation(HumanGunner.MOD_ID, "dimensions");
    private static final ResourceLocation LABELS = new ResourceLocation(HumanGunner.MOD_ID, "labels");
    private static volatile Definitions current = defaults();
    private SpecialtyData() { }
    public static Definitions get() {
        FactionDefinitions.Revision revision = FactionDefinitions.current();
        return revision == null ? current : revision.specialty();
    }
    /** Publish only after factions, dimensions, labels and equipment candidates all validate. */
    public static void publish(Definitions definitions) {
        current = Objects.requireNonNull(definitions);
        FactionDefinitions.publishSpecialty(current);
    }

    /** Keys are relative to the specialty directory: namespace:dimensions and namespace:labels.
     * Parsing never publishes; any invalid supplied document aborts the caller's whole revision. */
    public static Definitions parse(Map<ResourceLocation, JsonElement> files) {
        Definitions fallback = defaults();
        double[] costs = fallback.costs(), caps = fallback.caps.clone();
        if (files.containsKey(DIMENSIONS)) {
            JsonObject object = files.get(DIMENSIONS).getAsJsonObject();
            for (SpecialtyAttributes.Dimension dimension : SpecialtyAttributes.Dimension.values()) {
                if (!object.has(dimension.key())) throw new IllegalArgumentException("missing dimension " + dimension.key());
                JsonObject entry = object.getAsJsonObject(dimension.key());
                double cost = entry.get("cost").getAsDouble();
                SpecialtyAllocation.positive(cost);
                if (cost > 1000000) throw new IllegalArgumentException("excessive cost");
                costs[dimension.ordinal()] = cost;
                caps[dimension.ordinal()] = entry.has("deviation_cap") ? entry.get("deviation_cap").getAsDouble() : Double.NaN;
                double cap = caps[dimension.ordinal()];
                if (!Double.isNaN(cap) && (!Double.isFinite(cap) || cap < 0 || cap > 1))
                    throw new IllegalArgumentException("invalid cap " + dimension.key());
                // NaN in a supplied override is invalid; only omission means global fallback.
                if (entry.has("deviation_cap") && Double.isNaN(cap)) throw new IllegalArgumentException("NaN cap");
            }
        }
        List<Label> labels = fallback.labels();
        if (files.containsKey(LABELS)) {
            labels = new ArrayList<>();
            JsonObject object = files.get(LABELS).getAsJsonObject();
            if (object.size() > 64) throw new IllegalArgumentException("too many labels");
            for (var entry : object.entrySet()) {
                if (!entry.getKey().matches("[a-z0-9_]+")) throw new IllegalArgumentException("label id");
                Map<SpecialtyAttributes.Dimension, Double> thresholds = new EnumMap<>(SpecialtyAttributes.Dimension.class);
                for (var condition : entry.getValue().getAsJsonObject().entrySet()) {
                    SpecialtyAttributes.Dimension dimension = dimension(condition.getKey());
                    double value = condition.getValue().getAsDouble();
                    SpecialtyAllocation.positive(value);
                    thresholds.put(dimension, value);
                }
                if (thresholds.isEmpty()) throw new IllegalArgumentException("empty label condition");
                labels.add(new Label(entry.getKey(), thresholds));
            }
        }
        return new Definitions(costs, caps, labels);
    }
    static SpecialtyAttributes.Dimension dimension(String key) {
        for (var dimension : SpecialtyAttributes.Dimension.values()) if (dimension.key().equals(key)) return dimension;
        throw new IllegalArgumentException("unknown dimension " + key);
    }
    private static Definitions defaults() {
        double[] caps = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(caps, Double.NaN); caps[7] = .3; caps[9] = .5;
        return new Definitions(new double[]{1.2,1.4,1,.7,1.1,.4,1.25,1.3,1.2,.6,1.25,1.2,1.2}, caps,
                List.of(label("assault", "melee", 1.25),
                        new Label("resilient", Map.of(SpecialtyAttributes.Dimension.HEALTH, 1.2, SpecialtyAttributes.Dimension.ARMOR, 1.15)),
                        label("swift", "speed", 1.15), label("precise", "accuracy", 1.25),
                        label("ranged", "projectile", 1.22), label("firepower", "gun", 1.15),
                        label("perceptive", "detection", 1.25), label("regenerative", "regeneration", 1.2)));
    }
    private static Label label(String id, String dimension, double threshold) {
        return new Label(id, Map.of(dimension(dimension), threshold));
    }
}
