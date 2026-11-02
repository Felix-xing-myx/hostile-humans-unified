package dev.felix.hostilehumans.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Client-only bridges used exclusively by the detached prediction body. */
@Mixin(value = Entity.class, remap = false)
public interface MonitorPredictionEntityAccess {
    @Invoker("m_20272_") Vec3 hostileHumans$collide(Vec3 motion);
    @Invoker("m_20323_") void hostileHumans$updateFluidEyes();
    @Accessor("f_19815_") void hostileHumans$dimensions(EntityDimensions dimensions);
    @Accessor("f_19816_") void hostileHumans$eyeHeight(float height);
}
