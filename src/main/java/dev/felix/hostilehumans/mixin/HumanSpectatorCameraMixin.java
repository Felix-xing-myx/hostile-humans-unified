package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.client.render.HumanSpectatorCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, remap = false)
public abstract class HumanSpectatorCameraMixin {
    @Shadow protected abstract void m_90581_(Vec3 position);

    @Inject(method = "m_90575_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("RETURN"), remap = false)
    private void hostileHumans$raiseSpectatorCamera(BlockGetter world, Entity entity,
            boolean detached, boolean mirrored, float partialTick, CallbackInfo callback) {
        m_90581_(HumanSpectatorCamera.position((Camera) (Object) this, world, entity, detached, partialTick));
    }
}
