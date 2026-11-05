package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Per-render visibility filter. Never aliases suppressed consumers: vanilla may combine them. */
final class RenderVisibilityMask implements MultiBufferSource {
    private final MultiBufferSource original;
    private final Predicate<RenderType> visible;

    RenderVisibilityMask(MultiBufferSource original, Predicate<RenderType> visible) {
        this.original = Objects.requireNonNull(original);
        this.visible = Objects.requireNonNull(visible);
    }

    @Override public VertexConsumer getBuffer(RenderType type) {
        // A request may subsequently become either the base or the glint delegate. Even requests
        // for the same type must be independently composable. These short-lived sinks own no GPU
        // resource and are only allocated during the brief hidden part of a dodge.
        return visible.test(type) ? original.getBuffer(type) : new DiscardedVertices();
    }

    private static final class DiscardedVertices implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() { }
        @Override public void defaultColor(int r, int g, int b, int a) { }
        @Override public void unsetDefaultColor() { }
    }
}
