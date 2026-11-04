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

        AuthorStroke(int width) {
            xray = author("hhu_author_xray_" + width, NO_DEPTH_TEST, width);
            depth = author("hhu_author_depth_" + width, LEQUAL_DEPTH_TEST, width);
        }

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

    private DebugWorldLines(){super("hhu_world_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});}
    public static MultiBufferSource.BufferSource buffers(){return BUFFERS;}
    public static RenderType type(){return TYPE;}
}
