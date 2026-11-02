package dev.felix.hostilehumans.faction.visual;

import dev.felix.hostilehumans.faction.IdentityBandItem;

import dev.felix.hostilehumans.bootstrap.ContentRegistry;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import java.util.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client display cache; no inventory/Curios/config reads occur during a render frame. */
public final class FactionVisualClient {
    private static FactionVisualNetwork.Definitions policy = new FactionVisualNetwork.Definitions(-1, false, false, List.of());
    private static Map<String, FactionVisualNetwork.Definition> definitions = Map.of();
    private static final Map<UUID, FactionVisualNetwork.PlayerBand> PLAYERS = new HashMap<>();
    private FactionVisualClient() { }
    public static void accept(FactionVisualNetwork.Definitions next) {
        if (next.revision() < policy.revision()) return;
        Map<String, FactionVisualNetwork.Definition> values = new HashMap<>();
        for (var entry : next.entries()) values.put(entry.id(), entry);
        definitions = Map.copyOf(values); policy = next;
    }
    public static void accept(FactionVisualNetwork.PlayerBand next) {
        var previous = PLAYERS.get(next.player());
        if (previous == null || next.revision() >= previous.revision()) PLAYERS.put(next.player(), next);
    }
    public static FactionVisualNetwork.PlayerBand player(UUID id) { return PLAYERS.get(id); }
    public static FactionVisualNetwork.Definition definition(String id) { return definitions.get(id); }
    public static int color(String id) {
        var definition = definitions.get(id);
        if (definition != null) return definition.color();
        // Resource-only fallback while awaiting the server's authoritative visual policy.
        return switch (id) {
            case "hostile_humans_unified:crimson" -> 0xA52A36;
            case "hostile_humans_unified:azure" -> 0x354D89;
            case "hostile_humans_unified:verdant" -> 0x53856B;
            case "hostile_humans_unified:ashen" -> 0x827468;
            default -> 0x929292;
        };
    }
    public static boolean humansVisible() { return policy.humansVisible(); }
    public static boolean playersVisible() { return policy.playersVisible(); }
    public static void clear() {
        PLAYERS.clear(); definitions = Map.of();
        policy = new FactionVisualNetwork.Definitions(-1, false, false, List.of());
    }
    @Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new FactionVisualLayer<>(renderer, true, skin.equals("slim")));
            }
        }
        @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
            for (var item : ContentRegistry.IDENTITY_BANDS)
                event.register((ItemStack stack, int layer) -> stack.getItem() instanceof IdentityBandItem band
                        ? layer == 0 ? 0xFF000000 | color(band.faction()) : 0xFFFFFFFF : -1, item.get());
        }
    }
    @Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
        @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
            if (event.getLevel().isClientSide && event.getEntity() instanceof AbstractClientPlayer)
                PLAYERS.remove(event.getEntity().getUUID());
        }
        @SubscribeEvent public static void unload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) PLAYERS.clear();
        }
    }
}
