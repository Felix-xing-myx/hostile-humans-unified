package dev.felix.hostilehumans.loot;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Publishes the author library as an ordinary required server datapack. Fresh snapshot per reload. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LootLibraryPack {
    public static final String ID = HumanRuntime.MOD_ID + ":author_loot";
    private LootLibraryPack() {}
    @SubscribeEvent public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        event.addRepositorySource(output -> {
            Pack pack = Pack.readMetaAndCreate(ID, Component.literal("HHU author loot library"), true,
                    name -> new Resources(name, LootLibrary.files()), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.DEFAULT);
            if (pack != null) output.accept(pack);
        });
    }
    private static final class Resources extends AbstractPackResources {
        private final Map<ResourceLocation, byte[]> files;
        Resources(String name, Map<ResourceLocation, byte[]> files) { super(name, true); this.files = files; }
        @Override public IoSupplier<InputStream> getRootResource(String... path) {
            if (path.length != 1 || !path[0].equals("pack.mcmeta")) return null;
            byte[] bytes = ("{\"pack\":{\"pack_format\":" + SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA)
                    + ",\"description\":\"HHU author loot\"}}").getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
        }
        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            byte[] bytes = type == PackType.SERVER_DATA ? files.get(id) : null;
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }
        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.SERVER_DATA) return;
            String prefix = path.isEmpty() ? "" : path + "/";
            files.forEach((id, bytes) -> { if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix))
                output.accept(id, () -> new ByteArrayInputStream(bytes)); });
        }
        @Override public Set<String> getNamespaces(PackType type) {
            return type == PackType.SERVER_DATA ? Set.of(LootLibrary.NAMESPACE) : Set.of();
        }
        @Override public void close() {}
    }
}
