package com.craftix.hostile_humans;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Predicate;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.resource.PathPackResources;

@Mod.EventBusSubscriber(modid="hostile_humans", bus=Mod.EventBusSubscriber.Bus.MOD)
public class EventHandlerMod {
    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        selectPack(ModList.get()::isLoaded, Config.noWaystones.get())
                .ifPresent(name -> registerAddon(event, name));
    }

    /** One mutually exclusive overlay; no third-party classes are referenced. */
    static Optional<String> selectPack(Predicate<String> installed, boolean disableWaystones) {
        boolean craftin = installed.test("quark") && installed.test("mctb");
        boolean farmers = installed.test("farmersdelight");
        boolean waystones = installed.test("waystones") && !disableWaystones;
        String name = craftin ? "craftin" : "";
        if (farmers) name += name.isEmpty() ? "farmers" : "_farmers";
        if (waystones) name += name.isEmpty() ? "waystones" : "_waystones";
        return name.isEmpty() ? Optional.empty() : Optional.of(name);
    }

    private static void registerAddon(AddPackFindersEvent event, String packName) {
        event.addRepositorySource(packConsumer -> {
            Path path = ModList.get().getModFileById("hostile_humans").getFile()
                    .findResource("datapacks", packName);
            readAddon(path, packName).ifPresent(packConsumer);
        });
    }

    /** Missing/broken optional overlays must not prevent base-world loading. */
    static Optional<Pack> readAddon(Path path, String packName) {
        try {
            Pack pack = Pack.readMetaAndCreate("hostile_humans:" + packName,
                    Component.literal(packName), true, id -> new PathPackResources(id, true, path),
                    PackType.SERVER_DATA, Pack.Position.TOP, PackSource.DEFAULT);
            if (pack != null) return Optional.of(pack);
            LogUtils.getLogger().error("Optional structure pack {} has missing/invalid metadata at {}; using base structures", packName, path);
        } catch (RuntimeException error) {
            LogUtils.getLogger().error("Could not read optional structure pack {}; using base structures", packName, error);
        }
        return Optional.empty();
    }
}

