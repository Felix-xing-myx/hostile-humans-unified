package dev.felix.hostilehumans.observer;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/** Local display preferences only. No entity identity, server authority or gameplay configuration. */
public final class NavigationObserverPreferences {
    public record Settings(List<Integer> layers, int page) {
        public Settings {
            layers = List.copyOf(layers);
            if (layers.size() != 4 || layers.stream().anyMatch(value -> value < 0 || value > 2)
                    || page < 0 || page >= 7) throw new IllegalArgumentException("Invalid observer display preferences");
        }
    }
    static final Settings DEFAULTS = new Settings(List.of(2, 1, 1, 1), 0);
    private NavigationObserverPreferences() {}

    public static Settings load(Path path) throws IOException {
        if (!Files.exists(path)) return DEFAULTS;
        try (var reader = Files.newBufferedReader(path)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            if (integer(json.get("version")) != 1) throw new IllegalArgumentException("Unknown observer settings version");
            var layers = json.getAsJsonArray("layers");
            if (layers.size() != 4) throw new IllegalArgumentException("Expected four display layers");
            return new Settings(java.util.stream.StreamSupport.stream(layers.spliterator(), false)
                    .map(NavigationObserverPreferences::integer).toList(), integer(json.get("page")));
        } catch (RuntimeException malformed) {
            throw new IOException("Invalid observer display preferences: " + path, malformed);
        }
    }
    private static int integer(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Expected an integer");
        double number = element.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Expected a bounded integer");
        return (int) number;
    }
    public static void save(Path path, Settings settings) throws IOException {
        var json = new JsonObject();
        json.addProperty("version", 1);
        var layers = new JsonArray();
        settings.layers().forEach(layers::add);
        json.add("layers", layers);
        json.addProperty("page", settings.page());
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "observer-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unavailable) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
