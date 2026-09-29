package com.craftix.hostile_humans.mixin;

import club.someoneice.humangunner.GunSupport;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents TaCZ's legacy biped pose from overriding the maintained Human gun pose. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.animation.third.InnerThirdPersonManager", remap = false)
public abstract class TaczNpcAnimatorMixin {
    @Inject(method = "setRotationAnglesHead", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private static void hostileHumans$avoidLegacyPoseOnAnimatedNpc(
            LivingEntity entity,
            ModelPart head,
            ModelPart rightArm,
            ModelPart leftArm,
            ModelPart body,
            float tickDelta,
            CallbackInfo callback
    ) {
        if (entity instanceof Human human && GunSupport.get().isGun(human.getMainHandItem())) {
            callback.cancel();
        }
    }
}
