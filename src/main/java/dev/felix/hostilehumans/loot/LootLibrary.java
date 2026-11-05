package dev.felix.hostilehumans.loot;

import com.google.gson.JsonObject;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.fml.loading.FMLPaths;

/** Server-owned library. Bundled/foreign resources stay read-only; authors edit independent copies. */
public final class LootLibrary {
    public static final String NAMESPACE = "hhu_author";
    public static final int MAX_FILES = 2048;
    public record Document(String id, String json, String revision, String source, boolean active) {}
    private LootLibrary() {}
    public static Path directory() { return FMLPaths.CONFIGDIR.get().resolve(HumanRuntime.MOD_ID).resolve("loot_library"); }
    private static void validId(ResourceLocation id) {
        if (id.toString().length() > 256 || Arrays.stream(id.getPath().split("/", -1))
                .anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."))) throw new IllegalArgumentException("id_invalid");
    }
    private static Path file(ResourceLocation id) {
        validId(id);
        if (!id.getNamespace().equals(NAMESPACE) || id.getPath().length() > 180
                || Arrays.stream(id.getPath().split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")))
            throw new IllegalArgumentException("custom_id");
        return safe(directory().resolve("custom/data").resolve(NAMESPACE).resolve("loot_tables").resolve(id.getPath() + ".json"));
    }
    private static Path safe(Path path) {
        Path base = directory().toAbsolutePath().normalize(), target = path.toAbsolutePath().normalize();
        if (!target.startsWith(base)) throw new IllegalArgumentException("file_path");
        for (Path part = target; part != null && part.startsWith(base); part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("file_link");
        return target;
    }
    private static String read(Path path) throws IOException {
        safe(path);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(LootDocuments.MAX_BYTES + 1);
            if (bytes.length > LootDocuments.MAX_BYTES) throw new IllegalArgumentException("document_large");
            String text = new String(bytes, StandardCharsets.UTF_8);
            LootDocuments.parse(text);
            return text;
        }
    }
    private static void atomic(Path target, String text) throws IOException {
        safe(target); Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".hhu-loot-", ".tmp");
        try {
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    static Map<ResourceLocation, byte[]> files() {
        Map<ResourceLocation, byte[]> result = new TreeMap<>();
        Path root = safe(directory().resolve("custom/data").resolve(NAMESPACE).resolve("loot_tables"));
        if (!Files.isDirectory(root)) return result;
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).limit(MAX_FILES + 1L).toList()) {
                if (result.size() >= MAX_FILES) { HumanRuntime.LOGGER.warn("Author loot library exceeds {} files", MAX_FILES); break; }
                String relative = root.relativize(path).toString().replace('\\', '/');
                var id = new ResourceLocation(NAMESPACE, relative.substring(0, relative.length() - 5));
                try { result.put(new ResourceLocation(NAMESPACE, "loot_tables/" + id.getPath() + ".json"), read(file(id)).getBytes(StandardCharsets.UTF_8)); }
                catch (IOException | RuntimeException bad) { HumanRuntime.LOGGER.warn("Ignored author loot {}: {}", id, bad.toString()); }
            }
        } catch (IOException error) { HumanRuntime.LOGGER.warn("Could not index author loot", error); }
        return Map.copyOf(result);
    }
    public static List<String> ids(MinecraftServer server) {
        TreeSet<String> ids = new TreeSet<>();
        server.getLootData().getKeys(LootDataType.TABLE).forEach(id -> ids.add(id.toString()));
        files().keySet().forEach(id -> ids.add(NAMESPACE + ":" + id.getPath().substring(12, id.getPath().length() - 5)));
        return List.copyOf(ids);
    }
    public static Document document(MinecraftServer server, ResourceLocation id) throws IOException {
        validId(id);
        boolean active = server.getLootData().getKeys(LootDataType.TABLE).contains(id);
        if (id.getNamespace().equals(NAMESPACE) && Files.exists(file(id))) {
            String json = read(file(id));
            var resource = server.getResourceManager().getResource(new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json"));
            if (active && resource.isPresent()) {
                try (var input = resource.get().open()) {
                    byte[] loaded = input.readNBytes(LootDocuments.MAX_BYTES + 1);
                    active = loaded.length <= LootDocuments.MAX_BYTES && LootDocuments.digest(new String(loaded, StandardCharsets.UTF_8)).equals(LootDocuments.digest(json));
                }
            } else active = false;
            return new Document(id.toString(), json, LootDocuments.digest(json), "custom", active);
        }
        var resource = server.getResourceManager().getResource(new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json"));
        if (resource.isPresent()) {
            try (var input = resource.get().open()) {
                byte[] bytes = input.readNBytes(LootDocuments.MAX_BYTES + 1);
                if (bytes.length > LootDocuments.MAX_BYTES) throw new IllegalArgumentException("document_large");
                String json = new String(bytes, StandardCharsets.UTF_8); LootDocuments.parse(json);
                if (id.getNamespace().equals(HumanRuntime.MOD_ID)) atomic(safe(directory().resolve("official/data")
                        .resolve(id.getNamespace()).resolve("loot_tables").resolve(id.getPath() + ".json")), json);
                return new Document(id.toString(), json, LootDocuments.digest(json), resource.get().sourcePackId(), active);
            }
        }
        if (!active) throw new IllegalArgumentException("table_missing");
        // Code-registered tables have no source JSON; use the native registered serializers.
        String json = LootDataType.TABLE.parser().toJson(server.getLootData().getLootTable(id), LootTable.class);
        LootDocuments.parse(json);
        return new Document(id.toString(), json, LootDocuments.digest(json), "runtime", true);
    }
    public static void mirrorOfficial(net.minecraft.server.packs.resources.ResourceManager resources) {
        var entries = resources.listResources("loot_tables", id -> id.getNamespace().equals(HumanRuntime.MOD_ID) && id.getPath().endsWith(".json"));
        for (var entry : entries.entrySet().stream().limit(MAX_FILES).toList()) {
            try (var input = entry.getValue().open()) {
                validId(entry.getKey());
                byte[] bytes = input.readNBytes(LootDocuments.MAX_BYTES+1);
                if (bytes.length > LootDocuments.MAX_BYTES) continue;
                String text = new String(bytes, StandardCharsets.UTF_8); LootDocuments.parse(text);
                atomic(safe(directory().resolve("official/data").resolve(entry.getKey().getNamespace()).resolve(entry.getKey().getPath())), text);
            } catch (IOException | RuntimeException bad) { HumanRuntime.LOGGER.warn("Could not mirror bundled loot {}", entry.getKey(), bad); }
        }
    }
    public static LootTable validate(MinecraftServer server, ResourceLocation id, JsonObject json) {
        validId(id);
        LootTable table = LootDataType.TABLE.deserialize(id, json, server.getResourceManager()).orElseThrow(() -> new IllegalArgumentException("table_invalid"));
        if (table == null) throw new IllegalArgumentException("table_invalid");
        LootDataId<LootTable> key = new LootDataId<>(LootDataType.TABLE, id);
        LootDataResolver resolver = new LootDataResolver() {
            @Override @SuppressWarnings("unchecked") public <T> T getElement(LootDataId<T> query) {
                return query.equals(key) ? (T) table : server.getLootData().getElement(query);
            }
        };
        var context = new ValidationContext(table.getParamSet(), resolver).enterElement(id.toString(), key);
        table.validate(context);
        if (!context.getProblems().isEmpty()) throw new IllegalArgumentException("validation: " + context.getProblems());
        return table;
    }
    public static boolean containerTable(LootTable table) {
        return LootContextParamSets.CHEST.getAllowed().containsAll(table.getParamSet().getRequired());
    }
    public static Document save(MinecraftServer server, ResourceLocation id, String expected, String text) throws IOException {
        Path target = file(id);
        String current = Files.exists(target) ? read(target) : "";
        if (!Objects.equals(expected, current.isEmpty() ? "" : LootDocuments.digest(current))) throw new IllegalArgumentException("table_changed");
        JsonObject json = LootDocuments.parse(text);
        if (!containerTable(validate(server, id, json))) throw new IllegalArgumentException("not_chest");
        String pretty = LootDocuments.JSON.toJson(json); LootDocuments.parse(pretty);
        if (!Files.exists(target) && files().size() >= MAX_FILES) throw new IllegalArgumentException("library_full");
        if (!current.isEmpty()) atomic(target.resolveSibling(target.getFileName() + ".previous"), current);
        atomic(target, pretty);
        return document(server, id);
    }
    public static String export(MinecraftServer server, ResourceLocation id) throws IOException {
        var doc = document(server, id);
        Path output = safe(directory().resolve("exports/data").resolve(id.getNamespace()).resolve("loot_tables").resolve(id.getPath() + ".json"));
        atomic(output, doc.json());
        // The directory is immediately usable as a datapack or as mod src/main/resources.
        atomic(safe(directory().resolve("exports/pack.mcmeta")), "{\"pack\":{\"pack_format\":15,\"description\":\"HHU authored loot tables\"}}");
        return output.toString();
    }
}
