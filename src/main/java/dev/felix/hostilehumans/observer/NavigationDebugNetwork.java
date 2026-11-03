package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.observer.client.NavigationDebugClient;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Server snapshots and payload-free developer operations on the sender's own focus. */
public final class NavigationDebugNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HumanRuntime.MOD_ID, "navigation_debug"), () -> "10", "10"::equals, "10"::equals);
    private static final Map<ServerPlayer,Integer> BUDGET_REQUESTS = new WeakHashMap<>();
    private NavigationDebugNetwork() {}
    public static void requestGlobalBudget() { CHANNEL.sendToServer(new BudgetRequest()); }

    public static void register() {
        registerBudgetPackets();
        CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, CycleFocus.class, CycleFocus::encode, CycleFocus::decode, CycleFocus::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, StopFocusedTest.class, StopFocusedTest::encode, StopFocusedTest::decode, StopFocusedTest::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private static void registerBudgetPackets() {
        CHANNEL.registerMessage(3, BudgetRequest.class, (packet,out) -> {}, in -> {
            if (in.isReadable()) throw new IllegalArgumentException("Unexpected budget request payload");
            return new BudgetRequest();
        }, BudgetRequest::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, BudgetView.class, BudgetView::encode, BudgetView::decode,
                BudgetView::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer player, NavigationDebugFrame frame) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(NavigationDebugFrameCodec.normalize(frame)));
    }
    public static void cycleFocus(){CHANNEL.sendToServer(new CycleFocus());}
    public static void stopFocusedTest(){CHANNEL.sendToServer(new StopFocusedTest());}
    private record BudgetRequest() {
        static void handle(BudgetRequest packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            var player = context.getSender();
            context.enqueueWork(() -> {
                if (player == null) return;
                var server = player.serverLevel().getServer();
                int tick = server.getTickCount();
                Integer previous = BUDGET_REQUESTS.get(player);
                if (previous != null && tick >= previous && tick - previous < 15) return;
                BUDGET_REQUESTS.put(player,tick);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BudgetView(
                        player.isCreative() || player.hasPermissions(2) ? GlobalBudgetSnapshot.read(player)
                                : List.of("all|全局预算仅向创造模式玩家或管理员开放。")));
            });
            context.setPacketHandled(true);
        }
    }

    private record BudgetView(List<String> rows) {
        static void encode(BudgetView view, FriendlyByteBuf out) {
            out.writeVarInt(view.rows.size());
            for (String row : view.rows) out.writeUtf(row,256);
        }
        static BudgetView decode(FriendlyByteBuf in) {
            int count = in.readVarInt();
            if (count < 0 || count > 24) throw new IllegalArgumentException("Invalid budget row count");
            List<String> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) rows.add(in.readUtf(256));
            if (in.isReadable()) throw new IllegalArgumentException("Unexpected budget snapshot payload");
            return new BudgetView(List.copyOf(rows));
        }
        static void handle(BudgetView view, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.felix.hostilehumans.observer.client.GlobalBudgetScreen.accept(view.rows)));
            context.setPacketHandled(true);
        }
    }

    private record StopFocusedTest() {
        static void encode(StopFocusedTest packet,FriendlyByteBuf out) {}
        static StopFocusedTest decode(FriendlyByteBuf in) {
            if(in.isReadable())throw new IllegalArgumentException("Stop request must have no payload");
            return new StopFocusedTest();
        }
        static void handle(StopFocusedTest packet,Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();ServerPlayer sender=context.getSender();
            context.enqueueWork(()->{if(sender!=null)NavigationDebugService.stopFocusedTest(sender);});
            context.setPacketHandled(true);
        }
    }
    private record CycleFocus() {
        static void encode(CycleFocus packet,FriendlyByteBuf out) {}
        static CycleFocus decode(FriendlyByteBuf in) {
            if(in.isReadable())throw new IllegalArgumentException("Focus request must have no payload");
            return new CycleFocus();
        }
        static void handle(CycleFocus packet,Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();ServerPlayer sender=context.getSender();
            context.enqueueWork(()->{if(sender!=null)NavigationDebugService.cycleFromDetails(sender);});
            context.setPacketHandled(true);
        }
    }
    private record Packet(NavigationDebugFrame frame) {
        static void encode(Packet packet, FriendlyByteBuf out) { NavigationDebugFrameCodec.write(packet.frame, out); }
        static Packet decode(FriendlyByteBuf in) { return new Packet(NavigationDebugFrameCodec.read(in)); }
        static void handle(Packet packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> NavigationDebugClient.accept(packet.frame)));
            context.setPacketHandled(true);
        }
    }
}
