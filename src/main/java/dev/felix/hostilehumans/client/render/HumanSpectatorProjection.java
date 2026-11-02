package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.felix.hostilehumans.mixin.HumanSpectatorProjectionAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Native hand FOV scoped to this draw. No world depth clear, player bob or shared backup slot. */
final class HumanSpectatorProjection implements AutoCloseable {
    private final Matrix4f projection;
    private final VertexSorting sorting;
    private final MultiBufferSource buffers;
    HumanSpectatorProjection(float partialTick,MultiBufferSource buffers) {
        this.buffers=buffers;
        var client=Minecraft.getInstance();
        // Finish world vertices under the world projection before installing a hand projection.
        client.renderBuffers().bufferSource().endBatch();
        projection=new Matrix4f(RenderSystem.getProjectionMatrix());sorting=RenderSystem.getVertexSorting();
        double fov=((HumanSpectatorProjectionAccess)client.gameRenderer).hostileHumans$handFov(
                client.gameRenderer.getMainCamera(),partialTick,false);
        if(!Double.isFinite(fov)||fov<=1||fov>=179)fov=70;
        RenderSystem.setProjectionMatrix(client.gameRenderer.getProjectionMatrix(fov),VertexSorting.DISTANCE_TO_ORIGIN);
    }
    @Override public void close() {
        try {if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();}
        finally{RenderSystem.setProjectionMatrix(projection,sorting);}
    }
}
