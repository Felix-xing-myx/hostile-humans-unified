package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageSound;
import dev.felix.hostilehumans.monitor.HumanMonitorAudio;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Deliver the final native payload to remote observers outside the original audience. */
public final class TaczMonitorAudio {
    private TaczMonitorAudio() {}

    public static void supplement(ServerMessageSound message) {
        var server = ServerLifecycleHooks.getCurrentServer();
        int distance = message.getDistance();
        // The same constructor is used by the receiving decoder. It is not a server emission.
        if (server == null || !server.isSameThread() || distance <= 0) return;
        for (var level : server.getAllLevels()) {
            if (!(level.getEntity(message.getEntityId()) instanceof LivingEntity shooter)) continue;
            var pos = shooter.blockPosition();
            var nativeAudience = level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false);
            HumanMonitorAudio.supplementNative(server, shooter instanceof Player player ? player : null,
                    pos.getX(), pos.getY(), pos.getZ(), distance, level.dimension(),
                    player -> nativeAudience.contains(player)
                            && player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) < (double) distance * distance,
                    player -> NetworkHandler.sendToClientPlayer(message, player));
            return;
        }
    }
}
