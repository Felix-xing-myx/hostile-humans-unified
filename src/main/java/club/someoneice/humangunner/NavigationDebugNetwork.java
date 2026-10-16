package club.someoneice.humangunner;

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
            new ResourceLocation(HumanGunner.MOD_ID, "navigation_debug"), () -> "6", "6"::equals, "6"::equals);
    private NavigationDebugNetwork() {}
    static void register() {
        CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, CycleFocus.class, CycleFocus::encode, CycleFocus::decode, CycleFocus::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, StopFocusedTest.class, StopFocusedTest::encode, StopFocusedTest::decode, StopFocusedTest::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    static void send(ServerPlayer player, NavigationDebugFrame frame) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(frame));
    }
    static void cycleFocus(){CHANNEL.sendToServer(new CycleFocus());}
    static void stopFocusedTest(){CHANNEL.sendToServer(new StopFocusedTest());}
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
