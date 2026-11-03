package dev.felix.hostilehumans.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record SettlementPreview(String dimension, List<Box> boxes, BlockPos first, BlockPos second,
        boolean toolOnly) {
    private static final int MAX_BOXES = 1153;
    public static final SettlementPreview EMPTY = new SettlementPreview("", List.of(), null, null, true);

    public record Box(RegionBox bounds, int rgb, String label) {
        public Box(RegionBox bounds, int rgb) {
            this(bounds, rgb, "");
        }
    }

    public SettlementPreview {
        boxes = List.copyOf(boxes);
        if (boxes.size() > MAX_BOXES) throw new IllegalArgumentException("preview box limit");
    }

    static void encode(SettlementPreview preview, FriendlyByteBuf out) {
        out.writeUtf(preview.dimension(), 256);
        out.writeBoolean(preview.toolOnly());
        out.writeNullable(preview.first(), FriendlyByteBuf::writeBlockPos);
        out.writeNullable(preview.second(), FriendlyByteBuf::writeBlockPos);
        out.writeVarInt(preview.boxes().size());
        for (var box : preview.boxes()) {
            var b = box.bounds();
            out.writeBlockPos(new BlockPos(b.minX(), b.minY(), b.minZ()));
            out.writeBlockPos(new BlockPos(b.maxX(), b.maxY(), b.maxZ()));
            out.writeInt(box.rgb());
            out.writeUtf(box.label(), 192);
        }
    }

    static SettlementPreview decode(FriendlyByteBuf in) {
        String dimension = in.readUtf(256);
        boolean toolOnly = in.readBoolean();
        var first = in.readNullable(FriendlyByteBuf::readBlockPos);
        var second = in.readNullable(FriendlyByteBuf::readBlockPos);
        int size = in.readVarInt();
        if (size < 0 || size > MAX_BOXES) throw new IllegalArgumentException("preview box limit");
        var boxes = new java.util.ArrayList<Box>(size);
        for (int i = 0; i < size; i++) {
            var a = SettlementAuthoring.cell(in.readBlockPos());
            var b = SettlementAuthoring.cell(in.readBlockPos());
            boxes.add(new Box(RegionBox.between(a, b), in.readInt(), in.readUtf(192)));
        }
        return new SettlementPreview(dimension, boxes, first, second, toolOnly);
    }
}
