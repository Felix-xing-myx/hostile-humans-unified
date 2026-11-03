package dev.felix.hostilehumans.settlement.client;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.client.render.DebugWorldLines;
import dev.felix.hostilehumans.settlement.RegionBox;
import dev.felix.hostilehumans.settlement.SettlementPreview;
import dev.felix.hostilehumans.settlement.SettlementSelectionTool;
import dev.felix.hostilehumans.settlement.SettlementPlacementTool;
import dev.felix.hostilehumans.settlement.SettlementNetwork;
import dev.felix.hostilehumans.settlement.TemplateTransform.Cell;
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
    private static SettlementPreview selection = SettlementPreview.EMPTY;
    private static SettlementPreview placement = SettlementPreview.EMPTY_PLACEMENT;

    private SettlementPreviewClient() {}

    public static void accept(SettlementPreview value) {
        if (value.task().equals("place")) placement = value;
        else selection = value;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        accept(SettlementPreview.EMPTY);
        accept(SettlementPreview.EMPTY_PLACEMENT);
    }

    @SubscribeEvent
    public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        var client = Minecraft.getInstance();
        var preview = event.getItemStack().getItem() instanceof SettlementPlacementTool ? placement : selection;
        if (!isPreviewTool(preview, event.getItemStack()) || client.level == null
                || preview.task().isEmpty()
                || !preview.dimension().equals(client.level.dimension().location().toString())) return;
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable(
                "item.hostile_humans_unified.settlement_selection_tool.mode." + preview.task())
                .append(preview.region().isEmpty() ? "" : " · " + preview.region()));
    }

    private static boolean isPreviewTool(SettlementPreview preview, net.minecraft.world.item.ItemStack stack) {
        return preview.task().equals("place") ? stack.getItem() instanceof SettlementPlacementTool
                : stack.getItem() instanceof SettlementSelectionTool;
    }

    @SubscribeEvent
    public static void cancelCorner(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        var player = Minecraft.getInstance().player;
        if (!event.isAttack() || player == null || !player.isCreative() && !player.hasPermissions(2)
                || !(player.getMainHandItem().getItem() instanceof SettlementSelectionTool)) return;
        var request = new net.minecraft.nbt.CompoundTag();
        request.putBoolean("cancel", true);
        SettlementNetwork.configureSelector(request);
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        var mainHand = mc.player.getMainHandItem();
        var preview = isPreviewTool(selection, mainHand)
                || !isPreviewTool(placement, mainHand) && isPreviewTool(selection, mc.player.getOffhandItem())
                ? selection : placement;
        if (!preview.dimension().equals(mc.level.dimension().location().toString())
                || preview.toolOnly()
                        && !(isPreviewTool(preview, mc.player.getMainHandItem()) || isPreviewTool(preview, mc.player.getOffhandItem()))) return;

        Vec3 camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = DebugWorldLines.buffers();
        SettlementGhostRenderer.draw(mc, poses, camera, preview.ghosts());
        var type = DebugWorldLines.author(throughWalls);
        RegionBox draftBounds = null;
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
                    draftBounds = RegionBox.between(
                            new Cell(first.getX(), first.getY(), first.getZ()),
                            new Cell(second.getX(), second.getY(), second.getZ()));
                    LevelRenderer.renderLineBox(poses, lines, bounds(draftBounds).inflate(.03)
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
            var labelBoxes = preview.boxes();
            if (draftBounds != null) {
                labelBoxes = new java.util.ArrayList<>(labelBoxes);
                labelBoxes.add(0, new SettlementPreview.Box(draftBounds, 0x40ff73,
                        draftBounds.sizeX() + " × " + draftBounds.sizeY() + " × " + draftBounds.sizeZ()));
            }
            for (var entry : labelBoxes) {
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
                    String label = entry.label();
                    if (entry.rgb() == 0xffffff) label += "  " + box.sizeX() + " × " + box.sizeY() + " × " + box.sizeZ();
                    font.drawInBatch(label, -font.width(label) / 2F, 0,
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
