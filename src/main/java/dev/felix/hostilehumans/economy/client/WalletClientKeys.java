package dev.felix.hostilehumans.economy.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.felix.hostilehumans.economy.EconomyNetwork;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Unbound by default. Vanilla controls own the binding and its current displayed key. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class WalletClientKeys {
    private static final KeyMapping OPEN =
            new KeyMapping(
                    "key.hostile_humans_unified.wallet",
                    InputConstants.UNKNOWN.getValue(),
                    "key.categories.hostile_humans_unified");

    private WalletClientKeys() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean requested = false;
        while (OPEN.consumeClick()) requested = true;
        var client = Minecraft.getInstance();
        if (requested && client.player != null && client.screen == null)
            EconomyNetwork.requestOpenWallet();
    }

    @Mod.EventBusSubscriber(
            modid = "hostile_humans_unified",
            value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }
}
