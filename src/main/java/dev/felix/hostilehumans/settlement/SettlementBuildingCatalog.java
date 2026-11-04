package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A server startup snapshot used by placement and, separately, by the generation registry. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class SettlementBuildingCatalog {
    private static final int MAX_PACKS = 64;
    private static final long MAX_TEMPLATE_BYTES = 64L * 1024 * 1024;
    private static final String BUNDLED_ROOT = "/data/" + HumanRuntime.MOD_ID + "/building_packs/";
    private static volatile Snapshot snapshot;

    private record Snapshot(MinecraftServer owner, Map<ResourceLocation, SettlementBuildingPack.Contents> entries,
            Set<ResourceLocation> enabled) {}

    record Catalog(Map<ResourceLocation, SettlementBuildingPack.Contents> entries, Set<ResourceLocation> enabled) {}

    private SettlementBuildingCatalog() {}

    @SubscribeEvent
    public static void starting(ServerAboutToStartEvent event) {
        var catalog = load();
        snapshot = new Snapshot(event.getServer(), catalog.entries(), catalog.enabled());
    }

    static Catalog load() {
        var loader = new Loader();
        loader.readBundled();
        Path directory = SettlementBuildingPack.installedDirectory();
        loader.readExternal(directory);
        return new Catalog(Map.copyOf(loader.entries), loader.enabled(directory));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        var current = snapshot;
        if (current != null && current.owner() == event.getServer()) snapshot = null;
    }

    static Map<ResourceLocation, SettlementBuildingPack.Contents> entries(MinecraftServer server) {
        var current = snapshot;
        return current != null && current.owner() == server ? current.entries() : Map.of();
    }

    static boolean generationEnabled(MinecraftServer server, ResourceLocation id) {
        var current = snapshot;
        return current != null && current.owner() == server && current.enabled().contains(id);
    }

    private static final class Loader {
        final Map<ResourceLocation, SettlementBuildingPack.Contents> entries = new LinkedHashMap<>();
        final Set<ResourceLocation> duplicates = new HashSet<>();
        long bytes;

        void readBundled() {
            try (var input = SettlementBuildingCatalog.class.getResourceAsStream(BUNDLED_ROOT + "index.txt")) {
                if (input == null) return;
                try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    for (String name : reader.lines().limit(MAX_PACKS).toList()) {
                        if (!name.matches("[a-z0-9_.-]+\\.zip")) continue;
                        try (var pack = SettlementBuildingCatalog.class.getResourceAsStream(BUNDLED_ROOT + name)) {
                            if (pack == null) throw new IOException("listed pack is missing");
                            add(pack);
                        } catch (IOException | RuntimeException invalid) {
                            warn(name, invalid);
                        }
                    }
                }
            } catch (IOException | RuntimeException invalid) {
                warn("bundled index", invalid);
            }
        }

        void readExternal(Path directory) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory.getParent())) return;
            try (var files = Files.list(directory)) {
                List<Path> candidates = files
                        .filter(path -> path.getFileName().toString().endsWith(".zip"))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .limit(MAX_PACKS + 1L).sorted().toList();
                if (candidates.size() > MAX_PACKS) {
                    HumanRuntime.LOGGER.warn("Too many building pack files in {}", directory);
                    return;
                }
                for (Path path : candidates) {
                    try (var input = Files.newInputStream(path)) {
                        if (Files.size(path) > MAX_TEMPLATE_BYTES) throw new IOException("building pack file too large");
                        add(input);
                    } catch (IOException | RuntimeException invalid) {
                        warn(path.toString(), invalid);
                    }
                }
            } catch (IOException | RuntimeException invalid) {
                warn(directory.toString(), invalid);
            }
        }

        void add(InputStream input) throws IOException {
            var pack = SettlementBuildingPack.read(input);
            var id = new ResourceLocation(pack.manifest().template());
            if (duplicates.contains(id)) return;
            var previous = entries.remove(id);
            if (previous != null) {
                bytes -= previous.template().length;
                duplicates.add(id);
                HumanRuntime.LOGGER.warn("Ignoring duplicate building pack ID {}", id);
                return;
            }
            if (entries.size() >= MAX_PACKS || bytes + pack.template().length > MAX_TEMPLATE_BYTES)
                throw new IOException("building pack catalog limit exceeded");
            entries.put(id, pack);
            bytes += pack.template().length;
        }

        Set<ResourceLocation> enabled(Path directory) {
            Path file = directory.resolve("enabled.txt");
            if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(directory.getParent())
                    || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return Set.of();
            var selected = new HashSet<ResourceLocation>();
            var groups = new HashMap<String, SettlementBuildingPack.Group>();
            var inconsistent = new HashSet<String>();
            try (var input = Files.newInputStream(file)) {
                byte[] data = input.readNBytes(16 * 1024 + 1);
                if (data.length > 16 * 1024) throw new IOException("enable list too large");
                for (String line : new String(data, StandardCharsets.UTF_8).lines().toList()) {
                    String name = line.strip();
                    if (name.isEmpty() || name.startsWith("#")) continue;
                    var id = ResourceLocation.tryParse(name);
                    var pack = id == null ? null : entries.get(id);
                    if (pack == null || !pack.generation().rules().enabled()) {
                        HumanRuntime.LOGGER.warn("Building pack generation is unavailable for {}", name);
                        continue;
                    }
                    var author = pack.author();
                    if (author.kind() == SettlementTemplate.Kind.ATTACHMENT
                            || author.size().x() > SettlementWorldgenResources.MAX_HORIZONTAL_SIDE
                            || author.size().z() > SettlementWorldgenResources.MAX_HORIZONTAL_SIDE) {
                        HumanRuntime.LOGGER.warn("Building pack {} can only be placed manually", id);
                        continue;
                    }
                    String group = pack.generation().rules().group();
                    var previous = groups.putIfAbsent(group, pack.generation().group());
                    if (previous != null && !previous.equals(pack.generation().group())) inconsistent.add(group);
                    selected.add(id);
                }
            } catch (IOException | RuntimeException invalid) {
                warn(file.toString(), invalid);
                return Set.of();
            }
            for (String group : inconsistent)
                HumanRuntime.LOGGER.warn("Ignoring inconsistent building pack generation group {}", group);
            selected.removeIf(id -> inconsistent.contains(entries.get(id).generation().rules().group()));
            return Set.copyOf(selected);
        }
    }

    private static void warn(String source, Exception error) {
        HumanRuntime.LOGGER.warn("Could not load building pack {}", source, error);
    }
}
