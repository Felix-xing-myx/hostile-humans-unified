package dev.felix.hostilehumans.settlement;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Incremental vanilla capture, merged into a standard single-palette structure file. */
final class SettlementTemplateCapture {
    private static final int SIDE = 16;
    private static final int MAX_ENTITIES = 4096;
    private final ServerLevel level;
    private final RegionBox bounds;
    private final boolean includeEntities;
    private final TemplateTransform.Cell size;
    private final int columns;
    private final int rows;
    private final int layers;
    private final ListTag palette = new ListTag();
    private final ListTag blocks = new ListTag();
    private final ListTag entities = new ListTag();
    private final Map<CompoundTag, Integer> stateIds = new HashMap<>();
    private final java.util.Set<java.util.UUID> seenEntities = new java.util.HashSet<>();
    private int next;

    SettlementTemplateCapture(ServerLevel level, RegionBox bounds, TemplateTransform.Cell size, boolean includeEntities) {
        this.level = level;
        this.bounds = bounds;
        this.size = size;
        this.includeEntities = includeEntities;
        columns = tiles(size.x());
        rows = tiles(size.z());
        layers = tiles(size.y());
    }

    private static int tiles(int size) {
        return (size + SIDE - 1) / SIDE;
    }

    int total() {
        return columns * rows * layers;
    }

    int completed() {
        return next;
    }

    void captureTile() {
        int dx = next % columns * SIDE;
        int dz = next / columns % rows * SIDE;
        int dy = next / (columns * rows) * SIDE;
        int sx = Math.min(SIDE, size.x() - dx);
        int sy = Math.min(SIDE, size.y() - dy);
        int sz = Math.min(SIDE, size.z() - dz);
        var origin = new BlockPos(bounds.minX() + dx, bounds.minY() + dy, bounds.minZ() + dz);
        SettlementTemplateFiles.checkArea(level, new RegionBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + sx - 1, origin.getY() + sy - 1, origin.getZ() + sz - 1));
        var tile = new StructureTemplate();
        tile.fillFromWorld(level, origin, new Vec3i(sx, sy, sz), includeEntities, Blocks.STRUCTURE_VOID);
        var data = tile.save(new CompoundTag());
        var localPalette = data.getList("palette", Tag.TAG_COMPOUND);
        int[] mapping = new int[localPalette.size()];
        for (int i = 0; i < mapping.length; i++) {
            var state = localPalette.getCompound(i);
            mapping[i] = stateIds.computeIfAbsent(state, key -> {
                int index = palette.size();
                palette.add(key);
                return index;
            });
        }
        for (Tag tag : data.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) tag;
            shift(block.getList("pos", Tag.TAG_INT), dx, dy, dz);
            block.putInt("state", mapping[block.getInt("state")]);
            blocks.add(block);
        }
        var tileSize = new TemplateTransform.Cell(sx, sy, sz);
        for (Tag tag : data.getList("entities", Tag.TAG_COMPOUND)) {
            var entity = (CompoundTag) tag;
            if (!SettlementTemplateFiles.validEntityPosition(entity, tileSize)) continue;
            var entityData = entity.getCompound("nbt");
            if (entityData.hasUUID("UUID") && !seenEntities.add(entityData.getUUID("UUID"))) continue;
            if (entities.size() >= MAX_ENTITIES)
                throw new IllegalArgumentException("large template entity limit: " + MAX_ENTITIES);
            var pos = entity.getList("pos", Tag.TAG_DOUBLE);
            pos.set(0, DoubleTag.valueOf(pos.getDouble(0) + dx));
            pos.set(1, DoubleTag.valueOf(pos.getDouble(1) + dy));
            pos.set(2, DoubleTag.valueOf(pos.getDouble(2) + dz));
            shift(entity.getList("blockPos", Tag.TAG_INT), dx, dy, dz);
            entities.add(entity);
        }
        next++;
    }

    private static void shift(ListTag pos, int x, int y, int z) {
        pos.set(0, IntTag.valueOf(pos.getInt(0) + x));
        pos.set(1, IntTag.valueOf(pos.getInt(1) + y));
        pos.set(2, IntTag.valueOf(pos.getInt(2) + z));
    }

    CompoundTag finish(String author) {
        if (next != total()) throw new IllegalStateException("unfinished structure capture");
        var data = new CompoundTag();
        var dimensions = new ListTag();
        dimensions.add(IntTag.valueOf(size.x()));
        dimensions.add(IntTag.valueOf(size.y()));
        dimensions.add(IntTag.valueOf(size.z()));
        data.put("size", dimensions);
        data.putString("author", author);
        data.put("palette", palette);
        data.put("blocks", blocks);
        data.put("entities", entities);
        return NbtUtils.addCurrentDataVersion(data);
    }
}
