package dev.felix.hostilehumans.settlement;

import dev.felix.hostilehumans.entity.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;

/** Server-owned map snapshot, restricted to already loaded residents in the capture volumes. */
final class SettlementMapSnapshot {
    private static final int MARKER_LIMIT = 512;

    private SettlementMapSnapshot() {}

    static CompoundTag capture(ServerPlayer player, SettlementInstance instance, boolean terrain) {
        var data = new CompoundTag();
        data.putString("name", instance.name());
        data.putString("faction", instance.faction());
        var boxes = new ArrayList<RegionBox>();
        var bounds = new ListTag();
        var regions = new ListTag();
        var markers = new ListTag();
        var seen = new HashSet<Integer>();
        boolean limited = false;
        for (var module : instance.modules()) {
            var size = module.author().size();
            var box = new RegionBox(0, 0, 0, size.x() - 1, size.y() - 1, size.z() - 1)
                    .transform(module.transform());
            boxes.add(box);
            var outline = new CompoundTag();
            outline.putIntArray("box", coordinates(box));
            bounds.add(outline);
            regions.addAll(SettlementCodec.regions(module.regions()));
            int remaining = MARKER_LIMIT - markers.size();
            if (remaining == 0) {
                limited = true;
                continue;
            }
            var found = new ArrayList<LivingEntity>();
            player.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class),
                    new AABB(box.minX(), box.minY(), box.minZ(),
                            box.maxX() + 1D, box.maxY() + 1D, box.maxZ() + 1D),
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                            && !seen.contains(entity.getId())
                            && box.contains(entity.getX(), entity.getY(), entity.getZ()),
                    found, remaining + 1);
            limited |= found.size() > remaining;
            for (int i = 0; i < Math.min(remaining, found.size()); i++) {
                var entity = found.get(i);
                seen.add(entity.getId());
                var marker = position(entity);
                String name = entity.getDisplayName().getString();
                marker.putString("name", name.substring(0, Math.min(128, name.length())));
                marker.putBoolean("friendly", entity instanceof Human human
                        && instance.faction().equals(human.hhu$getFaction()));
                markers.add(marker);
            }
        }
        if (terrain) data.put("map_terrain", terrain(player.serverLevel(), boxes));
        data.put("map_bounds", bounds);
        data.put("map_regions", regions);
        data.put("map_markers", markers);
        data.putBoolean("map_limited", limited);
        var self = position(player);
        self.putFloat("yaw", player.getYRot());
        data.put("map_self", self);
        return data;
    }

    private static CompoundTag terrain(net.minecraft.server.level.ServerLevel level, ArrayList<RegionBox> boxes) {
        var extent = boxes.stream().reduce(RegionBox::union).orElseThrow();
        int stride = (int) Math.max(1L, (Math.max(extent.sizeX(), extent.sizeZ()) + 63) / 64);
        int columns = (int) ((extent.sizeX() + stride - 1) / stride);
        int rows = (int) ((extent.sizeZ() + stride - 1) / stride);
        int[] tiles = new int[columns * rows], tints = new int[columns * rows];
        java.util.Arrays.fill(tints,-1);
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                int x = extent.minX() + col * stride;
                int z = extent.minZ() + row * stride;
                if (!level.hasChunkAt(pos.set(x, level.getMinBuildHeight(), z))) continue;
                int surface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int highest = Integer.MIN_VALUE;
                for (var box : boxes) {
                    if (x > box.maxX() || x < box.minX() || z > box.maxZ() || z < box.minZ()) continue;
                    int bottom = Math.max(level.getMinBuildHeight(), Math.max(box.minY(), highest + 1));
                    for (int y = Math.min(surface, box.maxY()); y >= bottom; y--) {
                        var state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir()) continue;
                        highest = y;
                        tiles[row * columns + col] = net.minecraft.world.level.block.Block.getId(state);
                        var biome=level.getBiome(pos).value();
                        if(state.is(net.minecraft.tags.BlockTags.LEAVES))tints[row*columns+col]=biome.getFoliageColor();
                        else if(state.is(net.minecraft.world.level.block.Blocks.WATER))tints[row*columns+col]=biome.getWaterColor();
                        else if(state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                                ||state.getBlock() instanceof net.minecraft.world.level.block.TallGrassBlock
                                ||state.getBlock() instanceof net.minecraft.world.level.block.DoublePlantBlock
                                ||state.is(net.minecraft.world.level.block.Blocks.VINE)
                                ||state.is(net.minecraft.world.level.block.Blocks.SUGAR_CANE))tints[row*columns+col]=biome.getGrassColor(x,z);
                        break;
                    }
                }
            }
        }
        var result = new CompoundTag();
        result.putInt("stride", stride);
        result.putInt("columns", columns);
        result.putIntArray("tiles", tiles);
        result.putIntArray("tints",tints);
        return result;
    }

    private static CompoundTag position(LivingEntity entity) {
        var tag = new CompoundTag();
        tag.putDouble("x", entity.getX());
        tag.putDouble("z", entity.getZ());
        return tag;
    }

    private static int[] coordinates(RegionBox box) {
        return new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
    }
}
