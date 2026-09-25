package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Locale;
import java.util.function.Supplier;

/** Small validated channel for the hired-soldier command screen. */
public final class HumanCommandNetwork {
    private static final String PROTOCOL = "5";
    private record Session(java.util.UUID human, int entityId, long expiresAt) {}
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
    }

    static void openFor(ServerPlayer player, Human human) {
        if (!HumanRelations.isOwnedBy(human, player) || player.distanceToSqr(human) > 64.0D * 64.0D) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.humangunner.command.failed"), false);
            return;
        }
        SESSIONS.put(player, new Session(human.getUUID(), human.getId(), player.serverLevel().getGameTime() + 1200L));
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenScreenPacket(human.getId(), human.getDisplayName(), SoldierOrder.get(human),
                        SoldierCombatMode.get(human), SoldierPickupPolicy.isEnabled(human)));
    }

    static void sendOrder(int entityId, SoldierOrder order) {
        CHANNEL.sendToServer(new SetOrderPacket(entityId, order));
    }

    static void sendCombatMode(int entityId, SoldierCombatMode mode) {
        CHANNEL.sendToServer(new SetCombatModePacket(entityId, mode));
    }

    static void sendDismiss(int entityId) {
        CHANNEL.sendToServer(new DismissPacket(entityId));
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
                "message.humangunner.command.failed"), false);
        return null;
    }

    private record OpenScreenPacket(int entityId, net.minecraft.network.chat.Component name,
                                    SoldierOrder order, SoldierCombatMode combatMode,
                                    boolean pickupEnabled) {
        static void encode(OpenScreenPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeComponent(packet.name);
            buffer.writeEnum(packet.order);
            buffer.writeEnum(packet.combatMode);
            buffer.writeBoolean(packet.pickupEnabled);
        }

        static OpenScreenPacket decode(FriendlyByteBuf buffer) {
            return new OpenScreenPacket(buffer.readVarInt(), buffer.readComponent(),
                    buffer.readEnum(SoldierOrder.class), buffer.readEnum(SoldierCombatMode.class),
                    buffer.readBoolean());
        }

        static void handle(OpenScreenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> SoldierCommandScreen.open(
                            packet.entityId, packet.name, packet.order, packet.combatMode,
                            packet.pickupEnabled)));
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
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.humangunner.order.changed",
                        net.minecraft.network.chat.Component.translatable(
                                "screen.humangunner.soldier." + packet.order.name().toLowerCase(Locale.ROOT))), false);
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
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.humangunner.combat_mode.changed",
                        net.minecraft.network.chat.Component.translatable(
                                "screen.humangunner.soldier.combat."
                                        + packet.mode.name().toLowerCase(Locale.ROOT))), false);
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
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.humangunner.pickup.changed",
                        net.minecraft.network.chat.Component.translatable(packet.enabled
                                ? "screen.humangunner.soldier.pickup.enabled"
                                : "screen.humangunner.soldier.pickup.disabled")), false);
            });
            context.setPacketHandled(true);
        }
    }

    private record DismissPacket(int entityId) {
        static void encode(DismissPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
        }

        static DismissPacket decode(FriendlyByteBuf buffer) {
            return new DismissPacket(buffer.readVarInt());
        }

        static void handle(DismissPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player == null || !(player.level().getEntity(packet.entityId) instanceof Human human)
                        || !HumanRelations.isOwnedBy(human, player)
                        || player.distanceToSqr(human) > 64.0D * 64.0D) return;
                Session session = SESSIONS.get(player);
                if (session == null || session.entityId() != packet.entityId
                        || !session.human().equals(human.getUUID())
                        || player.serverLevel().getGameTime() > session.expiresAt()
                        || !human.isAlive() || !CommandRateLimit.allow(player)) return;
                HumanRelations.dismiss(player, human);
                SESSIONS.remove(player);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.humangunner.dismissed.manual"), true);
            });
            context.setPacketHandled(true);
        }
    }
}
