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
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(value=Dist.CLIENT)
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientRenderer {
    public static final ModelLayerLocation HUMAN_MODEL_LAYER = new ModelLayerLocation(new ResourceLocation("hostile_humans_unified", "human"), "main");

    protected ClientRenderer() {
    }
    @SubscribeEvent
    public static void contentSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(
                club.someoneice.humangunner.FactionContent.TRIBUTE_CHEST.get(),net.minecraft.client.renderer.RenderType.cutout()));
    }

    @SubscribeEvent
    public static void registerAnimationReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(BackShieldPose.INSTANCE);
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            TaczNpcAnimator.resetPlayback();
            BetterCombatNpcAnimator.resetPlayback();
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        EmfAnimationCompat.registerPauseCondition();
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN1.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN2.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.HUMAN3.get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.ROAMER.get(), HumanRenderer::new);
        for (var boss : club.someoneice.humangunner.FactionContent.BOSSES)
            event.registerEntityRenderer((EntityType)boss.type().get(), HumanRenderer::new);
        event.registerEntityRenderer((EntityType)ModEntityType.SPAWNER_ENTITY.get(), SpawnerEntityRenderer::new);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void registerFactionEggColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) {
        java.util.List<net.minecraftforge.registries.RegistryObject<net.minecraft.world.item.Item>> eggs=new java.util.ArrayList<>(
                club.someoneice.humangunner.HumanGunnerRegistries.FACTION_SPAWN_EGGS);
        eggs.addAll(club.someoneice.humangunner.HumanGunnerRegistries.BOSS_SPAWN_EGGS);
        for(var egg:eggs)event.register((stack,index)-> {
            var item=(club.someoneice.humangunner.FactionSpawnEggItem)stack.getItem();
            return index==2 ? 0xFF000000 | club.someoneice.humangunner.FactionVisualClient.color(item.faction()) : index>2 ? 0xFFFFFFFF : item.getColor(index);
        },egg.get());
    }

    @SubscribeEvent
    public static void registerEntityLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HUMAN_MODEL_LAYER, () -> LayerDefinition.create((MeshDefinition)PlayerModel.createMesh((CubeDeformation)CubeDeformation.NONE, (boolean)true), (int)64, (int)64));
    }
}

