package dev.felix.hostilehumans.settlement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

/** Shared file contract for exported, installed and bundled building packs. */
final class SettlementBuildingPack {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MANIFEST = "manifest.json";
    private static final String TEMPLATE = "structure.nbt";
    private static final String GENERATION = "generation.json";
    private static final int MAX_JSON = 64 * 1024;
    private static final String LOOT_PREFIX = "loot_tables/";
    private static final int MAX_LOOT_FILES = 16;

    record Manifest(int schema, String template, String sha256) {
        Manifest {
            var id = ResourceLocation.tryParse(template == null ? "" : template);
            if (schema != 1 || id == null || !id.toString().equals(template) || template.length() > 256
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("invalid building pack manifest");
            SettlementTemplateLibrary.validateId(id);
        }
    }

    record Group(int spacing, int separation, int salt) {
        Group {
            if (spacing < 2 || spacing > 4096 || separation < 1 || separation >= spacing || salt < 0)
                throw new IllegalArgumentException("invalid building pack generation group");
        }
    }

    record Generation(int schema, SettlementGenerationRules rules, Group group) {
        Generation {
            if (schema != 1 || rules == null || group == null)
                throw new IllegalArgumentException("invalid building pack generation definition");
        }
    }

    record Contents(Manifest manifest, Generation generation, byte[] template, Map<ResourceLocation, JsonObject> loot, SettlementTemplate author) {}

    private SettlementBuildingPack() {}

    static Path installedDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(HumanRuntime.MOD_ID).resolve("building_packs");
    }

    static Path exportFile(ResourceLocation id) {
        Path directory = installedDirectory().resolveSibling("building_exports");
        SettlementTemplateLibrary.validateId(id);
        Path file = directory.resolve(id.getNamespace()).resolve(id.getPath() + ".zip");
        for (Path part = file; part != null && part.startsWith(directory.getParent()); part = part.getParent())
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("building pack symlinks are not allowed");
        return file;
    }

    static String export(ServerPlayer player, net.minecraft.core.BlockPos anchor, int revision, boolean overwrite) {
        var information = SettlementAuthoring.information(player, anchor);
        if (information == null || information.revision() != revision) return "author unavailable or stale";
        var draft = SettlementCodec.template(information.author());
        var id = new ResourceLocation(draft.template());
        try {
            Path saved = SettlementTemplateLibrary.path(id);
            byte[] bytes;
            try (var input = Files.newInputStream(saved)) {
                bytes = bounded(input, (int) SettlementTemplateLibrary.MAX_COMPRESSED_BYTES);
            }
            CompoundTag data = SettlementTemplateLibrary.read(bytes);
            var savedAuthor = SettlementTemplateFiles.validatePackedTemplate(data);
            if (!savedAuthor.template().equals(draft.template()) || savedAuthor.revision() != draft.revision()
                    || !savedAuthor.generation().equals(draft.generation()))
                return "save the current draft as NBT before exporting its building pack";
            var manifest = new Manifest(1, id.toString(), digest(bytes));
            var generation = new Generation(1, savedAuthor.generation(),
                    new Group(32, 8, savedAuthor.generation().group().hashCode() & Integer.MAX_VALUE));
            var output = exportFile(id);
            if (!overwrite && Files.exists(output)) return "building pack already exists";
            Files.createDirectories(output.getParent());
            Path staging = Files.createTempFile(output.getParent(), ".hhu-pack-", ".tmp");
            try {
                try (var zip = new ZipOutputStream(Files.newOutputStream(staging))) {
                    write(zip, MANIFEST, json(manifest));
                    write(zip, TEMPLATE, bytes);
                    write(zip, GENERATION, json(generation));
                }
                try (var input = Files.newInputStream(staging)) {
                    read(input);
                }
                if (overwrite)
                    Files.move(staging, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(staging, output);
            } finally {
                Files.deleteIfExists(staging);
            }
            return "exported building pack " + id;
        } catch (IOException | IllegalArgumentException error) {
            HumanRuntime.LOGGER.warn("Could not export building pack {}", id, error);
            return "could not export building pack: " + error.getMessage();
        }
    }

    static Contents read(InputStream input) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> loot = new LinkedHashMap<>();
        try (var zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String name = entry.getName();
                if (entry.isDirectory()) throw new IOException("unexpected directory entry");
                if (name.equals(TEMPLATE) || name.equals(MANIFEST) || name.equals(GENERATION)) {
                    int limit = name.equals(TEMPLATE) ? (int) SettlementTemplateLibrary.MAX_COMPRESSED_BYTES : MAX_JSON;
                    if (files.containsKey(name)) throw new IOException("duplicate building pack entry");
                    files.put(name, bounded(zip, limit));
                } else if (name.startsWith(LOOT_PREFIX) && name.endsWith(".json") && loot.size() < MAX_LOOT_FILES) {
                    var id = lootId(name);
                    if (loot.containsKey(id)) throw new IOException("duplicate loot table");
                    loot.put(id, parse(bounded(zip, MAX_JSON), JsonObject.class));
                } else throw new IOException("unexpected building pack entry");
            }
        }
        var manifest = parse(files.get(MANIFEST), Manifest.class);
        var generation = parse(files.get(GENERATION), Generation.class);
        byte[] template = files.get(TEMPLATE);
        if (template == null || !digest(template).equals(manifest.sha256()))
            throw new IOException("building pack template checksum mismatch");
        var author = SettlementTemplateFiles.validatePackedTemplate(SettlementTemplateLibrary.read(template));
        if (!author.template().equals(manifest.template()))
            throw new IOException("building pack template ID mismatch");
        return new Contents(manifest, generation, template, Map.copyOf(loot), author);
    }

    private static ResourceLocation lootId(String name) throws IOException {
        String path = name.substring(LOOT_PREFIX.length(), name.length() - 5);
        int separator = path.indexOf('/');
        if (separator <= 0) throw new IOException("loot table must include a namespace");
        var id = ResourceLocation.tryParse(path.substring(0, separator) + ":" + path.substring(separator + 1));
        if (id == null || id.toString().length() > 256) throw new IOException("invalid loot table ID");
        SettlementTemplateLibrary.validateId(id);
        return id;
    }

    private static byte[] bounded(InputStream input, int limit) throws IOException {
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) throw new IOException("building pack entry exceeds byte limit");
        return bytes;
    }

    private static <T> T parse(byte[] bytes, Class<T> type) throws IOException {
        if (bytes == null) throw new IOException("missing building pack entry");
        try {
            T value = JSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("empty building pack JSON");
            return value;
        } catch (RuntimeException invalid) {
            throw new IOException("invalid building pack JSON", invalid);
        }
    }

    private static byte[] json(Object value) {
        return JSON.toJson(value).getBytes(StandardCharsets.UTF_8);
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
