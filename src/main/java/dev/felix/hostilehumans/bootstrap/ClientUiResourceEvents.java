package dev.felix.hostilehumans.bootstrap;

import dev.felix.hostilehumans.client.ui.framework.UiThemes;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only resource lifecycle; shared widgets do not subscribe separately or parse palettes per
 * frame.
 */
@Mod.EventBusSubscriber(
        modid = HostileHumansUnified.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientUiResourceEvents {
    private ClientUiResourceEvents() {}

    @SubscribeEvent
    public static void clientSetup(
            net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> {
                    dev.felix.hostilehumans.cultivation.CultivationNetwork.setSnapshotHandler(
                            dev.felix.hostilehumans.cultivation.client.CultivationScreen::show);
                    dev.felix.hostilehumans.economy.EconomyNetwork.clientReceiver(
                            dev.felix.hostilehumans.economy.client.EconomyScreen::show);
                    dev.felix.hostilehumans.settlement.SettlementNetwork.clientReceiver(
                            dev.felix.hostilehumans.settlement.client.SettlementScreen::show);
                    dev.felix.hostilehumans.settlement.SettlementNetwork.clientPreviewReceiver(
                            dev.felix.hostilehumans.settlement.client.SettlementPreviewClient::accept);
                    dev.felix.hostilehumans.settlement.SettlementNetwork.clientWandReceiver(
                            dev.felix.hostilehumans.settlement.client.SettlementWandScreen::show);
                    dev.felix.hostilehumans.settlement.SettlementNetwork.clientSelectorReceiver(
                            dev.felix.hostilehumans.settlement.client.SettlementSelectorScreen::show);
                });
    }

    @SubscribeEvent
    public static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> UiThemes.reload());
    }
}
