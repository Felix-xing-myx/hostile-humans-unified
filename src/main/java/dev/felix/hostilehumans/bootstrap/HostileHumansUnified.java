package dev.felix.hostilehumans.bootstrap;

import dev.felix.hostilehumans.bootstrap.EventHandler;

import dev.felix.hostilehumans.bootstrap.ServerSetup;
import dev.felix.hostilehumans.bootstrap.ModEntityType;
import dev.felix.hostilehumans.world.SpawnHandler;
import dev.felix.hostilehumans.bootstrap.ModItems;
import dev.felix.hostilehumans.bootstrap.UnifiedCreativeTab;
import dev.felix.hostilehumans.network.NetworkHandler;
import dev.felix.hostilehumans.audio.ModSoundEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(value="hostile_humans_unified")
public class HostileHumansUnified {
    public static final String MOD_ID = "hostile_humans_unified";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HostileHumansUnified() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        modEventBus.addListener(NetworkHandler::registerNetworkHandler);
        ModEntityType.ENTITIES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        UnifiedCreativeTab.TABS.register(modEventBus);
        ModSoundEvents.SOUNDS.register(modEventBus);
        modEventBus.addListener(SpawnHandler::registerSpawnPlacements);
        forgeEventBus.addListener(ServerSetup::handleServerStartingEvent);
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        // One Forge mod container owns both source modules and their event bus.
        new dev.felix.hostilehumans.bootstrap.HumanRuntime();
    }
}

