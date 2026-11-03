package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.client.render.DebugWorldLines;
import dev.felix.hostilehumans.settlement.RegionBox;
import dev.felix.hostilehumans.settlement.SettlementPreview;
import dev.felix.hostilehumans.settlement.SettlementSelectionTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class SettlementPreviewClient {
    static boolean throughWalls = true;
    private static SettlementPreview preview = SettlementPreview.EMPTY;

    private SettlementPreviewClient() {}

    public static void accept(SettlementPreview value) {
        preview = value;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        accept(SettlementPreview.EMPTY);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui
                || !preview.dimension().equals(mc.level.dimension().location().toString())
                || preview.toolOnly()
                        && !(mc.player.getMainHandItem().getItem() instanceof SettlementSelectionTool
                        || mc.player.getOffhandItem().getItem() instanceof SettlementSelectionTool)) return;

        Vec3 camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = DebugWorldLines.buffers();
        var type = throughWalls ? DebugWorldLines.type() : net.minecraft.client.renderer.RenderType.lines();
        poses.pushPose();
        try {
            var lines = buffers.getBuffer(type);
            for (var entry : preview.boxes()) {
                var box = entry.bounds();
                AABB world = bounds(box).inflate(.004 * (1 + (entry.rgb() & 3)));
                if (world.distanceToSqr(camera) > 192 * 192) continue;
                int rgb = entry.rgb();
                LevelRenderer.renderLineBox(poses, lines, world.move(-camera.x, -camera.y, -camera.z),
                        ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, .9F);
            }
            BlockPos first = preview.first();
            if (first != null) {
                LevelRenderer.renderLineBox(poses, lines, new AABB(first).inflate(.04)
                        .move(-camera.x, -camera.y, -camera.z), 0, 1, .3F, 1);
                BlockPos second = preview.second();
                if (second == null && mc.hitResult instanceof BlockHitResult hit
                        && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
                    second = hit.getBlockPos();
                if (second != null) {
                    LevelRenderer.renderLineBox(poses, lines, new AABB(second).inflate(.04)
                            .move(-camera.x, -camera.y, -camera.z), .3F, 1, .7F, 1);
                    var draft = new AABB(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                            Math.min(first.getZ(), second.getZ()), Math.max(first.getX(), second.getX()) + 1.,
                            Math.max(first.getY(), second.getY()) + 1., Math.max(first.getZ(), second.getZ()) + 1.);
                    LevelRenderer.renderLineBox(poses, lines, draft.inflate(.03)
                            .move(-camera.x, -camera.y, -camera.z), .25F, 1, .45F, 1);
                }
            }
        } finally {
            try {
                buffers.endBatch(type);
            } finally {
                poses.popPose();
            }
        }
        var font = mc.font;
        int labels = 0;
        try {
            for (var entry : preview.boxes()) {
                if (entry.label().isEmpty()) continue;
                var box = entry.bounds();
                var point = new Vec3(box.minX() + .5, box.maxY() + 1.12, box.minZ() + .5);
                if (point.distanceToSqr(camera) > 48 * 48) continue;
                var relative = point.subtract(camera);
                poses.pushPose();
                try {
                    poses.translate(relative.x, relative.y, relative.z);
                    poses.mulPose(event.getCamera().rotation());
                    poses.scale(-.025F, -.025F, .025F);
                    font.drawInBatch(entry.label(), -font.width(entry.label()) / 2F, 0,
                            0xff000000 | entry.rgb(), false, poses.last().pose(), buffers,
                            throughWalls ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH
                                    : net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                            0x70000000, 15728880);
                } finally {
                    poses.popPose();
                }
                if (++labels >= 24) break;
            }
        } finally {
            if (labels > 0) buffers.endBatch();
        }
    }

    private static AABB bounds(RegionBox box) {
        return new AABB(box.minX(), box.minY(), box.minZ(),
                box.maxX() + 1., box.maxY() + 1., box.maxZ() + 1.);
    }
}
