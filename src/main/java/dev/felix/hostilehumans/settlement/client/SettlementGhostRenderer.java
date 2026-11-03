package dev.felix.hostilehumans.settlement.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.felix.hostilehumans.settlement.SettlementPreview;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

/** Draw only block models. The server owns the placement and never sends block entity data. */
final class SettlementGhostRenderer {
    private static final RenderType TYPE = RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS);
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(65536));

    private SettlementGhostRenderer() {}

    static void draw(Minecraft mc, PoseStack poses, Vec3 camera, List<SettlementPreview.Ghost> ghosts) {
        if (ghosts.isEmpty()) return;
        var consumer = new TranslucentVertex(BUFFERS.getBuffer(TYPE));
        MultiBufferSource source = layer -> consumer;
        try {
            for (var ghost : ghosts) {
                var pos = ghost.position();
                if (pos.distToCenterSqr(camera.x, camera.y, camera.z) > 128 * 128) continue;
                poses.pushPose();
                try {
                    poses.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                    mc.getBlockRenderer().renderSingleBlock(Block.stateById(ghost.state()), poses,
                            source, 15728880, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, TYPE);
                } finally {
                    poses.popPose();
                }
            }
        } finally {
            BUFFERS.endBatch(TYPE);
        }
    }

    private record TranslucentVertex(VertexConsumer delegate) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color(r, g, b, a * 115 / 255);
            return this;
        }
        @Override public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }
        @Override public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }
        @Override public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }
        @Override public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
        @Override public void endVertex() {
            delegate.endVertex();
        }
        @Override public void defaultColor(int r, int g, int b, int a) {
            delegate.defaultColor(r, g, b, a * 115 / 255);
        }
        @Override public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
