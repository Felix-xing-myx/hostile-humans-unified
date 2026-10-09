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

@Mod.EventBusSubscriber(modid="hostile_humans_unified", bus=Mod.EventBusSubscriber.Bus.MOD)
public class EventHandlerMod {
    @SubscribeEvent
    public static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        selectPack(ModList.get()::isLoaded, Config.noWaystones())
                .ifPresent(name -> registerAddon(event, name));
    }

    /** One mutually exclusive overlay; no third-party classes are referenced. */
    static Optional<String> selectPack(Predicate<String> installed, boolean disableWaystones) {
        // Keep this argument for the existing caller/config contract. Legacy
        // Quark/mctb and Waystones overlays remain packaged but never activated.
        return installed.test("farmersdelight") ? Optional.of("farmers") : Optional.empty();
    }

    private static void registerAddon(AddPackFindersEvent event, String packName) {
        event.addRepositorySource(packConsumer -> {
            Path path = ModList.get().getModFileById("hostile_humans_unified").getFile()
                    .findResource("datapacks", packName);
            readAddon(path, packName).ifPresent(packConsumer);
        });
    }

    /** Missing/broken optional overlays must not prevent base-world loading. */
    static Optional<Pack> readAddon(Path path, String packName) {
        try {
            Pack pack = Pack.readMetaAndCreate("hostile_humans_unified:" + packName,
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

