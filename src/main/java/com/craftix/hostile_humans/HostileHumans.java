package com.craftix.hostile_humans;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.ServerSetup;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import com.craftix.hostile_humans.entity.loadout.HumanLoadoutManager;
import com.craftix.hostile_humans.entity.spawner.SpawnHandler;
import com.craftix.hostile_humans.event.EventHandler;
import com.craftix.hostile_humans.item.ModItems;
import com.craftix.hostile_humans.network.NetworkHandler;
import com.craftix.hostile_humans.sounds.ModSoundEvents;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.slf4j.Logger;

@Mod(value="hostile_humans")
public class HostileHumans {
    public static final String MOD_ID = "hostile_humans";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static List<String> patreonNames = List.of();

    public HostileHumans() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)Config.SPEC, "hostile_humans.toml");
        modEventBus.addListener(NetworkHandler::registerNetworkHandler);
        modEventBus.addListener(HostileHumans::buildCreativeTabContents);
        forgeEventBus.addListener(HostileHumans::remapUnifiedTierThreeIds);
        ModEntityType.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModSoundEvents.SOUNDS.register(modEventBus);
        modEventBus.addListener(SpawnHandler::registerSpawnPlacements);
        forgeEventBus.addListener(ServerSetup::handleServerStartingEvent);
        forgeEventBus.addListener(HumanLoadoutManager::addReloadListener);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        loadLocalNames();
    }

    private static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.HUMAN1_SPAWN_EGG);
            event.accept(ModItems.HUMAN2_SPAWN_EGG);
            event.accept(ModItems.HUMAN3_SPAWN_EGG);
            event.accept(ModItems.ROAMER_SPAWN_EGG);
        }
    }

    /** Preserve 3.1.3/3.1.4 worlds while moving tier three into the common namespace. */
    private static void remapUnifiedTierThreeIds(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<EntityType<?>> mapping
                : event.getMappings(Registries.ENTITY_TYPE, "humangunner")) {
            if (mapping.getKey().getPath().equals("tier_three_human")) {
                mapping.remap(ModEntityType.HUMAN3.get());
            }
        }
        for (MissingMappingsEvent.Mapping<Item> mapping
                : event.getMappings(Registries.ITEM, "humangunner")) {
            if (mapping.getKey().getPath().equals("tier_three_human_spawn_egg")) {
                mapping.remap(ModItems.HUMAN3_SPAWN_EGG.get());
            }
        }
    }

    /** No startup network IO. Existing local cache is optional and never rewritten. */
    private static void loadLocalNames() {
        java.nio.file.Path path = FMLPaths.GAMEDIR.get().resolve("hhpatreonnamescache.txt");
        try {
            if (!java.nio.file.Files.isRegularFile(path) || java.nio.file.Files.size(path) > 1024 * 1024) return;
            try (var lines = java.nio.file.Files.lines(path, StandardCharsets.UTF_8)) {
                patreonNames = lines.map(String::strip).filter(n -> !n.isEmpty() && n.length() <= 64)
                        .distinct().limit(4096).toList();
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to read optional local human name catalog: {}", e.getMessage());
        }
    }
}

