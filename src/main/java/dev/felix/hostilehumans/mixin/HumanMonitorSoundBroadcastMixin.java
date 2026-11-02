package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.HumanMonitorAudio;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplement only sound/level-event audiences; original broadcast and all non-audio packets remain native. */
@Mixin(value = PlayerList.class, remap = false)
public abstract class HumanMonitorSoundBroadcastMixin {
    @Inject(method = "m_11241_(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("TAIL"), require = 1, allow = 1, remap = false)
    private void hostileHumans$monitorAudio(Player excluded, double x, double y, double z, double radius,
            ResourceKey<Level> dimension, Packet<?> packet, CallbackInfo callback) {
        HumanMonitorAudio.broadcast(((PlayerList) (Object) this).getServer(), excluded, x, y, z, radius, dimension, packet);
    }
}
