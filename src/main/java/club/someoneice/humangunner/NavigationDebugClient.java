package club.someoneice.humangunner;

import com.mojang.blaze3d.vertex.*;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
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
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Bounded server geometry; reusable native storage and stable per-observer entity colors. */
@Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, value = Dist.CLIENT)
public final class NavigationDebugClient {
    private static NavigationDebugFrame frame = NavigationDebugFrame.EMPTY;
    private static long receivedAt;
    private static final int[] COLORS = {0x37D5FF, 0xFFA842, 0xC18CFF, 0x66E699, 0xFF7FB5, 0xE4DF55, 0x729EFF, 0xEE8266};
    // 0 hidden, 1 diagnostic focus, 2 all observed members. Local UI only.
    static final int[] LAYERS = {2, 1, 1, 1};
    static boolean diagnosticFocus(NavigationDebugFrame.Entry e) {
        var ids=visibleEntries().stream().map(member -> member.snapshot().entityUuid()).toList();
        return Objects.equals(NavigationDebugViewPolicy.resolveFocus(frame.focused(),ids), e.snapshot().entityUuid());
    }
    private static boolean layer(int layer, NavigationDebugFrame.Entry e) {
        return NavigationDebugViewPolicy.layerVisible(LAYERS[layer], diagnosticFocus(e));
    }
    private NavigationDebugClient() {}
    static int slotColor(int slot) { return COLORS[slot]; }
    @Mod.EventBusSubscriber(modid = HumanGunner.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        static final KeyMapping LEGEND = new KeyMapping("key.hostile_humans_unified.navigation_legend",
                KeyConflictContext.IN_GAME, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8, "key.categories.hostile_humans_unified");
        static final KeyMapping STOP_TEST = new KeyMapping("key.hostile_humans_unified.navigation_stop_test",
                KeyConflictContext.IN_GAME, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F9, "key.categories.hostile_humans_unified");
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(LEGEND); event.register(STOP_TEST); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        while (Keys.STOP_TEST.consumeClick()) {
            if (mc.player != null && mc.screen == null && !visibleEntries().isEmpty()) NavigationDebugNetwork.stopFocusedTest();
        }
        while (Keys.LEGEND.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new NavigationLegendScreen());
        }
    }
    public static void accept(NavigationDebugFrame value) {
        frame = value; receivedAt = System.nanoTime();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { frame = NavigationDebugFrame.EMPTY; }
    static List<NavigationDebugFrame.Entry> visibleEntries() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || System.nanoTime() - receivedAt > 3_000_000_000L
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
    static boolean focused(NavigationDebugFrame.Entry entry) { return Objects.equals(frame.focused(), entry.snapshot().entityUuid()); }
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
        int columns = 1;
        int width = Math.min(170, g.guiWidth() - 16), cardHeight = 33;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i); var s = entry.snapshot();
            int x = 6 + (i % columns) * width, y = 6 + (i / columns) * cardHeight;
            g.fill(x, y, x + width - 4, y + cardHeight - 2, focused(entry) ? 0xD0000000 : 0xA0000000);
            g.fill(x, y, x + 2, y + cardHeight - 2, 0xFF000000 | color(entry));
            draw(g, text("card_title", entry.slot() + 1, s.name(), text("mode." + s.mode()), focused(entry) ? "◀" : ""), x+5, y+2, width-12, color(entry));
            draw(g, text("card_route", text("result." + s.state()), s.firstNode(), s.totalNodes()), x+5, y+12, width-12, 0xFFFFFF);
            draw(g, s.mode().equals("test") ? text("test_warning") : text("card_goal", s.requested() == null ? "—" : s.requested().toShortString()), x+5, y+22, width-12, s.mode().equals("test") ? 0xFFAA44 : 0xDDDDDD);
        }
        var focus = entries.stream().filter(NavigationDebugClient::diagnosticFocus).findFirst().orElse(entries.get(0));
        var s = focus.snapshot();
        int y = 10 + ((entries.size() + columns - 1) / columns) * cardHeight;
        Component[] details = {
            text("detail.focus", focus.slot() + 1, s.name()),
            text("detail.state", s.perception().awareness(), s.perception().decision()),
            text("detail.reason", s.perception().reason()),
            text("hud_route", text("result." + s.state()), s.firstNode(), s.totalNodes(),
                    text(s.totalNodes() == 0 ? "result.none" : s.complete() ? "result.complete" : "result.partial"), s.truncated() ? "…" : ""),
            text("hud_goal", s.requested() == null ? "—" : s.requested().toShortString(), s.navigator()),
            text("hud_endpoint", point(s.endpoint())),
            text("hud_health", number(s.health()), number(s.fallBudget()), s.maxDrop(), number(s.followRange())),
            text("hud_work", s.budget(), number(s.searchMillis()), text("reason." + s.obstruction())),
            text("multi_help"),
            text("help.open", Keys.LEGEND.getTranslatedKeyMessage()),
            text("stop_help", Keys.STOP_TEST.getTranslatedKeyMessage())
        };
        for (Component detail : details) {
            if (y + 8 > g.guiHeight()) break;
            y = compactDetail(g, detail, y, 0xFFFFFF);
        }
        for (int i = Math.max(0, s.samples().size()-5); i < s.samples().size() && y+8 <= g.guiHeight(); i++) {
            var sample = s.samples().get(i);
            Component detail = text("hud_sample", text("source." + sample.source()), point(sample.position()),
                    text("result." + sample.result()), number(sample.score()));
            y = compactDetail(g, detail, y, 0xDDDDDD);
        }
    }
    private static int compactDetail(GuiGraphics g, Component detail, int y, int color) {
        var font = Minecraft.getInstance().font;
        float scale = .8F;
        int screenWidth = Math.min(g.guiWidth() - 12, Math.min(240, Math.max(80, (int)(g.guiWidth() * .42F))));
        int textWidth = Math.max(8, (int)((screenWidth - 8) / scale));
        var poses = g.pose();
        poses.pushPose();
        try {
            poses.translate(6, y, 0);
            poses.scale(scale, scale, 1);
            int row = 0;
            for (var line : font.split(detail, textWidth)) {
                if (y + (row + 10) * scale > g.guiHeight()) break;
                // Keep each background strip close to the text, including short wrapped lines.
                g.fill(0, row, font.width(line) + 8, row + 10, 0x90000000);
                g.drawString(font, line, 4, row + 1, color, false);
                row += 10;
            }
            return y + (int)Math.ceil(row * scale);
        } finally { poses.popPose(); }
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
                if (layer(0, entry)) {
                if (s.requested() != null) box(poses, lines, Vec3.atBottomCenterOf(s.requested()).subtract(camera), .35, r,g,b);
                if (s.endpoint() != null) box(poses, lines, s.endpoint().subtract(camera), .25, r,g,b);
                Vec3 previous = s.position().add(0,.08,0).subtract(camera);
                for (int i = 0; i < s.path().size(); i++) {
                    Vec3 next = s.path().get(i).add(0,.08,0).subtract(camera);
                    line(poses, lines, previous, next, r,g,b);
                    box(poses, lines, next, i == 0 ? .14 : .06, i == 0 ? 1 : r, i == 0 ? 1 : g, i == 0 ? 1 : b);
                    previous = next;
                }
                    for (var sample : s.samples()) {
                        boolean good = sample.result().equals("accepted") || sample.result().equals("complete") || sample.result().equals("scored");
                        boolean pending = sample.result().equals("candidate") || sample.result().equals("partial");
                        box(poses, lines, sample.position().add(0,.15,0).subtract(camera), .12, good ? .2F : 1, good ? 1 : .25F, pending ? 1 : .2F);
                    }
                }
                if (layer(2, entry) && s.enemy() != null)
                    box(poses, lines, s.enemy().subtract(camera), .35, 1,.1F,.1F);
                perception(poses, lines, s.perception(), camera, entry, r, g, b);
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
    private static void perception(PoseStack poses, VertexConsumer out, PerceptionDebugSnapshot s, Vec3 camera,
            NavigationDebugFrame.Entry entry, float r, float g, float b) {
        if(NavigationDebugViewPolicy.compactVision(LAYERS[1],diagnosticFocus(entry)))compactVision(poses,out,s,camera,r,g,b);
        for (var m : s.markers()) {
            boolean ring = m.kind().startsWith("vision_");
            int layer = ring || m.kind().equals("los") || m.kind().equals("blocked") ? 1
                    : m.kind().equals("sound") || m.kind().equals("beacon") || m.kind().equals("direction") ? 3 : 2;
            if (!layer(layer, entry)) continue;
            if (layer == 1 && !NavigationDebugViewPolicy.detailedVision(LAYERS[1])) continue;
            if(m.kind().equals("vision_near_top")||m.kind().equals("vision_known_near_top"))continue;
            if(m.kind().equals("vision_near_base")||m.kind().equals("vision_known_near_base")){
                boolean known=m.kind().equals("vision_known_near_base");
                var top=nearTop(s,known);
                if(top!=null)nearVolume(poses,out,m,top,camera,known?.5F:r,known?.8F:g,known?1:b);
                continue;
            }
            Vec3 p = m.position().subtract(camera);
            float red = r, green = g, blue = b;
            switch (m.kind()) {
                case "vision_known_ring" -> { red = .5F; green = .8F; blue = 1; }
                case "blocked" -> { red = 1; green = .15F; blue = .15F; }
                case "visible", "los" -> { red = .2F; green = 1; blue = .3F; }
                case "last" -> { red = 1; green = .65F; blue = .15F; }
                case "dynamic" -> { red = 1; green = .25F; blue = .7F; }
                case "sound", "direction" -> { red = .2F; green = .8F; blue = 1; }
                case "beacon" -> { red = .8F; green = .3F; blue = 1; }
            }
            if (ring || m.kind().equals("sound") || m.kind().equals("beacon")) {
                Vec3 axis = ring && m.end() != null ? m.end() : new Vec3(0, 1, 0);
                if (axis.lengthSqr() < 1E-8) continue;
                axis = axis.normalize();
                Vec3 u = axis.cross(Math.abs(axis.y) < .9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize();
                Vec3 v = axis.cross(u).normalize(), previous = p.add(u.scale(m.radius()));
                for (int i = 1; i <= 32; i++) {
                    double angle = i * Math.PI / 16;
                    Vec3 next = p.add(u.scale(Math.cos(angle)*m.radius())).add(v.scale(Math.sin(angle)*m.radius()));
                    line(poses,out,previous,next,red,green,blue); previous=next;
                    if (ring && i % 8 == 0) line(poses,out,s.eye().subtract(camera),next,red,green,blue);
                }
            } else box(poses,out,p,.15,red,green,blue);
            if (!ring && m.end() != null) line(poses,out,p,m.end().subtract(camera),red,green,blue);
        }
    }
    /** Focus shows one body-relative near volume; all retains familiar/distant fields and sampled rays. */
    private static void compactVision(PoseStack poses, VertexConsumer out, PerceptionDebugSnapshot s, Vec3 camera,
            float r,float g,float b) {
        if(s.range()<=0||s.view().lengthSqr()<1.e-8)return;
        for(var marker:s.markers())if(marker.kind().equals("vision_near_base")){
            var top=nearTop(s,false);
            if(top!=null)nearVolume(poses,out,marker,top,camera,r,g,b);
            line(poses,out,s.eye().subtract(camera),s.eye().add(s.view().normalize().scale(Math.min(8,s.range()))).subtract(camera),r,g,b);
            return;
        }
        PerceptionDebugSnapshot.Marker nearest=null;double best=Double.MAX_VALUE;
        for(var marker:s.markers())if(marker.kind().equals("vision_new_ring")){
            double distance=Math.hypot(marker.position().distanceTo(s.eye()),marker.radius());
            double score=Math.abs(distance-8);
            if(score<best){best=score;nearest=marker;}
        }
        Vec3 origin=s.eye().subtract(camera),axis=s.view().normalize();
        line(poses,out,origin,origin.add(axis.scale(Math.min(8,s.range()))),r,g,b);
        if(nearest==null)return;
        double axial=nearest.position().distanceTo(s.eye());
        double radius=Math.min(8,Math.hypot(axial,nearest.radius()));
        double halfAngle=Math.atan2(nearest.radius(),axial);
        Vec3 side=axis.cross(Math.abs(axis.y)<.9?new Vec3(0,1,0):new Vec3(1,0,0)).normalize();
        Vec3 previous=null;
        for(int i=0;i<=16;i++){
            double angle=-halfAngle+2*halfAngle*i/16;
            Vec3 next=origin.add(axis.scale(radius*Math.cos(angle))).add(side.scale(radius*Math.sin(angle)));
            if(previous!=null)line(poses,out,previous,next,r,g,b);
            if(i==0||i==16)line(poses,out,origin,next,r,g,b);
            previous=next;
        }
    }
    private static PerceptionDebugSnapshot.Marker nearTop(PerceptionDebugSnapshot snapshot,boolean known){
        String kind=known?"vision_known_near_top":"vision_near_top";
        return snapshot.markers().stream().filter(m->m.kind().equals(kind)).findFirst().orElse(null);
    }
    private static void nearVolume(PoseStack poses,VertexConsumer out,PerceptionDebugSnapshot.Marker base,
            PerceptionDebugSnapshot.Marker top,Vec3 camera,float r,float g,float b){
        if(base.end()==null)return;
        Vec3 axis=new Vec3(base.end().x,0,base.end().z);
        if(axis.lengthSqr()<1.e-8)return;
        axis=axis.normalize();Vec3 side=new Vec3(axis.z,0,-axis.x);
        Vec3 bottom=base.position().subtract(camera),upper=top.position().subtract(camera);
        Vec3 previousBottom=null,previousTop=null;double half=base.end().y;
        for(int i=0;i<=16;i++){
            double angle=-half+2*half*i/16;
            Vec3 offset=axis.scale(base.radius()*Math.cos(angle)).add(side.scale(base.radius()*Math.sin(angle)));
            Vec3 low=bottom.add(offset),high=upper.add(offset);
            if(previousBottom!=null){line(poses,out,previousBottom,low,r,g,b);line(poses,out,previousTop,high,r,g,b);}
            if(i==0||i==16){line(poses,out,bottom,low,r,g,b);line(poses,out,upper,high,r,g,b);}
            if(i==0||i==8||i==16)line(poses,out,low,high,r,g,b);
            previousBottom=low;previousTop=high;
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
