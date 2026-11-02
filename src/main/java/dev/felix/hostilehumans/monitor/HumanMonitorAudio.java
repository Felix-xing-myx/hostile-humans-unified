package dev.felix.hostilehumans.monitor;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server audience boundary. Reuses authored packets, positions, volume and range without re-emitting sounds. */
public final class HumanMonitorAudio {
    private HumanMonitorAudio() {}

    public static void broadcast(MinecraftServer server, Player excluded, double x, double y, double z,
            double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        if (!(packet instanceof ClientboundSoundPacket || packet instanceof ClientboundSoundEntityPacket
                || packet instanceof ClientboundLevelEventPacket)) return;
        // A nearby sound source need not itself be tracked from the player's stationary body.
        // Use its authored broadcast position so an unresolved entity ID cannot silence the supplement.
        Packet<?> audible = packet instanceof ClientboundSoundEntityPacket bound
                ? new ClientboundSoundPacket(bound.getSound(), bound.getSource(), x, y, z,
                        bound.getVolume(), bound.getPitch(), bound.getSeed()) : packet;
        supplement(server, excluded, x, y, z, radius, dimension, player -> player.connection.send(audible));
    }

    /** Optional sound adapters supply their native delivery; ordinary recipients are never sent a duplicate. */
    public static void supplement(MinecraftServer server, Player excluded, double x, double y, double z,
            double radius, ResourceKey<Level> dimension, Consumer<ServerPlayer> send) {
        supplementNative(server, excluded, x, y, z, radius, dimension,
                player -> player.distanceToSqr(x, y, z) < radius * radius, send);
    }

    /** Native channels can additionally restrict recipients by chunk tracking; preserve their exact deduplication. */
    public static void supplementNative(MinecraftServer server, Player excluded, double x, double y, double z,
            double radius, ResourceKey<Level> dimension, java.util.function.Predicate<ServerPlayer> alreadyReceived,
            Consumer<ServerPlayer> send) {
        if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z)) return;
        double squared = radius * radius;
        HumanMonitorService.forEachTarget((player, human) -> {
            if (player.server != server || player == excluded || player.level().dimension() != dimension) return;
            // Match vanilla PlayerList.broadcast's strict spherical boundary exactly.
            if (alreadyReceived.test(player)) return;
            if (human.getEyePosition().distanceToSqr(x, y, z) < squared) send.accept(player);
        });
    }
}
