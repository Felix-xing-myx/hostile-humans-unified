package dev.felix.hostilehumans.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Client-only access to the same FOV selection used by vanilla's hand pass. */
@Mixin(value=GameRenderer.class,remap=false)
public interface HumanSpectatorProjectionAccess {
    @Invoker("m_109141_") double hostileHumans$handFov(Camera camera,float partialTick,boolean useWorldSetting);
}
