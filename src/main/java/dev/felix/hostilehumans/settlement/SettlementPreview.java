package dev.felix.hostilehumans.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record SettlementPreview(String dimension, List<Box> boxes, BlockPos first, BlockPos second,
        boolean toolOnly, List<Ghost> ghosts, String task, String region) {
    private static final int MAX_BOXES = 1154;
    public static final SettlementPreview EMPTY = new SettlementPreview("", List.of(), null, null, true);

    public static final SettlementPreview EMPTY_PLACEMENT =
            new SettlementPreview("", List.of(), null, null, true, List.of(), "place", "");

    static final int MAX_GHOSTS = 4096;

    public record Ghost(BlockPos position, int state) {}

    public SettlementPreview(String dimension, List<Box> boxes, BlockPos first, BlockPos second,
            boolean toolOnly) {
        this(dimension, boxes, first, second, toolOnly, List.of(), "", "");
    }

    public SettlementPreview(String dimension, List<Box> boxes, BlockPos first, BlockPos second,
            boolean toolOnly, List<Ghost> ghosts) {
        this(dimension, boxes, first, second, toolOnly, ghosts, "", "");
    }

    public record Box(RegionBox bounds, int rgb, String label) {
        public Box(RegionBox bounds, int rgb) {
            this(bounds, rgb, "");
        }
    }

    public SettlementPreview {
        boxes = List.copyOf(boxes);
        ghosts = List.copyOf(ghosts);
        if (ghosts.size() > MAX_GHOSTS) throw new IllegalArgumentException("preview model limit");
        if (boxes.size() > MAX_BOXES) throw new IllegalArgumentException("preview box limit");
    }

    static void encode(SettlementPreview preview, FriendlyByteBuf out) {
        out.writeUtf(preview.dimension(), 256);
        out.writeBoolean(preview.toolOnly());
        out.writeUtf(preview.task(), 32);
        out.writeUtf(preview.region(), 256);
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
        out.writeVarInt(preview.ghosts().size());
        for (var ghost : preview.ghosts()) {
            out.writeBlockPos(ghost.position());
            out.writeVarInt(ghost.state());
        }
    }

    static SettlementPreview decode(FriendlyByteBuf in) {
        String dimension = in.readUtf(256);
        boolean toolOnly = in.readBoolean();
        String task = in.readUtf(32);
        String region = in.readUtf(256);
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
        int ghostCount = in.readVarInt();
        if (ghostCount < 0 || ghostCount > MAX_GHOSTS) throw new IllegalArgumentException("preview model limit");
        var ghosts = new java.util.ArrayList<Ghost>(ghostCount);
        for (int i = 0; i < ghostCount; i++)
            ghosts.add(new Ghost(in.readBlockPos(), in.readVarInt()));
        return new SettlementPreview(dimension, boxes, first, second, toolOnly, ghosts, task, region);
    }
}
