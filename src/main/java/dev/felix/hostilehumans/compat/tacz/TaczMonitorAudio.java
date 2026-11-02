package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSound;
import dev.felix.hostilehumans.monitor.HumanMonitorAudio;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

/** Supplement the actual native sound message, never invent sound from a separate shoot event. */
public final class TaczMonitorAudio {
    private TaczMonitorAudio() {}
    public static void supplement(LivingEntity shooter, int distance, ResourceLocation gun,
            ResourceLocation display, String name, float volume, float pitch) {
        if (!(shooter.level() instanceof ServerLevel level) || distance <= 0) return;
        var pos = shooter.blockPosition();
        var nativeAudience = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
        var message = new ServerMessageSound(shooter.getId(), gun, display, name, volume, pitch, distance);
        HumanMonitorAudio.supplementNative(level.getServer(), shooter instanceof Player player ? player : null,
                pos.getX(), pos.getY(), pos.getZ(), distance, level.dimension(),
                player -> nativeAudience.contains(player) && player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < (double) distance * distance,
                player -> NetworkHandler.sendToClientPlayer(message, player));
    }
}
