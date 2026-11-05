package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felix.hostilehumans.entity.DodgeMotion;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Whole-appearance trail frame. Actual model layers are captured once by DodgeGeometryCapture. */
final class DodgeTrailLayer {
    private DodgeTrailLayer(){}
    record Frame(Matrix4f origin,Vector3f axis,float offset){}
    static Frame frame(Human human,float partial,PoseStack poses){
        DodgeMotion motion=human.hhu$dodgeMotion();
        if(motion==null||!motion.hasExposure(human.tickCount,partial,DodgeMotion.SHUTTER_TICKS))return null;
        double yaw=Math.toRadians(Minecraft.getInstance().gameRenderer.getMainCamera().getYRot());
        return new Frame(new Matrix4f(poses.last().pose()),new Vector3f((float)Math.cos(yaw),0,(float)Math.sin(yaw)),motion.offset(human.tickCount,partial));
    }
    static MultiBufferSource blinkBuffers(Human human,float partial,MultiBufferSource original){
        if(!human.hhu$dodgeMotion().blink(human.tickCount,partial))return original;
        return new RenderVisibilityMask(original,type->false);
    }
}
