package dev.felix.hostilehumans.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Bounded block writes through vanilla, followed by entities and the author carrier. */
final class SettlementTemplatePlacement {
    private static final int BLOCKS_PER_BATCH = 2048;
    private static final int ENTITIES_PER_BATCH = 64;
    private static final String CARRIER = "hostile_humans_unified:settlement_information";
    private final CompoundTag header = new CompoundTag();
    private final ListTag blocks;
    private final ListTag entities;
    private final ServerLevel level;
    private final BlockPos origin;
    private final StructurePlaceSettings settings;
    private CompoundTag carrier;
    private int nextBlock;
    private int nextEntity;
    private boolean started;

    SettlementTemplatePlacement(CompoundTag data, ServerLevel level, BlockPos origin, StructurePlaceSettings settings) {
        this.level = level;
        this.origin = origin;
        this.settings = settings;
        for (String key : java.util.List.of("size", "palette", "DataVersion"))
            if (data.contains(key)) header.put(key, data.get(key));
        blocks = data.getList("blocks", Tag.TAG_COMPOUND);
        entities = settings.isIgnoreEntities() ? new ListTag() : data.getList("entities", Tag.TAG_COMPOUND);
    }

    boolean started() {
        return started;
    }

    int completed() {
        return nextBlock + nextEntity;
    }

    int total() {
        return blocks.size() + entities.size();
    }

    boolean advance() {
        if (nextBlock < blocks.size()) {
            var batch = new ListTag();
            int limit = Math.min(nextBlock + BLOCKS_PER_BATCH, blocks.size());
            while (nextBlock < limit) {
                var block = blocks.getCompound(nextBlock++);
                if (CARRIER.equals(block.getCompound("nbt").getString("id"))) carrier = block;
                else batch.add(block);
            }
            if (!batch.isEmpty()) place(batch, new ListTag());
        } else if (nextEntity < entities.size()) {
            var batch = new ListTag();
            int limit = Math.min(nextEntity + ENTITIES_PER_BATCH, entities.size());
            while (nextEntity < limit) batch.add(entities.getCompound(nextEntity++));
            place(new ListTag(), batch);
        }
        if (completed() < total()) return false;
        if (carrier != null) {
            var batch = new ListTag();
            batch.add(carrier);
            place(batch, new ListTag());
        }
        return true;
    }

    private void place(ListTag batch, ListTag entityBatch) {
        var data = new CompoundTag();
        for (String key : header.getAllKeys()) data.put(key, header.get(key));
        data.put("blocks", batch);
        data.put("entities", entityBatch);
        var chunks = new java.util.HashSet<Long>();
        for (Tag tag : batch) {
            var pos = ((CompoundTag) tag).getList("pos", Tag.TAG_INT);
            checkChunk(StructureTemplate.calculateRelativePosition(settings,
                    new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))).offset(origin), chunks);
        }
        for (Tag tag : entityBatch) {
            var pos = ((CompoundTag) tag).getList("pos", Tag.TAG_DOUBLE);
            var local = new net.minecraft.world.phys.Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            checkChunk(BlockPos.containing(StructureTemplate.transformedVec3d(settings, local)
                    .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin))), chunks);
        }
        var template = level.getStructureManager().readStructure(data);
        started = true;
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL))
            throw new IllegalArgumentException("vanilla template batch did not place");
    }

    private void checkChunk(BlockPos position, java.util.Set<Long> checked) {
        long key = net.minecraft.world.level.ChunkPos.asLong(position);
        if (checked.add(key) && !level.hasChunkAt(position))
            throw new IllegalArgumentException("a destination chunk was unloaded");
    }
}
