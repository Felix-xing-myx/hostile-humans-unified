package dev.felix.hostilehumans.progression;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.faction.FactionDefinitions;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Immutable specialty definitions prepared and published by the central definition reload owner. */
public final class SpecialtyData {
    public record Label(String id, Map<SpecialtyAttributes.Dimension, Double> thresholds) {
        public Label { thresholds = Map.copyOf(thresholds); }
    }
    public static final class Definitions {
        private final double[] costs, caps, bossCaps, breakthroughCaps, minimum, maximum;
        private final List<Label> labels;
        Definitions(double[] costs, double[] caps, List<Label> labels) {
            this(costs, caps, defaultBossCaps(), defaultBreakthroughCaps(), defaultMinimum(), defaultMaximum(), labels);
        }
        Definitions(double[] costs, double[] caps, double[] bossCaps, double[] breakthroughCaps,
                    double[] minimum, double[] maximum, List<Label> labels) {
            this.costs = costs.clone(); this.caps = caps.clone(); this.labels = List.copyOf(labels);
            this.bossCaps = bossCaps.clone(); this.breakthroughCaps = breakthroughCaps.clone();
            this.minimum = minimum.clone(); this.maximum = maximum.clone();
        }
        public double[] costs() { return costs.clone(); }
        public double[] caps(double fallback) {
            double[] result = caps.clone();
            for (int i = 0; i < result.length; i++) if (Double.isNaN(result[i])) result[i] = fallback;
            return result;
        }
        public List<Label> labels() { return labels; }
        public double[] caps(double ordinaryFallback, double bossFallback, boolean boss, boolean breakthrough) {
            if (!boss && !breakthrough) return caps(ordinaryFallback);
            double[] result = (boss ? bossCaps : breakthroughCaps).clone();
            double[] base = caps(ordinaryFallback);
            for (int i = 0; i < result.length; i++) if (Double.isNaN(result[i]))
                result[i] = boss ? bossFallback : Math.min(1, base[i] + .10);
            return result;
        }
        public SpecialtyBounds.Limits limits() { return new SpecialtyBounds.Limits(minimum, maximum); }
    }
    private static final ResourceLocation DIMENSIONS = new ResourceLocation(HumanRuntime.MOD_ID, "dimensions");
    private static final ResourceLocation LABELS = new ResourceLocation(HumanRuntime.MOD_ID, "labels");
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
        double[] bossCaps = fallback.bossCaps.clone(), breakthroughCaps = fallback.breakthroughCaps.clone();
        double[] minimum = fallback.minimum.clone(), maximum = fallback.maximum.clone();
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
                bossCaps[dimension.ordinal()] = optionalCap(entry, "boss_deviation_cap", bossCaps[dimension.ordinal()]);
                breakthroughCaps[dimension.ordinal()] = optionalCap(entry, "breakthrough_deviation_cap", breakthroughCaps[dimension.ordinal()]);
                minimum[dimension.ordinal()] = optionalLimit(entry, "absolute_min", minimum[dimension.ordinal()], false);
                maximum[dimension.ordinal()] = optionalLimit(entry, "absolute_max", maximum[dimension.ordinal()], true);
                if (dimension == SpecialtyAttributes.Dimension.ARMOR_PENETRATION) {
                    maximum[dimension.ordinal()] = Math.min(.45, maximum[dimension.ordinal()]);
                }
                if (dimension == SpecialtyAttributes.Dimension.SPEED) {
                    maximum[dimension.ordinal()] = Math.min(1.80, maximum[dimension.ordinal()]);
                }
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
        new SpecialtyBounds.Limits(minimum, maximum); // reject the whole reload before publication
        return new Definitions(costs, caps, bossCaps, breakthroughCaps, minimum, maximum, labels);
    }
    private static double optionalCap(JsonObject entry, String key, double fallback) {
        if (!entry.has(key) || entry.get(key).isJsonNull()) return fallback;
        double value = entry.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException(key);
        return value;
    }
    private static double optionalLimit(JsonObject entry, String key, double fallback, boolean upper) {
        if (!entry.has(key) || entry.get(key).isJsonNull()) return fallback;
        double value = entry.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < 0 || upper && value == 0) throw new IllegalArgumentException(key);
        return value;
    }
    private static double[] defaultBossCaps() {
        double[] values = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(values, Double.NaN);
        values[4] = .40; values[7] = .60; values[9] = 1.0;
        return values;
    }
    private static double[] defaultBreakthroughCaps() {
        double[] values = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(values, Double.NaN);
        return values;
    }
    private static double[] defaultMinimum() {
        double[] values = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(values, .50); values[12] = 0;
        return values;
    }
    private static double[] defaultMaximum() {
        double[] values = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(values, Double.POSITIVE_INFINITY);
        values[4] = 1.80; values[12] = .45; return values;
    }
    public static SpecialtyAttributes.Dimension dimension(String key) {
        for (var dimension : SpecialtyAttributes.Dimension.values()) if (dimension.key().equals(key)) return dimension;
        throw new IllegalArgumentException("unknown dimension " + key);
    }
    private static Definitions defaults() {
        double[] caps = new double[SpecialtyAttributes.DIMENSIONS]; Arrays.fill(caps, Double.NaN); caps[7] = .3; caps[9] = .5;
        caps[SpecialtyAttributes.Dimension.SPEED.ordinal()] = .2;
        return new Definitions(new double[]{1.2,1.1,0.5,0.4,1.2,0.4,1.25,1.45,1.0,0.6,1.1,1.1,1.2}, caps,
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
