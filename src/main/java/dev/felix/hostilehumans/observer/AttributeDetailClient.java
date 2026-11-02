package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.observer.client.AttributeDetailScreen;


import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Client lifecycle only; all numeric values arrive in server snapshots. */
public final class AttributeDetailClient {
    private AttributeDetailClient() { }
    public static void register() { MinecraftForge.EVENT_BUS.register(AttributeDetailClient.class); }
    public static void accept(UUID token, boolean opening, AttributeDetailSnapshot snapshot) {
        Minecraft minecraft=Minecraft.getInstance();
        if(opening) {
            var parent=minecraft.screen instanceof AttributeDetailScreen previous?previous.parent():minecraft.screen;
            minecraft.setScreen(new AttributeDetailScreen(parent,token,snapshot));
        } else if(minecraft.screen instanceof AttributeDetailScreen screen&&screen.token().equals(token))screen.update(snapshot);
    }
    public static void closed(UUID token) {
        if(Minecraft.getInstance().screen instanceof AttributeDetailScreen screen&&screen.token().equals(token))screen.serverClosed();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if(Minecraft.getInstance().screen instanceof AttributeDetailScreen screen)screen.disconnect();
    }
}
