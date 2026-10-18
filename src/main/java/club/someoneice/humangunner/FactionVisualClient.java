package club.someoneice.humangunner;

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
    public static int color(String id) { var definition = definitions.get(id); return definition == null ? 0x929292 : definition.color(); }
    public static boolean humansVisible() { return policy.humansVisible(); }
    public static boolean playersVisible() { return policy.playersVisible(); }
    public static void clear() {
        PLAYERS.clear(); definitions = Map.of();
        policy = new FactionVisualNetwork.Definitions(-1, false, false, List.of());
    }
    @Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                net.minecraft.client.renderer.entity.player.PlayerRenderer renderer = event.getSkin(skin);
                if (renderer != null) renderer.addLayer(new FactionVisualLayer<>(renderer, true, skin.equals("slim")));
            }
        }
        @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Item event) {
            for (var item : HumanGunnerRegistries.IDENTITY_BANDS)
                event.register((ItemStack stack, int layer) -> stack.getItem() instanceof IdentityBandItem band
                        ? 0xFF000000 | color(band.faction()) : -1, item.get());
        }
    }
    @Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
