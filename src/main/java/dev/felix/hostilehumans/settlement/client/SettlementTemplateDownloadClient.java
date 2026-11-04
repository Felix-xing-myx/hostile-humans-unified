package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.settlement.SettlementTemplateTransfer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class SettlementTemplateDownloadClient {
    private SettlementTemplateDownloadClient() {}

    public static void accept(SettlementTemplateTransfer.Chunk chunk) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var message = SettlementTemplateTransfer.receive(chunk);
        if (message != null) player.sendSystemMessage(message);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        SettlementTemplateTransfer.resetClient();
    }
}
