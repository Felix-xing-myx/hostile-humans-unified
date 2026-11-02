package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded creative-monitor protocol; state is server-owned, client requests are session-scoped. */
public final class HumanMonitorNetwork {
    public record State(UUID token, UUID target, int entityId, float health, float maximumHealth,
                 String name, boolean hired, int mode, boolean debug, boolean active, boolean takeover, long generation, float pendingFood) {
        public State(UUID token, UUID target, int entityId, float health, float maximumHealth, String name,
                boolean hired, int mode, boolean debug, boolean active, boolean takeover, long generation) {
            this(token, target, entityId, health, maximumHealth, name, hired, mode, debug, active, takeover, generation, 0);
        }
        public State(UUID token, UUID target, int entityId, float health, float maximumHealth, String name,
                boolean hired, int mode, boolean debug, boolean active, boolean takeover) {
            this(token, target, entityId, health, maximumHealth, name, hired, mode, debug, active, takeover, 0, 0);
        }
    }
    private static final class Connection {
        static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(HumanRuntime.MOD_ID, "human_monitor"), () -> "6", "6"::equals, "6"::equals);
    }
    private HumanMonitorNetwork() {}
    public static void register() {
        Connection.CHANNEL.registerMessage(0, State.class, HumanMonitorNetwork::write, HumanMonitorNetwork::read, HumanMonitorNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(1, Request.class, Request::encode, Request::decode, Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(2, Control.class, Control::encode, Control::decode, Control::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(3, MonitorMotionSnapshot.class, MonitorMotionSnapshot::encode,
                MonitorMotionSnapshot::decode, (packet, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> HumanMonitorClient.acceptMotion(packet)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(4, MonitorLoadoutSnapshot.class, MonitorLoadoutSnapshot::encode, MonitorLoadoutSnapshot::decode,
                (packet, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HumanMonitorClient.acceptLoadout(packet)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        Connection.CHANNEL.registerMessage(5, Selection.class, Selection::encode, Selection::decode, Selection::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        Connection.CHANNEL.registerMessage(6, MonitorWeaponSnapshot.class, MonitorWeaponSnapshot::encode, MonitorWeaponSnapshot::decode,
                (packet, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> dev.felix.hostilehumans.monitor.client.MonitorGunHud.accept(packet)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    static void sendWeapon(ServerPlayer player, MonitorWeaponSnapshot snapshot) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    static void sendMotion(ServerPlayer player, MonitorMotionSnapshot snapshot) {
        Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
    static void send(ServerPlayer player, State state) { Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state); }
    static void sendLoadout(ServerPlayer player, MonitorLoadoutSnapshot view) { Connection.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), view); }
    public static void select(UUID token, long generation, long revision, UUID weapon) { Connection.CHANNEL.sendToServer(new Selection(token, generation, revision, weapon)); }
    public static void request(UUID token, int action, boolean enabled, int mode) { Connection.CHANNEL.sendToServer(new Request(token, action, enabled, mode)); }
    public static void control(Control input) { Connection.CHANNEL.sendToServer(input); }
    static void write(State state, FriendlyByteBuf out) {
        out.writeUUID(state.token); out.writeUUID(state.target); out.writeVarInt(state.entityId);
        out.writeFloat(state.health); out.writeFloat(state.maximumHealth);
        out.writeUtf(state.name.length() > 256 ? state.name.substring(0, 256) : state.name, 256);
        out.writeBoolean(state.hired); out.writeVarInt(state.mode); out.writeBoolean(state.debug); out.writeBoolean(state.active); out.writeBoolean(state.takeover); out.writeVarLong(state.generation); out.writeFloat(state.pendingFood);
    }
    static State read(FriendlyByteBuf in) {
        var state = new State(in.readUUID(), in.readUUID(), in.readVarInt(), in.readFloat(), in.readFloat(),
                in.readUtf(256), in.readBoolean(), in.readVarInt(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readVarLong(), in.readFloat());
        if (state.generation < 0 || !Float.isFinite(state.health) || !Float.isFinite(state.maximumHealth) || state.maximumHealth <= 0
                || !Float.isFinite(state.pendingFood) || state.pendingFood < 0
                || state.mode < 0 || state.mode > 3 || in.isReadable()) throw new IllegalArgumentException("Invalid monitor state");
        return state;
    }
    private static void handle(State state, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HumanMonitorClient.accept(state)));
        context.setPacketHandled(true);
    }
    record Request(UUID token, int action, boolean enabled, int mode) {
        static void encode(Request packet, FriendlyByteBuf out) {
            out.writeUUID(packet.token); out.writeVarInt(packet.action); out.writeBoolean(packet.enabled); out.writeVarInt(packet.mode);
        }
        static Request decode(FriendlyByteBuf in) {
            var request = new Request(in.readUUID(), in.readVarInt(), in.readBoolean(), in.readVarInt());
            if (request.action < 0 || request.action > 6 || request.mode < 0 || request.mode > 3 || in.isReadable())
                throw new IllegalArgumentException("Invalid monitor operation");
            return request;
        }
        static void handle(Request packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); var sender = context.getSender();
            context.enqueueWork(() -> { if (sender != null) HumanMonitorService.request(sender, packet.token, packet.action, packet.enabled, packet.mode); });
            context.setPacketHandled(true);
        }
    }
    /** Inputs only; identity, displacement, velocity and speed remain server-owned. */
    public record Control(UUID token, float forward, float sideways, float yaw, float pitch, boolean jump, boolean sprint, boolean sneak,
                          long generation, long sequence, boolean active, boolean attack, boolean use, boolean reload, boolean supply) {
        public Control(UUID token, float forward, float sideways, float yaw, float pitch, boolean jump, boolean sprint, boolean sneak,
                long generation, long sequence, boolean active, boolean attack, boolean use, boolean reload) {
            this(token, forward, sideways, yaw, pitch, jump, sprint, sneak, generation, sequence, active, attack, use, reload, false);
        }
        public Control(UUID token, float forward, float sideways, float yaw, float pitch, boolean jump, boolean sprint, boolean sneak, long generation, long sequence) {
            this(token, forward, sideways, yaw, pitch, jump, sprint, sneak, generation, sequence, true, false, false, false, false);
        }
        public Control(UUID token, float forward, float sideways, float yaw, float pitch, boolean jump, boolean sprint, boolean sneak) {
            this(token, forward, sideways, yaw, pitch, jump, sprint, sneak, 0, 0);
        }
        public Control {
            // Entity yaw accumulates across full turns; finite angles are canonicalized, not malformed input.
            if (Float.isFinite(yaw)) yaw = net.minecraft.util.Mth.wrapDegrees(yaw);
            if (Float.isFinite(pitch)) pitch = net.minecraft.util.Mth.clamp(pitch, -90, 90);
        }
        static void encode(Control input, FriendlyByteBuf out) {
            out.writeUUID(input.token); out.writeFloat(input.forward); out.writeFloat(input.sideways);
            out.writeFloat(input.yaw); out.writeFloat(input.pitch);
            out.writeBoolean(input.jump); out.writeBoolean(input.sprint); out.writeBoolean(input.sneak);
            out.writeVarLong(input.generation); out.writeVarLong(input.sequence);
            out.writeBoolean(input.active); out.writeBoolean(input.attack); out.writeBoolean(input.use); out.writeBoolean(input.reload); out.writeBoolean(input.supply);
        }
        static Control decode(FriendlyByteBuf in) {
            var input = new Control(in.readUUID(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readVarLong(), in.readVarLong(),
                    in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean(), in.readBoolean());
            if (input.generation < 0 || input.sequence < 0 || !Float.isFinite(input.forward) || !Float.isFinite(input.sideways)
                    || !Float.isFinite(input.yaw) || !Float.isFinite(input.pitch)
                    || Math.abs(input.forward) > 1 || Math.abs(input.sideways) > 1 || in.isReadable()) throw new IllegalArgumentException("Invalid monitor input");
            return input;
        }
        static void handle(Control input, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); var sender = context.getSender();
            context.enqueueWork(() -> { if (sender != null) HumanMonitorControl.input(sender, input); });
            context.setPacketHandled(true);
        }
    }
    record Selection(UUID token, long generation, long revision, UUID weapon) {
        static void encode(Selection packet, FriendlyByteBuf out) { out.writeUUID(packet.token); out.writeVarLong(packet.generation); out.writeVarLong(packet.revision); out.writeUUID(packet.weapon); }
        static Selection decode(FriendlyByteBuf in) {
            var packet = new Selection(in.readUUID(), in.readVarLong(), in.readVarLong(), in.readUUID());
            if (packet.generation <= 0 || packet.revision < 0 || in.isReadable()) throw new IllegalArgumentException("Invalid monitor selection");
            return packet;
        }
        static void handle(Selection packet, Supplier<NetworkEvent.Context> supplier) {
            var context = supplier.get(); var player = context.getSender();
            context.enqueueWork(() -> { if (player != null) HumanMonitorService.select(player, packet.token, packet.generation, packet.revision, packet.weapon); });
            context.setPacketHandled(true);
        }
    }

}
