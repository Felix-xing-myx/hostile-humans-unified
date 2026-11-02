package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.remote.MonitorRemoteView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep native pairing, broadcast eligibility and tracking ranges, with the viewing origin as audience center. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity", remap = false)
public abstract class MonitorRemoteEntityTrackingMixin {
    @Redirect(method = "m_140497_(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_20182_()Lnet/minecraft/world/phys/Vec3;"), require = 1, allow = 1)
    private Vec3 hostileHumans$viewPosition(ServerPlayer player) { return MonitorRemoteView.position(player); }
}
