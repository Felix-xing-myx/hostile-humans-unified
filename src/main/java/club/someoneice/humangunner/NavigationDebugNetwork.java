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

/** Server-to-observer only. Selection and destinations use validated vanilla interaction events. */
public final class NavigationDebugNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HumanGunner.MOD_ID, "navigation_debug"), () -> "2", "2"::equals, "2"::equals);
    private NavigationDebugNetwork() {}
    static void register() {
        CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    static void send(ServerPlayer player, NavigationDebugFrame frame) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(frame));
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
