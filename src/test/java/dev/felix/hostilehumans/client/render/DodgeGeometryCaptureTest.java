package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import dev.felix.hostilehumans.entity.DodgeMotion;
import java.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real native materials and delegate composition; geometry buffers are in-memory, no GL/world. */
public final class DodgeGeometryCaptureTest {
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void prepareNativeMaterials(){
        net.minecraft.SharedConstants.tryDetectVersion();
        // Standalone JavaExec lacks Forge event-bus transformation: initialize registries only.
        try {
            var flag=net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            flag.setAccessible(true);flag.setBoolean(null,true);
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("Native test bootstrap",failure);}
        net.minecraft.core.registries.BuiltInRegistries.bootStrap();
    }
    public static void main(String[] args){
        prepareNativeMaterials();
        var motion=new DodgeMotion();motion.trigger(100);
        var frame=new DodgeTrailLayer.Frame(new Matrix4f(),new Vector3f(1,0,0),motion.offset(101,.25F));
        List<RenderType> materials=List.of("skin","armor","band","shield","weapon").stream()
                .map(name->RenderType.entityCutoutNoCull(new ResourceLocation("hostile_humans_unified","test/"+name+".png"))).toList();
        Map<RenderType,Counter> nativeBuffers=new IdentityHashMap<>(),ghostBuffers=new IdentityHashMap<>();
        MultiBufferSource source=type->nativeBuffers.computeIfAbsent(type,key->new Counter());
        MultiBufferSource output=type->ghostBuffers.computeIfAbsent(type,key->new Counter());
        try(var capture=new DodgeGeometryCapture(source,frame)){
            for(var material:materials){check(DodgeRenderTypes.ghost(material)!=null,"native texture bridge");quad(capture.getBuffer(material));}
            check(nativeBuffers.values().stream().mapToInt(c->c.count).sum()==20,"normal mesh forwarded once");
            capture.replay(output,motion,101,.25F,true);
            check(ghostBuffers.size()==5,"all model-layer materials represented independently");
            for(var material:materials)check(ghostBuffers.get(DodgeRenderTypes.ghost(material)).count>0,"equipment ghost receives geometry");
            check(nativeBuffers.values().stream().mapToInt(c->c.count).sum()==20,"replay never invokes original layers");
        }
        nativeBuffers.clear();ghostBuffers.clear();
        try(var capture=new DodgeGeometryCapture(new RenderVisibilityMask(source,type->false),frame)){
            quad(VertexMultiConsumer.create(capture.getBuffer(materials.get(1)),capture.getBuffer(RenderType.glint())));
            check(nativeBuffers.isEmpty(),"blink hides real armor and foil");
            capture.replay(output,motion,101,.25F,true);
            check(ghostBuffers.size()==1,"blink still captures armor; foil is not duplicated");
            check(ghostBuffers.values().stream().allMatch(c->c.finite&&c.alpha>0&&c.alpha<=255),"finite/tinted ghost output");
        }
        check(DodgeRenderTypes.ghost(RenderType.text(new ResourceLocation("minecraft","font/ascii.png")))==null,"labels are not body trails");
        System.out.println("DodgeGeometryCaptureTest: skin/armor/band/shield/weapon, blink and native foil composition passed");
    }
    private static void quad(VertexConsumer v){for(int i=0;i<4;i++)v.vertex(i,0,0).color(255,255,255,255).uv(0,0).overlayCoords(0,10).uv2(240,240).normal(0,1,0).endVertex();}
    private static final class Counter implements VertexConsumer{
        int count,alpha;boolean finite=true;
        @Override public VertexConsumer vertex(double x,double y,double z){finite&=Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){alpha=a;return this;}
        @Override public VertexConsumer uv(float u,float v){return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){return this;}
        @Override public VertexConsumer uv2(int u,int v){return this;}
        @Override public VertexConsumer normal(float x,float y,float z){return this;}
        @Override public void endVertex(){count++;}
        @Override public void defaultColor(int r,int g,int b,int a){}
        @Override public void unsetDefaultColor(){}
    }
}
