package com.craftix.hostile_humans.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={DamageSources.class}, priority=999)
public class DamageSourceMix {
    private static final ResourceKey<DamageType> HUMAN_DAMAGE_TYPE = ResourceKey.create((ResourceKey)Registries.DAMAGE_TYPE, (ResourceLocation)new ResourceLocation("hostile_humans", "human"));
    private static final ResourceKey<DamageType> HUMAN_WITH_NAME_DAMAGE_TYPE = ResourceKey.create((ResourceKey)Registries.DAMAGE_TYPE, (ResourceLocation)new ResourceLocation("hostile_humans", "human_with_name"));
    @Shadow(remap=false)
    @Final
    private Registry<DamageType> f_268645_;

    @Inject(method={"mobAttack"}, at={@At(value="HEAD")}, cancellable=true)
    private void getRenderDistance(LivingEntity entity, CallbackInfoReturnable<DamageSource> cir) {
        if (entity instanceof Human) {
            ResourceKey<DamageType> damageTypeKey = entity.hasCustomName() ? HUMAN_WITH_NAME_DAMAGE_TYPE : HUMAN_DAMAGE_TYPE;
            cir.setReturnValue(new DamageSource((Holder)this.f_268645_.getHolderOrThrow(damageTypeKey), (Entity)entity));
        }
    }
}

