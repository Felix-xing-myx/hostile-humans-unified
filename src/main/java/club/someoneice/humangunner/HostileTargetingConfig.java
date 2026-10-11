package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.Set;

/** Restart-scoped classification overrides, independent of optional mod classes. */
public record HostileTargetingConfig(boolean enabled, Set<String> includedEntities, Set<String> excludedEntities) {
    static HostileTargetingConfig parse(JsonObject json) {
        return new HostileTargetingConfig(UnifiedConfig.bool(json, "enabled", true),
                ids(json, "included_entities"), ids(json, "excluded_entities"));
    }

    private static Set<String> ids(JsonObject json, String key) {
        Set<String> result = new LinkedHashSet<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement entry : json.getAsJsonArray(key))
                if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString())
                    result.add(entry.getAsString().trim());
        }
        return Set.copyOf(result);
    }
}
