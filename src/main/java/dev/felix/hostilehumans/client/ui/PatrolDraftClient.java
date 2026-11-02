package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.client.render.DebugWorldLines;
import dev.felix.hostilehumans.soldier.PatrolCommandBatonItem;
import dev.felix.hostilehumans.soldier.PatrolToolNetwork;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only server-confirmed drafts are shown; rendering does not scan blocks or select authoritative points. */
@Mod.EventBusSubscriber(modid=HumanRuntime.MOD_ID,value=Dist.CLIENT)
public final class PatrolDraftClient {
    private static PatrolToolNetwork.DraftPreview draft=new PatrolToolNetwork.DraftPreview(new UUID(0,0),"",List.of());
    private PatrolDraftClient() {}
    public static void accept(PatrolToolNetwork.DraftPreview value){draft=value;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){draft=new PatrolToolNetwork.DraftPreview(new UUID(0,0),"",List.of());}
    @SubscribeEvent public static void world(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||draft.points().isEmpty()
                ||!draft.dimension().equals(mc.level.dimension().location().toString())
                ||!(mc.player.getMainHandItem().getItem() instanceof PatrolCommandBatonItem
                    ||mc.player.getOffhandItem().getItem() instanceof PatrolCommandBatonItem))return;
        var poses=e.getPoseStack();var buffers=DebugWorldLines.buffers();Vec3 camera=e.getCamera().getPosition();
        poses.pushPose();
        try {
            var lines=buffers.getBuffer(DebugWorldLines.type());
            for(int i=0;i<draft.points().size();i++){
                BlockPos point=draft.points().get(i);if(Vec3.atCenterOf(point).distanceToSqr(camera)>128*128)continue;
                int rgb=i==0?0x66E699:i==draft.points().size()-1?0xFFD36A:0x37D5FF;
                float r=((rgb>>16)&255)/255F,g=((rgb>>8)&255)/255F,b=(rgb&255)/255F;
                var box=new AABB(point).inflate(.002).move(-camera.x,-camera.y,-camera.z);
                LevelRenderer.renderLineBox(poses,lines,box,r,g,b,.9F);
            }
        }finally{try{buffers.endBatch(DebugWorldLines.type());}finally{poses.popPose();}}
    }
}
