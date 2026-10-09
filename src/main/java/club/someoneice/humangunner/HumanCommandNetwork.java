package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Small validated channel for the hired-soldier command screen. */
public final class HumanCommandNetwork {
    private static final String PROTOCOL = "7";
    private record Session(java.util.UUID token, java.util.UUID human, int entityId,
                           ResourceKey<Level> dimension, long expiresAt, GrowthSnapshot growth) {}
    private static final java.util.Map<ServerPlayer, Session> SESSIONS = new java.util.WeakHashMap<>();
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HumanGunner.MOD_ID, "soldier_commands"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private HumanCommandNetwork() {}

    static void register() {
        CHANNEL.registerMessage(0, OpenScreenPacket.class, OpenScreenPacket::encode,
                OpenScreenPacket::decode, OpenScreenPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, SetOrderPacket.class, SetOrderPacket::encode,
                SetOrderPacket::decode, SetOrderPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SetCombatModePacket.class, SetCombatModePacket::encode,
                SetCombatModePacket::decode, SetCombatModePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, DismissPacket.class, DismissPacket::encode,
                DismissPacket::decode, DismissPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, SetPickupPolicyPacket.class, SetPickupPolicyPacket::encode,
                SetPickupPolicyPacket::decode, SetPickupPolicyPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, GrowthUpdatePacket.class, GrowthUpdatePacket::encode,
                GrowthUpdatePacket::decode, GrowthUpdatePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, CloseScreenPacket.class, CloseScreenPacket::encode,
                CloseScreenPacket::decode, CloseScreenPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, InvalidateScreenPacket.class, InvalidateScreenPacket::encode,
                InvalidateScreenPacket::decode, InvalidateScreenPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    static void openFor(ServerPlayer player, Human human) {
        if (!human.isAlive() || human.level() != player.level()
                || !HumanRelations.isOwnedBy(human, player) || player.distanceToSqr(human) > 64.0D * 64.0D) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.hostile_humans_unified.command.failed"), false);
            return;
        }
        java.util.UUID token = java.util.UUID.randomUUID();
        GrowthSnapshot growth = HumanGrowth.snapshot(human);
        SESSIONS.put(player, new Session(token, human.getUUID(), human.getId(), player.level().dimension(),
                player.serverLevel().getGameTime() + 1200L, growth));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenScreenPacket(human.getId(), human.getUUID(), human.getDisplayName(), SoldierOrder.get(human),
                        SoldierCombatMode.get(human), SoldierPickupPolicy.isEnabled(human), token, growth));
    }

    /** Called on the server thread. Only active, validated panels receive changed snapshots. */
    public static void tickServer(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        var iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer player = entry.getKey();
            Session session = entry.getValue();
            if (player.server != server) continue;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
                iterator.remove();
                continue;
            }
            Human human = humanForSession(player, session);
            if (human == null) {
                iterator.remove();
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new InvalidateScreenPacket(session.token()));
                continue;
            }
            GrowthSnapshot growth = HumanGrowth.snapshot(human);
            if (!growth.equals(session.growth())) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new GrowthUpdatePacket(session.token(), session.human(), session.entityId(), growth));
            }
            entry.setValue(new Session(session.token(), session.human(), session.entityId(), session.dimension(),
                    player.serverLevel().getGameTime() + 1200L, growth));
        }
    }

    private static Human humanForSession(ServerPlayer player, Session session) {
        if (!player.isAlive() || !player.level().dimension().equals(session.dimension())
                || player.serverLevel().getGameTime() > session.expiresAt()) return null;
        if (player.level().getEntity(session.entityId()) instanceof Human human
                && human.level() == player.level() && human.isAlive()
                && session.human().equals(human.getUUID()) && HumanRelations.isOwnedBy(human, player)
                && player.distanceToSqr(human) <= 64.0D * 64.0D) return human;
        return null;
    }

    static void closeScreen(java.util.UUID token) {
        CHANNEL.sendToServer(new CloseScreenPacket(token));
    }

    static void sendOrder(int entityId, SoldierOrder order) {
        CHANNEL.sendToServer(new SetOrderPacket(entityId, order));
    }

    static void sendCombatMode(int entityId, SoldierCombatMode mode) {
        CHANNEL.sendToServer(new SetCombatModePacket(entityId, mode));
    }

    static void sendDismiss(int entityId, java.util.UUID token) {
        CHANNEL.sendToServer(new DismissPacket(entityId, token));
    }

    static void sendPickupPolicy(int entityId, boolean enabled) {
        CHANNEL.sendToServer(new SetPickupPolicyPacket(entityId, enabled));
    }

    private static Human ownedHumanForCommand(ServerPlayer player, int entityId) {
        if (player.level().getEntity(entityId) instanceof Human human
                && human.isAlive()
                && HumanRelations.isOwnedBy(human, player)
                && player.distanceToSqr(human) <= 64.0D * 64.0D) return human;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.hostile_humans_unified.command.failed"), false);
        return null;
    }

    private record OpenScreenPacket(int entityId, java.util.UUID human, net.minecraft.network.chat.Component name,
                                    SoldierOrder order, SoldierCombatMode combatMode,
                                    boolean pickupEnabled, java.util.UUID token, GrowthSnapshot growth) {
        static void encode(OpenScreenPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeUUID(packet.human);
            buffer.writeComponent(packet.name);
            buffer.writeEnum(packet.order);
            buffer.writeEnum(packet.combatMode);
            buffer.writeBoolean(packet.pickupEnabled);
            buffer.writeUUID(packet.token);
            packet.growth.encode(buffer);
        }

        static OpenScreenPacket decode(FriendlyByteBuf buffer) {
            return new OpenScreenPacket(buffer.readVarInt(), buffer.readUUID(), buffer.readComponent(),
                    buffer.readEnum(SoldierOrder.class), buffer.readEnum(SoldierCombatMode.class),
                    buffer.readBoolean(), buffer.readUUID(), GrowthSnapshot.decode(buffer));
        }

        static void handle(OpenScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SoldierCommandScreen.open(
                            packet.entityId, packet.human, packet.name, packet.order, packet.combatMode,
                            packet.pickupEnabled, packet.token, packet.growth)));
            context.setPacketHandled(true);
        }
    }

    private record SetOrderPacket(int entityId, SoldierOrder order) {
        static void encode(SetOrderPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeEnum(packet.order);
        }

        static SetOrderPacket decode(FriendlyByteBuf buffer) {
            return new SetOrderPacket(buffer.readVarInt(), buffer.readEnum(SoldierOrder.class));
        }

        static void handle(SetOrderPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                Human human = ownedHumanForCommand(player, packet.entityId);
                if (human == null) return;
                // Ownership, range and entity identity are authoritative.
                // The short-lived screen session is not: expiry or another
                // menu opening must not silently discard an owner command.
                SoldierOrder.set(human, packet.order);
                SoldierCommandFeedback.order(player, human, packet.order);
            });
            context.setPacketHandled(true);
        }
    }

    private record SetCombatModePacket(int entityId, SoldierCombatMode mode) {
        static void encode(SetCombatModePacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeEnum(packet.mode);
        }

        static SetCombatModePacket decode(FriendlyByteBuf buffer) {
            return new SetCombatModePacket(buffer.readVarInt(), buffer.readEnum(SoldierCombatMode.class));
        }

        static void handle(SetCombatModePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                Human human = ownedHumanForCommand(player, packet.entityId);
                if (human == null) return;
                SoldierCombatMode.set(human, packet.mode);
                SoldierCommandFeedback.combat(player, human, packet.mode);
            });
            context.setPacketHandled(true);
        }
    }

    private record SetPickupPolicyPacket(int entityId, boolean enabled) {
        static void encode(SetPickupPolicyPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeBoolean(packet.enabled);
        }

        static SetPickupPolicyPacket decode(FriendlyByteBuf buffer) {
            return new SetPickupPolicyPacket(buffer.readVarInt(), buffer.readBoolean());
        }

        static void handle(SetPickupPolicyPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                Human human = ownedHumanForCommand(player, packet.entityId);
                if (human == null) return;
                SoldierPickupPolicy.set(human, packet.enabled);
                SoldierCommandFeedback.pickup(player, human, packet.enabled);
            });
            context.setPacketHandled(true);
        }
    }

    private record DismissPacket(int entityId, java.util.UUID token) {
        static void encode(DismissPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeUUID(packet.token);
        }

        static DismissPacket decode(FriendlyByteBuf buffer) {
            return new DismissPacket(buffer.readVarInt(), buffer.readUUID());
        }

        static void handle(DismissPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.level().getEntity(packet.entityId) instanceof Human human)
                        || !HumanRelations.isOwnedBy(human, player)
                        || player.distanceToSqr(human) > 64.0D * 64.0D) return;
                Session session = SESSIONS.get(player);
                if (session == null || !session.token().equals(packet.token) || session.entityId() != packet.entityId
                        || !session.human().equals(human.getUUID())
                        || humanForSession(player, session) != human
                        || !human.isAlive() || !CommandRateLimit.allow(player)) return;
                HumanRelations.dismiss(player, human);
                SESSIONS.remove(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.hostile_humans_unified.dismissed.manual"), true);
            });
            context.setPacketHandled(true);
        }
    }

    private record GrowthUpdatePacket(java.util.UUID token, java.util.UUID human, int entityId,
                                      GrowthSnapshot growth) {
        static void encode(GrowthUpdatePacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.token);
            buffer.writeUUID(packet.human);
            buffer.writeVarInt(packet.entityId);
            packet.growth.encode(buffer);
        }
        static GrowthUpdatePacket decode(FriendlyByteBuf buffer) {
            return new GrowthUpdatePacket(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(),
                    GrowthSnapshot.decode(buffer));
        }
        static void handle(GrowthUpdatePacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SoldierCommandScreen.updateGrowth(packet.token, packet.human, packet.entityId, packet.growth)));
            context.setPacketHandled(true);
        }
    }

    private record CloseScreenPacket(java.util.UUID token) {
        static void encode(CloseScreenPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.token); }
        static CloseScreenPacket decode(FriendlyByteBuf buffer) { return new CloseScreenPacket(buffer.readUUID()); }
        static void handle(CloseScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null) return;
                Session session = SESSIONS.get(player);
                if (session != null && session.token().equals(packet.token)) SESSIONS.remove(player);
            });
            context.setPacketHandled(true);
        }
    }

    private record InvalidateScreenPacket(java.util.UUID token) {
        static void encode(InvalidateScreenPacket packet, FriendlyByteBuf buffer) { buffer.writeUUID(packet.token); }
        static InvalidateScreenPacket decode(FriendlyByteBuf buffer) { return new InvalidateScreenPacket(buffer.readUUID()); }
        static void handle(InvalidateScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SoldierCommandScreen.invalidate(packet.token)));
            context.setPacketHandled(true);
        }
    }
}
