package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.renderer.MultiBufferSource;

/** Actual vanilla delegate composition, without a world, render context or optional mods. */
public final class RenderVisibilityMaskTest {
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    public static void main(String[] args) {
        int[] originalCalls = {0};
        MultiBufferSource forbidden = type -> { originalCalls[0]++; throw new AssertionError("hidden geometry reached real buffer"); };
        var hidden = new RenderVisibilityMask(forbidden, type -> false);
        VertexConsumer base = hidden.getBuffer(null), glint = hidden.getBuffer(null), extra = hidden.getBuffer(null);
        check(base != glint && glint != extra && base != extra, "each request must compose independently");
        check(originalCalls[0] == 0, "hidden pass does not allocate real buffers");
        // Reproduce the original vanilla guard, then prove both double and multiple composition.
        try { VertexMultiConsumer.create(base, base); throw new AssertionError("vanilla duplicate guard disappeared"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("Duplicate"), "exact failure reproduced"); }
        emit(VertexMultiConsumer.create(base, glint));
        emit(VertexMultiConsumer.create(base, glint, extra));
        var visible = new RenderVisibilityMask(type -> { originalCalls[0]++; return base; }, type -> true);
        check(visible.getBuffer(null) == base && originalCalls[0] == 1, "visible pass preserves real consumer identity");
        emit(VertexMultiConsumer.create(visible.getBuffer(null), hidden.getBuffer(null)));
        System.out.println("RenderVisibilityMaskTest: native base/glint/multiple delegates passed");
    }
    private static void emit(VertexConsumer consumer) {
        consumer.defaultColor(255,255,255,255);
        consumer.vertex(1,2,3).color(255,255,255,255).uv(0,0).overlayCoords(0,0).uv2(0,0).normal(0,1,0).endVertex();
        consumer.unsetDefaultColor();
    }
}
