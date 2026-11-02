package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.remote.MonitorRemoteView;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayer.class, remap = false)
public abstract class MonitorRemoteClientOptionsMixin {
    @Inject(method = "m_9156_(Lnet/minecraft/network/protocol/game/ServerboundClientInformationPacket;)V",
            at = @At("TAIL"), require = 1, allow = 1)
    private void hostileHumans$recordViewDistance(ServerboundClientInformationPacket packet, CallbackInfo callback) {
        MonitorRemoteView.recordClientViewDistance((ServerPlayer) (Object) this, packet.viewDistance());
    }
}
