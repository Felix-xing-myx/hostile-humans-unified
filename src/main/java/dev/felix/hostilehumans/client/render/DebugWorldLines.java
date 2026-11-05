package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Shared bounded native storage and overlay line state for developer world geometry. Client only. */
public final class DebugWorldLines extends RenderType {
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final RenderType TYPE=create("hhu_world_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,4096,false,false,CompositeState.builder()
            .setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2)))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    public enum AuthorStroke {
        CAPTURE(16), OUTER(11), INNER(8), EXCLUSION(6), FACILITY(4), SELECTION(3), CORNER(2);

        private final RenderType xray, depth;
        private final float worldWidth;

        AuthorStroke(int width) {
            worldWidth=width/160F*1.75F;
            xray = author("hhu_author_xray_" + width, NO_DEPTH_TEST, width);
            depth = author("hhu_author_depth_" + width, LEQUAL_DEPTH_TEST, width);
        }

        public float worldWidth(){return worldWidth;}
        public RenderType renderType(boolean throughWalls) {
            return throughWalls ? xray : depth;
        }
    }

    private static RenderType author(String name, DepthTestStateShard depth, int width) {
        return create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                4096, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(width)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(depth)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }

    private static final RenderType VOLUME_XRAY=volume("hhu_author_volume_xray",NO_DEPTH_TEST),
            VOLUME_DEPTH=volume("hhu_author_volume_depth",LEQUAL_DEPTH_TEST);
    private static RenderType volume(String name,DepthTestStateShard depth){
        return create(name,DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,4096,false,false,
                CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(depth).setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
    public static RenderType authorVolume(boolean throughWalls){return throughWalls?VOLUME_XRAY:VOLUME_DEPTH;}
    /** Twelve world-space bars shrink naturally with distance; no screen-space width dependency. */
    public static void worldBox(com.mojang.blaze3d.vertex.PoseStack poses,com.mojang.blaze3d.vertex.VertexConsumer out,
            net.minecraft.world.phys.AABB box,float r,float g,float b,float a,float width){
        double h=width/2.;
        for(int axis=0;axis<3;axis++)for(int i=0;i<2;i++)for(int j=0;j<2;j++){
            double[] lo={box.minX,box.minY,box.minZ},hi={box.maxX,box.maxY,box.maxZ};
            int p=(axis+1)%3,q=(axis+2)%3;
            lo[p]=hi[p]=i==0?lo[p]:hi[p];lo[q]=hi[q]=j==0?lo[q]:hi[q];
            for(int k=0;k<3;k++){lo[k]-=h;hi[k]+=h;}
            var matrix=poses.last().pose();
            for(int face=0;face<3;face++)for(int side=0;side<2;side++){
                int u=(face+1)%3,v=(face+2)%3;
                for(int corner:new int[]{0,1,3,2}){
                    double[] xyz=lo.clone();xyz[face]=side==0?lo[face]:hi[face];
                    xyz[u]=(corner&1)==0?lo[u]:hi[u];xyz[v]=(corner&2)==0?lo[v]:hi[v];
                    out.vertex(matrix,(float)xyz[0],(float)xyz[1],(float)xyz[2]).color(r,g,b,a).endVertex();
                }
            }
        }
    }

    private DebugWorldLines(){super("hhu_world_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});}
    public static MultiBufferSource.BufferSource buffers(){return BUFFERS;}
    public static RenderType type(){return TYPE;}
}
