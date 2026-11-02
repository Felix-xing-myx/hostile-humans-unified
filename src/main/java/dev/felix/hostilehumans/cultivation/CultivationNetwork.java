package dev.felix.hostilehumans.cultivation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.*;

/**
 * Independent typed protocol; server handler is installed by the cultivation runtime, UI by the
 * client.
 */
public final class CultivationNetwork {
    public enum Action {
        REFRESH,
        PREVIEW,
        START,
        PAUSE,
        RESUME,
        CANCEL,
        CONFIRM_BREAKTHROUGH,
        SET_PRIMARY,
        DEPOSIT,
        WITHDRAW,
        USE_POTENTIAL,
        EXPAND_PRIMARY
    }

    public enum Mode {
        GROW,
        REDISTRIBUTE
    }

    public record Request(
            UUID token,
            UUID requestId,
            long expectedRevision,
            UUID target,
            Action action,
            int candidateIndex,
            long previewSignature,
            Mode mode,
            boolean automatic,
            List<String> primary,
            int amount) {
        public Request {
            primary = List.copyOf(primary);
            if (token == null
                    || requestId == null
                    || target == null
                    || action == null
                    || mode == null
                    || expectedRevision < 0
                    || candidateIndex < 0
                    || candidateIndex > 2
                    || primary.size() > 5
                    || amount < 0
                    || amount > 4096)
                throw new IllegalArgumentException("Invalid training request");
        }
    }

    private static final class Connection {
        static final SimpleChannel CHANNEL =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation("hostile_humans_unified", "cultivation"),
                        () -> "2",
                        "2"::equals,
                        "2"::equals);
    }

    private static Consumer<CultivationSnapshot> snapshots = ignored -> {};
    private static BiConsumer<ServerPlayer, Request> requests =
            (p, r) ->
                    p.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    "Training backend unavailable"),
                            true);

    private CultivationNetwork() {}

    public static void setSnapshotHandler(Consumer<CultivationSnapshot> handler) {
        snapshots = Objects.requireNonNull(handler);
    }

    public static void setRequestHandler(BiConsumer<ServerPlayer, Request> handler) {
        requests = Objects.requireNonNull(handler);
    }

    public static void register() {
        Connection.CHANNEL.registerMessage(
                0,
                CultivationSnapshot.class,
                CultivationSnapshotCodec::write,
                CultivationSnapshotCodec::read,
                (s, supplier) -> {
                    var c = supplier.get();
                    c.enqueueWork(() -> snapshots.accept(s));
                    c.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(
                1,
                Request.class,
                CultivationNetwork::write,
                CultivationNetwork::read,
                (r, supplier) -> {
                    var c = supplier.get();
                    var player = c.getSender();
                    c.enqueueWork(
                            () -> {
                                if (player != null) requests.accept(player, r);
                            });
                    c.setPacketHandled(true);
                },
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(Request request) {
        Connection.CHANNEL.sendToServer(request);
    }

    public static void snapshot(ServerPlayer player, CultivationSnapshot value) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), value);
    }

    private static void write(Request r, FriendlyByteBuf b) {
        b.writeUUID(r.token());
        b.writeUUID(r.requestId());
        b.writeVarLong(r.expectedRevision());
        b.writeUUID(r.target());
        b.writeEnum(r.action());
        b.writeVarInt(r.candidateIndex());
        b.writeLong(r.previewSignature());
        b.writeEnum(r.mode());
        b.writeBoolean(r.automatic());
        b.writeVarInt(r.primary().size());
        for (String id : r.primary()) b.writeUtf(id, 64);
        b.writeVarInt(r.amount());
    }

    private static Request read(FriendlyByteBuf b) {
        UUID token = b.readUUID(), tx = b.readUUID();
        long rev = b.readVarLong();
        UUID target = b.readUUID();
        Action action = b.readEnum(Action.class);
        int candidate = b.readVarInt();
        long signature = b.readLong();
        Mode mode = b.readEnum(Mode.class);
        boolean automatic = b.readBoolean();
        int count = b.readVarInt();
        if (count < 0 || count > 5) throw new IllegalArgumentException("Training primary bound");
        List<String> primary = new ArrayList<>();
        for (int i = 0; i < count; i++) primary.add(b.readUtf(64));
        var r =
                new Request(
                        token,
                        tx,
                        rev,
                        target,
                        action,
                        candidate,
                        signature,
                        mode,
                        automatic,
                        primary,
                        b.readVarInt());
        if (b.isReadable()) throw new IllegalArgumentException("Trailing training request");
        return r;
    }
}
