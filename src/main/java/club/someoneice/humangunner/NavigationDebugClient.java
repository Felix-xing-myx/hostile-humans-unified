package club.someoneice.humangunner;

import com.mojang.blaze3d.vertex.*;
import java.util.Locale;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draws only a bounded server snapshot; never recomputes AI, damage or a path on the client. */
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, value = Dist.CLIENT)
public final class NavigationDebugClient {
    private static NavigationDebugSnapshot snapshot;
    private static long receivedAt;
    private NavigationDebugClient() {}
    public static void accept(NavigationDebugSnapshot value) { snapshot = value; receivedAt = System.nanoTime(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { snapshot = null; }
    private static boolean visible() {
        var mc = Minecraft.getInstance();
        if (snapshot == null || mc.player == null || mc.level == null || System.nanoTime() - receivedAt > 2_000_000_000L
                || !mc.level.dimension().location().equals(snapshot.dimension())
                || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get())) return false;
        var entity = mc.level.getEntity(snapshot.entityId());
        return entity != null && entity.getUUID().equals(snapshot.entityUuid()) && entity.isAlive();
    }
    private static Component text(String key, Object... args) { return Component.translatable("navigation_debug." + key, args); }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—"; }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        if (!visible() || Minecraft.getInstance().options.hideGui) return;
        var mc = Minecraft.getInstance(); var s = snapshot; var g = event.getGuiGraphics();
        Component[] lines = {
            text("hud_title", s.name(), text("mode." + s.mode())),
            text("hud_route", text("result." + s.state()), s.firstNode(), s.totalNodes(),
                    text(s.totalNodes() == 0 ? "result.none" : s.complete() ? "result.complete" : "result.partial"), s.truncated() ? "…" : ""),
            text("hud_goal", s.requested() == null ? "—" : s.requested().toShortString(), s.navigator()),
            text("hud_endpoint", s.endpoint() == null ? "—" : net.minecraft.core.BlockPos.containing(s.endpoint()).toShortString()),
            text("hud_health", number(s.health()), number(s.fallBudget()), s.maxDrop(), number(s.followRange())),
            text("hud_work", s.budget(), number(s.searchMillis()), text("reason." + s.obstruction())),
            text("legend")
        };
        int y = 8;
        for (Component line : lines) {
            int width = Math.min(g.guiWidth() - 16, mc.font.width(line) + 8);
            g.fill(6, y - 1, 6 + width, y + 10, 0xB0000000);
            g.drawString(mc.font, line, 10, y, 0xFFFFFF, false); y += 12;
        }
        int first = Math.max(0, s.samples().size() - 5);
        for (int i = first; i < s.samples().size() && y + 12 < g.guiHeight(); i++) {
            var sample = s.samples().get(i);
            Component line = text("hud_sample", text("source." + sample.source()),
                    net.minecraft.core.BlockPos.containing(sample.position()).toShortString(),
                    text("result." + sample.result()), number(sample.score()));
            g.fill(6, y - 1, Math.min(g.guiWidth() - 6, mc.font.width(line) + 18), y + 10, 0xB0000000);
            g.drawString(mc.font, line, 10, y, 0xDDDDDD, false); y += 12;
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !visible()) return;
        var mc = Minecraft.getInstance(); var s = snapshot; var poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
            VertexConsumer lines = buffers.getBuffer(DebugLines.TYPE);
            var entity = mc.level.getEntity(s.entityId());
            LevelRenderer.renderLineBox(poses, lines, entity.getBoundingBox().inflate(.08), 1, 1, 1, 1);
            if (s.requested() != null) box(poses, lines, Vec3.atBottomCenterOf(s.requested()), .35, 1, .85F, 0);
            if (s.enemy() != null) box(poses, lines, s.enemy(), .35, 1, .1F, .1F);
            if (s.endpoint() != null) box(poses, lines, s.endpoint(), .25, 0, .85F, 1);
            Vec3 previous = entity.position().add(0, .08, 0);
            for (int i = 0; i < s.path().size(); i++) {
                Vec3 point = s.path().get(i).add(0, .08, 0);
                line(poses, lines, previous, point, 0, .85F, 1);
                box(poses, lines, point, i == 0 ? .14 : .06, i == 0 ? 1 : 0, i == 0 ? 1 : .85F, 1);
                previous = point;
            }
            for (var sample : s.samples()) {
                boolean good = sample.result().equals("accepted") || sample.result().equals("complete") || sample.result().equals("scored");
                boolean pending = sample.result().equals("candidate") || sample.result().equals("partial");
                box(poses, lines, sample.position().add(0, .15, 0), .12, good ? .2F : 1, good ? 1 : .25F, pending ? 1 : .2F);
            }
            buffers.endBatch(DebugLines.TYPE);
        } finally { poses.popPose(); }
    }
    private static void box(PoseStack poses, VertexConsumer out, Vec3 p, double half, float r, float g, float b) {
        LevelRenderer.renderLineBox(poses, out, new AABB(p.x-half, p.y, p.z-half, p.x+half, p.y+.3, p.z+half), r, g, b, 1);
    }
    private static void line(PoseStack poses, VertexConsumer out, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 normal = b.subtract(a); if (normal.lengthSqr() < 1.0E-8) return; normal = normal.normalize();
        var pose = poses.last();
        out.vertex(pose.pose(), (float)a.x, (float)a.y, (float)a.z).color(r,g,blue,1).normal(pose.normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
        out.vertex(pose.pose(), (float)b.x, (float)b.y, (float)b.z).color(r,g,blue,1).normal(pose.normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
    }
    /** A separate no-depth line layer makes routes behind the test wall inspectable. */
    private static final class DebugLines extends RenderType {
        private DebugLines() { super("hhu_debug", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false, () -> {}, () -> {}); }
        private static final RenderType TYPE = create("hhu_navigation_debug", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 4096, false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
}
