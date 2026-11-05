package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.felix.hostilehumans.entity.DodgeMotion;
import java.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Capture the real single body/equipment pass, then replay geometry only. No layer/animation re-entry. */
final class DodgeGeometryCapture implements MultiBufferSource,AutoCloseable {
    private static final int STRIDE=14,MAX_PER_MATERIAL=4096,MAX_TOTAL=24576;
    private static final ArrayDeque<Mesh> FREE=new ArrayDeque<>();
    private final Map<RenderType,Mesh> meshes=new LinkedHashMap<>();
    private final MultiBufferSource original;
    private final Vector3f direction;
    private final float offset;
    private int vertices;
    DodgeGeometryCapture(MultiBufferSource original,DodgeTrailLayer.Frame frame){
        this.original=original;direction=new Vector3f(frame.axis());frame.origin().transformDirection(direction);offset=frame.offset();
    }
    @Override public VertexConsumer getBuffer(RenderType type){
        VertexConsumer live=original.getBuffer(type);
        RenderType ghost=DodgeRenderTypes.ghost(type);if(ghost==null)return live;
        Mesh mesh=meshes.computeIfAbsent(type,key->{var pooled=FREE.pollFirst();var value=pooled==null?new Mesh():pooled;value.type=ghost;return value;});
        // Always a fresh wrapper: item renderers may combine independent base/glint consumers.
        return new CaptureVertex(live,mesh);
    }
    void replay(MultiBufferSource output,DodgeMotion motion,int tick,float partial,boolean detailed){
        int samples=detailed?5:3;
        for(var mesh:meshes.values()){
            int primitive=mesh.type.mode().primitiveLength;
            int complete=mesh.count-mesh.count%primitive;if(complete==0)continue;
            VertexConsumer target=null;
            for(int sample=samples;sample>=1;sample--){
                float exposure=(float)sample/samples;
                float past=motion.offset(tick,partial-DodgeMotion.SHUTTER_TICKS*exposure),delta=past-offset;
                if(Math.abs(delta)<.018F)continue;
                float lobe=sample==2||sample==samples?.10F:0;
                float alpha=Math.min(.30F,Math.abs(delta)*1.5F)*(1-.60F*exposure)*5/samples+lobe;
                if(target==null)target=output.getBuffer(mesh.type);
                float dx=direction.x*delta,dy=direction.y*delta,dz=direction.z*delta;
                for(int i=0;i<complete;i++){
                    int p=i*STRIDE;float[] v=mesh.data;
                    target.vertex(v[p]+dx,v[p+1]+dy,v[p+2]+dz)
                            .color(channel(v[p+3]*.78F),channel(v[p+4]*.85F),channel(v[p+5]*.92F),channel(v[p+6]*alpha))
                            .uv(v[p+7],v[p+8]).overlayCoords(Float.floatToRawIntBits(v[p+12]))
                            .uv2(Float.floatToRawIntBits(v[p+13])).normal(v[p+9],v[p+10],v[p+11]).endVertex();
                }
            }
        }
    }
    private static int channel(float value){return Math.max(0,Math.min(255,Math.round(value*255)));}
    @Override public void close(){
        for(var mesh:meshes.values()){mesh.count=0;mesh.type=null;if(FREE.size()<16)FREE.addFirst(mesh);}
        meshes.clear();vertices=0;
    }
    private static final class Mesh{
        RenderType type;int count;float[] data=new float[128*STRIDE];
        void room(){if((count+1)*STRIDE>data.length)data=Arrays.copyOf(data,Math.min(MAX_PER_MATERIAL*STRIDE,data.length*2));}
    }
    private final class CaptureVertex implements VertexConsumer {
        private final VertexConsumer live;private final Mesh mesh;
        private float x,y,z,r=1,g=1,b=1,a=1,u,v,nx,ny=1,nz;
        private int overlay=OverlayTexture.NO_OVERLAY,light=0xF000F0;private boolean position;
        CaptureVertex(VertexConsumer live,Mesh mesh){this.live=live;this.mesh=mesh;}
        @Override public VertexConsumer vertex(double x,double y,double z){live.vertex(x,y,z);this.x=(float)x;this.y=(float)y;this.z=(float)z;position=true;return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){live.color(r,g,b,a);this.r=r/255F;this.g=g/255F;this.b=b/255F;this.a=a/255F;return this;}
        @Override public VertexConsumer uv(float u,float v){live.uv(u,v);this.u=u;this.v=v;return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){live.overlayCoords(u,v);overlay=(u&65535)|(v<<16);return this;}
        @Override public VertexConsumer uv2(int u,int v){live.uv2(u,v);light=(u&65535)|(v<<16);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){live.normal(x,y,z);nx=x;ny=y;nz=z;return this;}
        @Override public void defaultColor(int r,int g,int b,int a){live.defaultColor(r,g,b,a);this.r=r/255F;this.g=g/255F;this.b=b/255F;this.a=a/255F;}
        @Override public void unsetDefaultColor(){live.unsetDefaultColor();r=g=b=a=1;}
        @Override public VertexConsumer misc(com.mojang.blaze3d.vertex.VertexFormatElement element,int... data){live.misc(element,data);return this;}
        @Override public void endVertex(){
            live.endVertex();
            if(position&&mesh.count<MAX_PER_MATERIAL&&vertices<MAX_TOTAL){
                mesh.room();int p=mesh.count++*STRIDE;float[] out=mesh.data;
                out[p]=x;out[p+1]=y;out[p+2]=z;out[p+3]=r;out[p+4]=g;out[p+5]=b;out[p+6]=a;
                out[p+7]=u;out[p+8]=v;out[p+9]=nx;out[p+10]=ny;out[p+11]=nz;
                out[p+12]=Float.intBitsToFloat(overlay);out[p+13]=Float.intBitsToFloat(light);vertices++;
            }
            position=false;
        }
    }
}
