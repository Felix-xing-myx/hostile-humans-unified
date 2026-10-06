package club.someoneice.humangunner;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Read-only migration source; never registers or rewrites the former Forge file. */
final class LegacyTomlConfig {
    private LegacyTomlConfig() {}
    static JsonObject read(Path configDirectory) throws IOException {
        JsonObject result = new JsonObject();
        Path file = configDirectory.resolve("hostile_humans.toml");
        if (!Files.exists(file)) return result;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            var parsed = new TomlParser().parse(reader);
            Gson gson = new Gson();
            JsonObject shared = new JsonObject();
            JsonObject ai = new JsonObject();
            for (String key : List.of("disabled_structures", "max_targeting", "no_waystones", "battle_event")) {
                Object value = parsed.get(List.of("Hostile Humans Settings", key));
                if (value != null) shared.add(key, gson.toJsonTree(value));
            }
            for (String key : List.of("flee_hp", "run_away_middle_fight_chance", "mid_battle_buff_instead_of_run_chance", "greet_chance")) {
                Object value = parsed.get(List.of("Hostile Humans Settings", key));
                if (value != null) ai.add(key, gson.toJsonTree(value));
            }
            result.add("native_gameplay", shared);
            result.add("ai", ai);
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("Could not read legacy config " + file, exception);
        }
    }
}
