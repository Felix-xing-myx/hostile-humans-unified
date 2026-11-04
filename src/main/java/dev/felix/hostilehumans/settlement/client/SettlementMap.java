package dev.felix.hostilehumans.settlement.client;

import com.mojang.math.Axis;
import dev.felix.hostilehumans.client.ui.framework.UiRect;
import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.settlement.RegionBox;
import dev.felix.hostilehumans.settlement.SettlementCodec;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** North-up X/Z projection of the authorized control-console snapshot. */
final class SettlementMap {
    private static final int FRIENDLY = 0xff75e09a;
    private static final int OTHER = 0xffff7777;
    private static final int OUTER = 0xffdfad63;
    private static final int INNER = 0xff77b8ed;
    private static final int EXCLUDED = 0xffbd91c4;
    private static final int CAPTURE = 0xffd2dce3;

    private SettlementMap() {}

    static void render(GuiGraphics g, Font font, UiRect area, CompoundTag data, int mx, int my) {
        var bounds = data.getList("map_bounds", Tag.TAG_COMPOUND);
        if (bounds.isEmpty() || area.width() < 48 || area.height() < 65) return;
        RegionBox extent = null;
        for (Tag entry : bounds) {
            var box = SettlementCodec.box(((CompoundTag) entry).getIntArray("box"));
            extent = extent == null ? box : extent.union(box);
        }
        g.drawString(font, font.plainSubstrByWidth(data.getString("name"), area.width() - 36),
                area.x() + 4, area.y() + 3, CAPTURE, false);
        g.drawString(font, "N ↑", area.right() - 28, area.y() + 3, CAPTURE, false);
        var plot = new UiRect(area.x() + 2, area.y() + 15, area.width() - 4, area.height() - (data.getBoolean("map_limited") ? 55 : 44));
        g.fill(plot.x(), plot.y(), plot.right(), plot.bottom(), 0xff111d24);
        int arrowInset = Math.min(6, (plot.height() - 1) / 2);
        double scale = Math.min((plot.width() - 14) / (double) extent.sizeX(),
                (plot.height() - 2 * arrowInset) / (double) extent.sizeZ());
        var projection = new Projection(extent, scale,
                plot.x() + (plot.width() - extent.sizeX() * scale) / 2,
                plot.y() + (plot.height() - extent.sizeZ() * scale) / 2);
        g.enableScissor(plot.x(), plot.y(), plot.right(), plot.bottom());
        Component tooltip = null;
        try {
            var regions = data.getList("map_regions", Tag.TAG_COMPOUND);
            for (String layer : new String[] {"OUTER", "INNER"}) {
                for (Tag entry : regions) {
                    var region = (CompoundTag) entry;
                    if (!region.getString("layer").equals(layer)) continue;
                    for (Tag tag : region.getList("include", Tag.TAG_INT_ARRAY))
                        projection.draw(g, SettlementCodec.box(((IntArrayTag) tag).getAsIntArray()),
                                layer.equals("OUTER") ? OUTER : INNER, true);
                }
            }
            for (Tag entry : regions) {
                for (Tag tag : ((CompoundTag) entry).getList("exclude", Tag.TAG_INT_ARRAY))
                    projection.draw(g, SettlementCodec.box(((IntArrayTag) tag).getAsIntArray()), EXCLUDED, false);
            }
            for (Tag entry : bounds)
                projection.draw(g, SettlementCodec.box(((CompoundTag) entry).getIntArray("box")), CAPTURE, false);
            int nearest = 26;
            for (Tag entry : data.getList("map_markers", Tag.TAG_COMPOUND)) {
                var marker = (CompoundTag) entry;
                int x = projection.x(marker.getDouble("x"));
                int y = projection.y(marker.getDouble("z"));
                g.fill(x - 2, y - 2, x + 3, y + 3, 0xff0a1015);
                g.fill(x - 1, y - 1, x + 2, y + 2, marker.getBoolean("friendly") ? FRIENDLY : OTHER);
                if (my >= plot.y() && my < plot.bottom()
                        && mx >= plot.x() && mx < plot.right()
                        && Math.abs(mx - x) <= 5 && Math.abs(my - y) <= 5) {
                    int distance = (mx - x) * (mx - x) + (my - y) * (my - y);
                    if (distance < nearest) {
                        nearest = distance;
                        tooltip = Component.literal(marker.getString("name"));
                    }
                }
            }
            var self = data.getCompound("map_self");
            int x = Math.max(plot.x() + 6, Math.min(plot.right() - 6, projection.x(self.getDouble("x"))));
            int y = Math.max(plot.y() + arrowInset, Math.min(plot.bottom() - arrowInset - 1, projection.y(self.getDouble("z"))));
            g.pose().pushPose();
            try {
                g.pose().translate(x, y, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(self.getFloat("yaw") + 180F));
                for (int row = 0; row < 5; row++)
                    g.fill(-row, row - 5, row + 1, row - 4, 0xffffffff);
                g.fill(-1, 0, 2, 4, 0xffffffff);
            } finally {
                g.pose().popPose();
            }
        } finally {
            g.disableScissor();
        }
        legend(g, font, area.x() + 4, plot.bottom() + 5,
                new String[] {"capture", "outer", "inner", "excluded"},
                new int[] {CAPTURE, OUTER, INNER, EXCLUDED});
        legend(g, font, area.x() + 4, plot.bottom() + 17,
                new String[] {"friendly", "other", "self"}, new int[] {FRIENDLY, OTHER, CAPTURE});
        if (data.getBoolean("map_limited"))
            g.drawString(font, label("limited"), area.x() + 4, plot.bottom() + 28, OTHER, false);
        if (tooltip != null) g.renderTooltip(font, tooltip, mx, my);
    }

    private static void legend(GuiGraphics g, Font font, int x, int y, String[] keys, int[] colors) {
        for (int i = 0; i < keys.length; i++) {
            var caption = label(keys[i]);
            g.drawString(font, caption, x, y, colors[i], false);
            x += font.width(caption) + 10;
        }
    }

    private static Component label(String key) {
        return Component.translatable("gui.hostile_humans_unified.settlement.map." + key);
    }

    private record Projection(RegionBox extent, double scale, double left, double top) {
        int x(double x) { return (int) Math.round(left + (x - extent.minX()) * scale); }
        int y(double z) { return (int) Math.round(top + (z - extent.minZ()) * scale); }

        void draw(GuiGraphics g, RegionBox box, int color, boolean shade) {
            int x1 = x(box.minX()), y1 = y(box.minZ());
            int x2 = Math.max(x1 + 1, x(box.maxX() + 1D));
            int y2 = Math.max(y1 + 1, y(box.maxZ() + 1D));
            if (shade) g.fill(x1, y1, x2, y2, color & 0x30ffffff);
            UiDraw.outline(g, x1, y1, x2 - x1, y2 - y1, color);
        }
    }
}
