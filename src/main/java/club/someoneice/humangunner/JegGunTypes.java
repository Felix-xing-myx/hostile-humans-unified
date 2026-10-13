package club.someoneice.humangunner;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** JEG exposes grip metadata, not weapon categories. Author categories by stable registry ID. */
final class JegGunTypes {
    private JegGunTypes() {}
    static Map<String, String> parse(JsonObject object) {
        Set<String> allowed = Set.of("pistol", "magnum", "smg", "rifle", "shotgun", "sniper", "lmg", "heavy", "flamethrower", "special");
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : object.entrySet()) {
            if (ResourceLocation.tryParse(entry.getKey()) == null || !entry.getValue().isJsonPrimitive()
                    || !entry.getValue().getAsJsonPrimitive().isString()) continue;
            String type = entry.getValue().getAsString();
            if (allowed.contains(type)) result.put(entry.getKey(), type);
        }
        return Map.copyOf(result);
    }
}
