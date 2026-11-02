package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.fml.ModList;

/** Optional native gun presentation seam. Its interface exposes no gun-mod classes or player state. */
final class HumanSpectatorGunView {
    private HumanSpectatorGunView() {}
    static boolean tryRender(Human human,float partialTick,PoseStack poses,MultiBufferSource buffers,int light) {
        if(!HumanSpectatorPresentation.allowsNativeGunPose(human)||!ModList.get().isLoaded("tacz")
                ||!dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isTaczGunItem(human.getMainHandItem()))return false;
        boolean drawn=false;
        try(var projection=new HumanSpectatorProjection(partialTick,buffers)) {
            drawn=dev.felix.hostilehumans.client.render.tacz.TaczSpectatorFirstPerson.tryRender(human,partialTick,poses,buffers,light);
            return drawn;
        }catch(RuntimeException|LinkageError failure){
            ClientAnimationCompatibility.warnOnce("TaCZ observed first person","Native presentation unavailable; retaining NPC arms",failure);
            return drawn;
        }
    }
}
