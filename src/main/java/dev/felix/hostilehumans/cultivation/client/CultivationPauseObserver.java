package dev.felix.hostilehumans.cultivation.client;

import dev.felix.hostilehumans.cultivation.CultivationRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Local integrated-server identity only; pause intervals are observed even while server.execute is
 * not draining.
 */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class CultivationPauseObserver {
    private static MinecraftServer previous;
    private static boolean paused;

    private CultivationPauseObserver() {}

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        var client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();
        boolean next = server != null && client.isPaused();
        if (server != previous) {
            previous = server;
            paused = !next;
        }
        if (server != null && next != paused) {
            paused = next;
            CultivationRuntime.observeLocalPause(server, next, System.nanoTime());
        }
    }
}
