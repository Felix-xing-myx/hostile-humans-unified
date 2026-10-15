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
            new ResourceLocation(HumanGunner.MOD_ID, "navigation_debug"), () -> "1", "1"::equals, "1"::equals);
    private NavigationDebugNetwork() {}
    static void register() {
        CHANNEL.registerMessage(0, Packet.class, Packet::encode, Packet::decode, Packet::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    static void send(ServerPlayer player, NavigationDebugSnapshot snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(snapshot));
    }
    private static void vector(FriendlyByteBuf out, Vec3 value) { out.writeDouble(value.x); out.writeDouble(value.y); out.writeDouble(value.z); }
    private static Vec3 vector(FriendlyByteBuf in) { return new Vec3(in.readDouble(), in.readDouble(), in.readDouble()); }
    private static int count(FriendlyByteBuf in, int maximum) {
        int count = in.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid navigation snapshot count");
        return count;
    }
    private record Packet(NavigationDebugSnapshot snapshot) {
        static void encode(Packet packet, FriendlyByteBuf out) {
            var s = packet.snapshot;
            out.writeBoolean(s != null); if (s == null) return;
            out.writeResourceLocation(s.dimension()); out.writeVarInt(s.entityId()); out.writeUUID(s.entityUuid());
            out.writeUtf(s.name(), 128); out.writeUtf(s.mode(), 32); out.writeUtf(s.state(), 64);
            out.writeUtf(s.navigator(), 64); out.writeUtf(s.budget(), 96); out.writeUtf(s.obstruction(), 64);
            out.writeFloat(s.health()); out.writeDouble(s.fallBudget()); out.writeVarInt(s.maxDrop()); out.writeDouble(s.followRange());
            out.writeBoolean(s.requested() != null); if (s.requested() != null) out.writeBlockPos(s.requested());
            out.writeBoolean(s.enemy() != null); if (s.enemy() != null) vector(out, s.enemy());
            out.writeBoolean(s.endpoint() != null); if (s.endpoint() != null) vector(out, s.endpoint());
            out.writeVarInt(s.path().size()); for (Vec3 v : s.path()) vector(out, v);
            out.writeVarInt(s.firstNode()); out.writeVarInt(s.totalNodes()); out.writeBoolean(s.complete());
            out.writeBoolean(s.truncated()); out.writeDouble(s.searchMillis());
            out.writeVarInt(s.samples().size());
            for (var sample : s.samples()) {
                vector(out, sample.position()); out.writeUtf(sample.source(), 64);
                out.writeUtf(sample.result(), 64); out.writeDouble(sample.score());
            }
        }
        static Packet decode(FriendlyByteBuf in) {
            if (!in.readBoolean()) return new Packet(null);
            var dimension = in.readResourceLocation(); int id = in.readVarInt(); var uuid = in.readUUID();
            String name = in.readUtf(128), mode = in.readUtf(32), state = in.readUtf(64),
                    navigator = in.readUtf(64), budget = in.readUtf(96), obstruction = in.readUtf(64);
            float health = in.readFloat(); double fall = in.readDouble(); int drop = in.readVarInt(); double range = in.readDouble();
            var requested = in.readBoolean() ? in.readBlockPos() : null;
            var enemy = in.readBoolean() ? vector(in) : null;
            var endpoint = in.readBoolean() ? vector(in) : null;
            int nodes = count(in, NavigationDebugSnapshot.MAX_PATH); List<Vec3> path = new ArrayList<>(nodes);
            for (int i = 0; i < nodes; i++) path.add(vector(in));
            int first = in.readVarInt(), total = in.readVarInt(); boolean complete = in.readBoolean(), truncated = in.readBoolean();
            double millis = in.readDouble(); int samples = count(in, NavigationDebugSnapshot.MAX_SAMPLES);
            List<NavigationDebugSnapshot.Sample> points = new ArrayList<>(samples);
            for (int i = 0; i < samples; i++) points.add(new NavigationDebugSnapshot.Sample(vector(in), in.readUtf(64), in.readUtf(64), in.readDouble()));
            return new Packet(new NavigationDebugSnapshot(dimension, id, uuid, name, mode, state, navigator, budget,
                    obstruction, health, fall, drop, range, requested, enemy, endpoint, List.copyOf(path), first, total,
                    complete, truncated, millis, List.copyOf(points)));
        }
        static void handle(Packet packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> NavigationDebugClient.accept(packet.snapshot)));
            context.setPacketHandled(true);
        }
    }
}
