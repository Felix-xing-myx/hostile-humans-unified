package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Publishes the building catalog before the world's dynamic registries are decoded. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SettlementWorldgenPack {
    private static final String ID = HumanRuntime.MOD_ID + ":building_pack_worldgen";
    private static final PackType TYPE = PackType.SERVER_DATA;

    private SettlementWorldgenPack() {}

    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() == TYPE) event.addRepositorySource(new Source());
    }

    private static final class Source implements RepositorySource {
        private boolean initialized;
        private Pack pack;

        @Override public synchronized void loadPacks(Consumer<Pack> output) {
            // A repository owns one frozen definition. /reload must not change saved structure IDs
            // or replace templates mid-generation. Reopen the world to read changed building packs.
            if (!initialized) {
                initialized = true;
                try {
                    var catalog = SettlementBuildingCatalog.load();
                    var files = SettlementWorldgenResources.compile(catalog.entries(), catalog.enabled());
                    if (!files.isEmpty()) {
                        var resources = index(files);
                        pack = Pack.readMetaAndCreate(ID, Component.literal("HHU building packs"), true,
                                name -> new Resources(name, resources), TYPE, Pack.Position.TOP, PackSource.DEFAULT);
                    }
                } catch (RuntimeException error) {
                    HumanRuntime.LOGGER.error("Could not publish building pack world generation resources", error);
                }
            }
            if (pack != null) output.accept(pack);
        }
    }

    private static Map<String, Map<ResourceLocation, byte[]>> index(Map<String, byte[]> files) {
        var namespaces = new HashMap<String, Map<ResourceLocation, byte[]>>();
        for (var file : files.entrySet()) {
            var path = file.getKey().split("/", 3);
            var id = new ResourceLocation(path[1], path[2]);
            namespaces.computeIfAbsent(id.getNamespace(), key -> new HashMap<>()).put(id, file.getValue());
        }
        namespaces.replaceAll((namespace, values) -> Map.copyOf(values));
        return Map.copyOf(namespaces);
    }

    private static final class Resources extends AbstractPackResources {
        private final Map<String, Map<ResourceLocation, byte[]>> files;

        Resources(String name, Map<String, Map<ResourceLocation, byte[]>> files) {
            super(name, true);
            this.files = files;
        }

        @Override public IoSupplier<InputStream> getRootResource(String... path) {
            if (path.length != 1 || !path[0].equals("pack.mcmeta")) return null;
            int format = SharedConstants.getCurrentVersion().getPackVersion(TYPE);
            var metadata = ("{\"pack\":{\"pack_format\":" + format + ",\"description\":\"HHU building packs\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(metadata);
        }

        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            if (type != TYPE) return null;
            byte[] bytes = files.getOrDefault(id.getNamespace(), Map.of()).get(id);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != TYPE) return;
            String prefix = path.isEmpty() ? "" : path + "/";
            files.getOrDefault(namespace, Map.of()).forEach((id, bytes) -> {
                if (id.getPath().startsWith(prefix)) output.accept(id, () -> new ByteArrayInputStream(bytes));
            });
        }

        @Override public Set<String> getNamespaces(PackType type) {
            return type == TYPE ? files.keySet() : Set.of();
        }

        @Override public void close() {}
    }
}
