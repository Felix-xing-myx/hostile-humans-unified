package com.craftix.hostile_humans.client;

import com.craftix.hostile_humans.entity.data.HumansClientData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "hostile_humans", value = Dist.CLIENT)
public final class ClientSessionEvents {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HumansClientData.clear();
    }
}
