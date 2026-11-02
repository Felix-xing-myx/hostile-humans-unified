package dev.felix.hostilehumans.economy;

import dev.felix.hostilehumans.settlement.SettlementIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Independent bounded position registration. No entity/chunk loads and no settlement membership.
 */
public final class BrokerIndex extends SavedData {
    private static final String FILE = "hostile_humans_unified_brokers";
    private final Map<BlockPos, Currency> entries = new LinkedHashMap<>();
    private final Map<Long, Set<BlockPos>> chunks = new HashMap<>();
    private final List<CompoundTag> unknown = new ArrayList<>();
    private CompoundTag unknownRoot;

    public static BrokerIndex get(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("broker index requires server thread");
        return level.getDataStorage().computeIfAbsent(BrokerIndex::load, BrokerIndex::new, FILE);
    }

    private void put(BlockPos pos, Currency currency) {
        entries.put(pos.immutable(), currency);
        chunks.computeIfAbsent(
                        net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4),
                        k -> new HashSet<>())
                .add(pos.immutable());
    }

    public void register(BlockPos pos, Currency currency) {
        if (unknownRoot != null || entries.size() >= 4096 && !entries.containsKey(pos)) return;
        if (entries.get(pos) == currency) return;
        put(pos, currency);
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (unknownRoot != null) return;
        if (entries.remove(pos) != null) {
            var bucket =
                    chunks.get(
                            net.minecraft.world.level.ChunkPos.asLong(
                                    pos.getX() >> 4, pos.getZ() >> 4));
            if (bucket != null) bucket.remove(pos);
            setDirty();
        }
    }

    public boolean available(ServerLevel level, BlockPos pos, Currency currency) {
        if (unknownRoot != null
                || !unknown.isEmpty()
                || entries.get(pos) != currency
                || !level.hasChunkAt(pos)
                || !(level.getBlockState(pos).getBlock() instanceof EconomyBrokerBlock broker)
                || broker.currency() != currency) return false;
        var region = SettlementIndex.get(level).query(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return !region.unknown() && region.hits().isEmpty();
    }

    public boolean excludesSpawn(ServerLevel level, double x, double y, double z) {
        if (unknownRoot != null) return false;
        int checked = 0;
        int cx = ((int) Math.floor(x)) >> 4, cz = ((int) Math.floor(z)) >> 4;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (var pos :
                        chunks.getOrDefault(
                                net.minecraft.world.level.ChunkPos.asLong(cx + dx, cz + dz),
                                Set.of())) {
                    if (++checked > 64) return false;
                    if (pos.distToCenterSqr(x, y, z) <= 1024
                            && available(level, pos, entries.get(pos))) return true;
                }
        return false;
    }

    public static BrokerIndex load(CompoundTag root) {
        var index = new BrokerIndex();
        if (root.getInt("schema") != 1) {
            index.unknownRoot = root.copy();
            return index;
        }
        for (Tag tag : root.getList("entries", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag;
            try {
                var c = Currency.valueOf(row.getString("currency"));
                var pos = BlockPos.of(row.getLong("pos"));
                if (index.entries.size() >= 4096 || index.entries.containsKey(pos))
                    throw new IllegalArgumentException();
                index.put(pos, c);
            } catch (RuntimeException bad) {
                index.unknown.add(row.copy());
            }
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        if (unknownRoot != null) return unknownRoot.copy();
        root.putInt("schema", 1);
        var rows = new ListTag();
        entries.forEach(
                (pos, c) -> {
                    var row = new CompoundTag();
                    row.putLong("pos", pos.asLong());
                    row.putString("currency", c.name());
                    rows.add(row);
                });
        unknown.forEach(row -> rows.add(row.copy()));
        root.put("entries", rows);
        return root;
    }
}
