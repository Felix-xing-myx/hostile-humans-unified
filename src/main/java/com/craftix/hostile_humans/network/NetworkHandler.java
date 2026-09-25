package com.craftix.hostile_humans.network;

import com.craftix.hostile_humans.network.message.*;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Explicit direction and bounded decoders. No global per-player packet suppression. */
public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("hostile_humans", "network"), () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    public static void registerNetworkHandler(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            INSTANCE.registerMessage(0, MessageCommandHuman.class, (m, b) -> {
                b.writeUtf(m.getHHFollowerUUID(), 36); b.writeUtf(m.getCommand(), 32);
            }, b -> new MessageCommandHuman(b.readUtf(36), b.readUtf(32)), MessageCommandHuman::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(1, MessageHumansData.class, (m, b) -> b.writeNbt(m.data()),
                    b -> new MessageHumansData(b.readNbt()), MessageHumansData::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(2, MessageHumanData.class, (m, b) -> {
                b.writeUtf(m.getHHFollowerUUID(), 36); b.writeNbt(m.getData());
            }, b -> new MessageHumanData(b.readUtf(36), b.readNbt()), MessageHumanData::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        });
    }
    public static ServerPlayer getServerPlayer(UUID id) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
    }
    public static void updateHHFollowersData(UUID owner, CompoundTag data) {
        ServerPlayer player = getServerPlayer(owner);
        if (player != null && data != null)
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MessageHumansData(data));
    }
    public static void updateHHFollowerData(UUID id, UUID owner, CompoundTag data) {
        ServerPlayer player = getServerPlayer(owner);
        if (player != null && id != null && data != null)
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new MessageHumanData(id.toString(), data));
    }
}

