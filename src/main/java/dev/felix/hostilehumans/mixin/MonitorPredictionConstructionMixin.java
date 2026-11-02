package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.monitor.client.MonitorPredictionBody;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Client-only construction isolation, matching precisely the unregistered physics shell. */
@Mixin(value = Entity.class, remap = false)
public abstract class MonitorPredictionConstructionMixin extends CapabilityProvider<Entity> {
    protected MonitorPredictionConstructionMixin() { super(Entity.class); }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/event/ForgeEventFactory;getEntitySizeForge(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Pose;Lnet/minecraft/world/entity/EntityDimensions;F)Lnet/minecraftforge/event/entity/EntityEvent$Size;", remap = false))
    private EntityEvent.Size hostileHumans$predictionSize(Entity entity, Pose pose, EntityDimensions dimensions, float eyeHeight) {
        return entity instanceof MonitorPredictionBody ? new EntityEvent.Size(entity, pose, dimensions, eyeHeight)
                : ForgeEventFactory.getEntitySizeForge(entity, pose, dimensions, eyeHeight);
    }
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z", remap = false))
    private boolean hostileHumans$predictionConstruction(IEventBus bus, Event event) {
        if ((Object) this instanceof MonitorPredictionBody) return false;
        return bus.post(event);
    }
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;gatherCapabilities()V", remap = false))
    private void hostileHumans$predictionCapabilities(Entity entity) {
        if (entity instanceof MonitorPredictionBody) return;
        gatherCapabilities();
    }
}
