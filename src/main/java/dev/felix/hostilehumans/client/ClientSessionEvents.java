package dev.felix.hostilehumans.client;

import dev.felix.hostilehumans.entity.HumansClientData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class ClientSessionEvents {
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        HumansClientData.clear();
    }
}
