package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.SpawnerEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(value=Dist.CLIENT)
public class SpawnerEntityRenderer
extends MobRenderer<SpawnerEntity, PlayerModel<SpawnerEntity>> {
    public SpawnerEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    public boolean shouldRender(SpawnerEntity p_115468_, Frustum p_115469_, double p_115470_, double p_115471_, double p_115472_) {
        return false;
    }

    public ResourceLocation getTextureLocation(SpawnerEntity p_114482_) {
        return null;
    }
}

