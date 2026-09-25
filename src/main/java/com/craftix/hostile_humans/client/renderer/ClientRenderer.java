package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.client.renderer.HumanRenderer;
import com.craftix.hostile_humans.client.renderer.SpawnerEntityRenderer;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(value=Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "hostile_humans", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRenderer {
    public static final ModelLayerLocation HUMAN_MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("hostile_humans", "human"), "main");

    protected ClientRenderer() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN1.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN2.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN3.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.ROAMER.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.SPAWNER_ENTITY.get(), SpawnerEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerEntityLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HUMAN_MODEL_LAYER, () -> LayerDefinition.create((MeshDefinition)PlayerModel.createMesh((CubeDeformation)CubeDeformation.NONE, (boolean)true), (int)64, (int)64));
    }
}

