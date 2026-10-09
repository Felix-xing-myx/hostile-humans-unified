package com.craftix.hostile_humans;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.ServerSetup;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import com.craftix.hostile_humans.entity.spawner.SpawnHandler;
import com.craftix.hostile_humans.event.EventHandler;
import com.craftix.hostile_humans.item.ModItems;
import com.craftix.hostile_humans.item.UnifiedCreativeTab;
import com.craftix.hostile_humans.network.NetworkHandler;
import com.craftix.hostile_humans.sounds.ModSoundEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(value="hostile_humans_unified")
public class HostileHumans {
    public static final String MOD_ID = "hostile_humans_unified";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HostileHumans() {
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
        new club.someoneice.humangunner.HumanGunner();
    }
}

