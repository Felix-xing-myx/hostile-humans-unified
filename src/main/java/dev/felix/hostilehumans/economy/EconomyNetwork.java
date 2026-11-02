package dev.felix.hostilehumans.economy;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.*;
import java.util.function.*;

/**
 * Transport only: typed rows, explicit request acknowledgements and a server-owned service seam.
 */
public final class EconomyNetwork {
    public static final String VERSION = "1";

    public record Request(
            UUID requestId,
            UUID token,
            int revision,
            EconomyView.Action action,
            EconomyView.Command command) {
        public Request {
            if (requestId == null
                    || requestId.equals(EconomyView.NONE)
                    || token == null
                    || revision < 0
                    || action == null
                    || command == null)
                throw new IllegalArgumentException("invalid economy request");
        }
    }

    public interface Backend {
        EconomyView.Snapshot open(
                ServerPlayer player, EconomyView.Page page, BlockPos anchor, String faction);

        EconomyView.Snapshot handle(ServerPlayer player, Request request);
    }

    private record OpenWallet() {}

    private static final class Connection {
        static final SimpleChannel CHANNEL =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(HumanRuntime.MOD_ID, "economy"),
                        () -> VERSION,
                        VERSION::equals,
                        VERSION::equals);
    }

    private static volatile Consumer<EconomyView.Snapshot> receiver = s -> {};
    private static Backend backend;
    private static boolean registered;

    private EconomyNetwork() {}

    public static void backend(Backend service) {
        backend = Objects.requireNonNull(service);
    }

    public static void clientReceiver(Consumer<EconomyView.Snapshot> callback) {
        receiver = Objects.requireNonNull(callback);
    }

    public static UUID request(
            EconomyView.Snapshot snapshot, EconomyView.Action action, EconomyView.Command command) {
        UUID id = UUID.randomUUID();
        Connection.CHANNEL.sendToServer(
                new Request(id, snapshot.token(), snapshot.revision(), action, command));
        return id;
    }

    public static void requestOpenWallet() {
        Connection.CHANNEL.sendToServer(new OpenWallet());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        Connection.CHANNEL.registerMessage(
                0,
                Request.class,
                EconomyNetwork::encode,
                EconomyNetwork::decode,
                EconomyNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(
                1,
                EconomyView.Snapshot.class,
                EconomyView::snapshot,
                EconomyView::snapshot,
                EconomyNetwork::receive,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(
                2,
                OpenWallet.class,
                (p, b) -> {},
                b -> new OpenWallet(),
                EconomyNetwork::open,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static EconomyView.Snapshot invalid(UUID token, UUID ack, String reason) {
        return new EconomyView.Snapshot(
                token,
                0,
                EconomyView.Page.WALLET,
                BlockPos.ZERO,
                "",
                false,
                false,
                shortReason(reason),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new EconomyView.PermitRow(
                        false, false, 0, 0, 0, false, false, 0, false, false, "unavailable"),
                List.of(),
                new CompoundTag(),
                ack);
    }

    public static String shortReason(String text) {
        return text == null ? "request rejected" : text.substring(0, Math.min(256, text.length()));
    }

    private static void send(ServerPlayer player, EconomyView.Snapshot snapshot) {
        if (snapshot != null)
            Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }

    private static void open(
            ServerPlayer player, EconomyView.Page page, BlockPos pos, String faction) {
        try {
            send(
                    player,
                    backend == null
                            ? invalid(
                                    EconomyView.NONE,
                                    EconomyView.NONE,
                                    "economy backend unavailable")
                            : backend.open(player, page, pos, faction));
        } catch (RuntimeException denied) {
            send(player, invalid(EconomyView.NONE, EconomyView.NONE, denied.getMessage()));
        }
    }

    public static void openWallet(ServerPlayer player) {
        open(player, EconomyView.Page.WALLET, player.blockPosition(), "");
    }

    public static void openBroker(ServerPlayer player, BlockPos pos, Currency currency) {
        open(player, EconomyView.Page.BROKER, pos, currency.faction());
    }

    public static void openFacility(ServerPlayer player, BlockPos pos, EconomyView.Page page) {
        if (page != EconomyView.Page.TRADER
                && page != EconomyView.Page.SUPPLY
                && page != EconomyView.Page.TASKS)
            throw new IllegalArgumentException("facility page");
        open(player, page, pos, "");
    }

    public static void openTest(ServerPlayer player, Currency currency) {
        open(player, EconomyView.Page.TEST, player.blockPosition(), currency.faction());
    }

    private static void encode(Request request, FriendlyByteBuf buf) {
        buf.writeUUID(request.requestId());
        buf.writeUUID(request.token());
        buf.writeVarInt(request.revision());
        buf.writeEnum(request.action());
        EconomyView.command(request.command(), buf);
    }

    private static Request decode(FriendlyByteBuf buf) {
        var result =
                new Request(
                        buf.readUUID(),
                        buf.readUUID(),
                        buf.readVarInt(),
                        buf.readEnum(EconomyView.Action.class),
                        EconomyView.command(buf));
        if (buf.readableBytes() != 0)
            throw new IllegalArgumentException("trailing economy request");
        return result;
    }

    private static void handle(Request request, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(
                () -> {
                    var player = context.getSender();
                    if (player == null) return;
                    try {
                        send(
                                player,
                                backend == null
                                        ? invalid(
                                                request.token(),
                                                request.requestId(),
                                                "economy backend unavailable")
                                        : backend.handle(player, request));
                    } catch (RuntimeException denied) {
                        send(
                                player,
                                invalid(request.token(), request.requestId(), denied.getMessage()));
                    }
                });
        context.setPacketHandled(true);
    }

    private static void receive(
            EconomyView.Snapshot snapshot, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> receiver.accept(snapshot));
        context.setPacketHandled(true);
    }

    private static void open(OpenWallet request, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(
                () -> {
                    if (context.getSender() != null) openWallet(context.getSender());
                });
        context.setPacketHandled(true);
    }
}
