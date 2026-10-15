package club.someoneice.humangunner;

import com.mojang.blaze3d.vertex.*;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded server geometry; reusable native storage and stable per-observer entity colors. */
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, value = Dist.CLIENT)
public final class NavigationDebugClient {
    private static NavigationDebugFrame frame = NavigationDebugFrame.EMPTY;
    private static long receivedAt;
    private static final int[] COLORS = {0x37D5FF, 0xFFA842, 0xC18CFF, 0x66E699, 0xFF7FB5, 0xE4DF55, 0x729EFF, 0xEE8266};
    private NavigationDebugClient() {}
    public static void accept(NavigationDebugFrame value) { frame = value; receivedAt = System.nanoTime(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { frame = NavigationDebugFrame.EMPTY; }
    private static List<NavigationDebugFrame.Entry> visibleEntries() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || System.nanoTime() - receivedAt > 2_000_000_000L
                || !mc.player.getItemBySlot(EquipmentSlot.HEAD).is(HumanGunnerRegistries.NAVIGATION_OBSERVER_HELMET.get())) return List.of();
        return frame.entries().stream().filter(entry -> valid(entry.snapshot())).toList();
    }
    private static boolean valid(NavigationDebugSnapshot snapshot) {
        var level = Minecraft.getInstance().level;
        if (!level.dimension().location().equals(snapshot.dimension())) return false;
        var entity = level.getEntity(snapshot.entityId());
        // Tracking can end before the server's 128-block observation range. Server geometry remains usable.
        return entity == null || entity.getUUID().equals(snapshot.entityUuid()) && entity.isAlive();
    }
    private static Component text(String key, Object... args) { return Component.translatable("navigation_debug." + key, args); }
    private static String number(double value) { return Double.isFinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "—"; }
    private static int color(NavigationDebugFrame.Entry entry) { return COLORS[entry.slot()]; }
    private static boolean focused(NavigationDebugFrame.Entry entry) { return Objects.equals(frame.focused(), entry.snapshot().entityUuid()); }
    private static String point(Vec3 position) { return position == null ? "—" : net.minecraft.core.BlockPos.containing(position).toShortString(); }
    private static void draw(GuiGraphics g, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        String value = font.plainSubstrByWidth(text.getString(), Math.max(8, width));
        g.drawString(font, value, x, y, color, false);
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        List<NavigationDebugFrame.Entry> entries = visibleEntries();
        if (entries.isEmpty() || Minecraft.getInstance().options.hideGui) return;
        var g = event.getGuiGraphics();
        int columns = Math.min(entries.size(), Math.max(1, g.guiWidth() / 155));
        int width = (g.guiWidth() - 16) / columns, cardHeight = 33;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i); var s = entry.snapshot();
            int x = 6 + (i % columns) * width, y = 6 + (i / columns) * cardHeight;
            g.fill(x, y, x + width - 4, y + cardHeight - 2, focused(entry) ? 0xD0000000 : 0xA0000000);
            g.fill(x, y, x + 2, y + cardHeight - 2, 0xFF000000 | color(entry));
            draw(g, text("card_title", entry.slot() + 1, s.name(), text("mode." + s.mode()), focused(entry) ? "◀" : ""), x+5, y+2, width-12, color(entry));
            draw(g, text("card_route", text("result." + s.state()), s.firstNode(), s.totalNodes()), x+5, y+12, width-12, 0xFFFFFF);
            draw(g, text("card_goal", s.requested() == null ? "—" : s.requested().toShortString()), x+5, y+22, width-12, 0xDDDDDD);
        }
        var focus = entries.stream().filter(NavigationDebugClient::focused).findFirst().orElse(entries.get(0));
        var s = focus.snapshot();
        int y = 10 + ((entries.size() + columns - 1) / columns) * cardHeight;
        Component[] details = {
            text("focus_title", focus.slot() + 1, s.name()),
            text("hud_route", text("result." + s.state()), s.firstNode(), s.totalNodes(),
                    text(s.totalNodes() == 0 ? "result.none" : s.complete() ? "result.complete" : "result.partial"), s.truncated() ? "…" : ""),
            text("hud_goal", s.requested() == null ? "—" : s.requested().toShortString(), s.navigator()),
            text("hud_endpoint", point(s.endpoint())),
            text("hud_health", number(s.health()), number(s.fallBudget()), s.maxDrop(), number(s.followRange())),
            text("hud_work", s.budget(), number(s.searchMillis()), text("reason." + s.obstruction())),
            text("multi_help")
        };
        for (Component detail : details) {
            if (y + 12 > g.guiHeight()) break;
            g.fill(6, y-1, g.guiWidth()-6, y+10, 0xB0000000);
            draw(g, detail, 10, y, g.guiWidth()-22, 0xFFFFFF); y += 12;
        }
        for (int i = Math.max(0, s.samples().size()-5); i < s.samples().size() && y+12 < g.guiHeight(); i++) {
            var sample = s.samples().get(i);
            Component detail = text("hud_sample", text("source." + sample.source()), point(sample.position()),
                    text("result." + sample.result()), number(sample.score()));
            g.fill(6, y-1, g.guiWidth()-6, y+10, 0xB0000000);
            draw(g, detail, 10, y, g.guiWidth()-22, 0xDDDDDD); y += 12;
        }
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        List<NavigationDebugFrame.Entry> entries = visibleEntries();
        if (entries.isEmpty()) return;
        var poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        var buffers = NativeBuffers.BUFFERS;
        poses.pushPose();
        try {
            for (var entry : entries) {
                var s = entry.snapshot(); int rgb = color(entry);
                float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
                VertexConsumer lines = buffers.getBuffer(DebugLines.TYPE);
                box(poses, lines, s.position().subtract(camera), .3, r,g,b);
                if (s.requested() != null) box(poses, lines, Vec3.atBottomCenterOf(s.requested()).subtract(camera), .35, r,g,b);
                if (s.endpoint() != null) box(poses, lines, s.endpoint().subtract(camera), .25, r,g,b);
                Vec3 previous = s.position().add(0,.08,0).subtract(camera);
                for (int i = 0; i < s.path().size(); i++) {
                    Vec3 next = s.path().get(i).add(0,.08,0).subtract(camera);
                    line(poses, lines, previous, next, r,g,b);
                    box(poses, lines, next, i == 0 ? .14 : .06, i == 0 ? 1 : r, i == 0 ? 1 : g, i == 0 ? 1 : b);
                    previous = next;
                }
                if (focused(entry)) {
                    if (s.enemy() != null) box(poses, lines, s.enemy().subtract(camera), .35, 1,.1F,.1F);
                    for (var sample : s.samples()) {
                        boolean good = sample.result().equals("accepted") || sample.result().equals("complete") || sample.result().equals("scored");
                        boolean pending = sample.result().equals("candidate") || sample.result().equals("partial");
                        box(poses, lines, sample.position().add(0,.15,0).subtract(camera), .12, good ? .2F : 1, good ? 1 : .25F, pending ? 1 : .2F);
                    }
                }
                // Flush each bounded entity before reusing the same native storage for the next one.
                buffers.endBatch(DebugLines.TYPE);
                label(event, buffers, entry, camera);
                buffers.endBatch();
            }
        } finally {
            buffers.endBatch();
            poses.popPose();
        }
    }
    private static void label(RenderLevelStageEvent event, MultiBufferSource buffers, NavigationDebugFrame.Entry entry, Vec3 camera) {
        var poses = event.getPoseStack(); var font = Minecraft.getInstance().font;
        Vec3 relative = entry.snapshot().position().add(0,2.2,0).subtract(camera);
        String name = "#" + (entry.slot()+1) + " " + entry.snapshot().name();
        poses.pushPose();
        try {
            poses.translate(relative.x, relative.y, relative.z);
            poses.mulPose(event.getCamera().rotation()); poses.scale(-.025F, -.025F, .025F);
            font.drawInBatch(name, -font.width(name)/2F, 0, 0xFF000000 | color(entry), false,
                    poses.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, 0x70000000, 15728880);
        } finally { poses.popPose(); }
    }
    private static void box(PoseStack poses, VertexConsumer out, Vec3 p, double half, float r, float g, float b) {
        LevelRenderer.renderLineBox(poses, out, new AABB(p.x-half,p.y,p.z-half,p.x+half,p.y+.3,p.z+half),r,g,b,1);
    }
    private static void line(PoseStack poses, VertexConsumer out, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 normal = b.subtract(a); if (normal.lengthSqr() < 1.0E-8) return; normal = normal.normalize();
        var pose = poses.last();
        out.vertex(pose.pose(), (float)a.x,(float)a.y,(float)a.z).color(r,g,blue,1).normal(pose.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        out.vertex(pose.pose(), (float)b.x,(float)b.y,(float)b.z).color(r,g,blue,1).normal(pose.normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
    }
    /** BufferBuilder owns malloc memory, not a GC-managed direct buffer: allocate once, never per frame. */
    private static final class NativeBuffers {
        private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(65536));
    }
    private static final class DebugLines extends RenderType {
        private DebugLines() { super("hhu_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{}); }
        private static final RenderType TYPE = create("hhu_navigation_debug",DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES,4096,false,false,CompositeState.builder()
                .setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.of(2)))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
}
