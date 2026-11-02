package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.remote.MonitorRemoteView;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Redirect only tracking-coordinate reads, never the physical ServerPlayer accessors globally.
 * SRG targets verified against Forge 47.4.16 / official 1.20.1 bytecode and joined mappings. */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class MonitorRemoteChunkMapMixin {
    @Redirect(method = {"m_140184_(Lnet/minecraft/server/level/ServerPlayer;)V",
            "m_140192_(Lnet/minecraft/server/level/ServerPlayer;Z)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_146903_()I"), require = 2, allow = 2)
    private int hostileHumans$viewBlockX(ServerPlayer player) {
        return net.minecraft.util.Mth.floor(MonitorRemoteView.position(player).x);
    }
    @Redirect(method = {"m_140184_(Lnet/minecraft/server/level/ServerPlayer;)V",
            "m_140192_(Lnet/minecraft/server/level/ServerPlayer;Z)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;m_146907_()I"), require = 2, allow = 2)
    private int hostileHumans$viewBlockZ(ServerPlayer player) {
        return net.minecraft.util.Mth.floor(MonitorRemoteView.position(player).z);
    }
    @Redirect(method = {"m_140184_(Lnet/minecraft/server/level/ServerPlayer;)V",
            "m_140192_(Lnet/minecraft/server/level/ServerPlayer;Z)V",
            "m_140373_(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/core/SectionPos;"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;m_235861_(Lnet/minecraft/world/level/entity/EntityAccess;)Lnet/minecraft/core/SectionPos;"), require = 3, allow = 3)
    private SectionPos hostileHumans$viewSection(EntityAccess entity) {
        return entity instanceof ServerPlayer player ? MonitorRemoteView.section(player) : SectionPos.of(entity);
    }
}
