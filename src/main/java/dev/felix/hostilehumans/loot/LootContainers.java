package dev.felix.hostilehumans.loot;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

/** Container adaptation lives here. Merely inspecting a draft must never unpack its loot. */
public final class LootContainers {
    public record Selection(List<BlockPos> positions, String revision, String table, boolean occupied) {}
    private LootContainers() {}
    private static List<BlockEntity> targets(ServerPlayer player, BlockPos position) {
        var level = player.serverLevel();
        if (!level.hasChunkAt(position) || player.distanceToSqr(position.getX()+.5, position.getY()+.5, position.getZ()+.5) > 64)
            throw new IllegalArgumentException("container_far");
        var entity = level.getBlockEntity(position);
        if (!(entity instanceof RandomizableContainerBlockEntity)) throw new IllegalArgumentException("container_unsupported");
        List<BlockEntity> targets = new ArrayList<>(); targets.add(entity);
        var state = entity.getBlockState();
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos peer = position.relative(ChestBlock.getConnectedDirection(state));
            if (!level.hasChunkAt(peer)) throw new IllegalArgumentException("container_far");
            var other = level.getBlockEntity(peer);
            if (!(other instanceof RandomizableContainerBlockEntity) || other.getBlockState().getBlock() != state.getBlock()
                    || other.getBlockState().getValue(ChestBlock.TYPE) == ChestType.SINGLE
                    || !peer.relative(ChestBlock.getConnectedDirection(other.getBlockState())).equals(position))
                throw new IllegalArgumentException("double_changed");
            targets.add(other);
        }
        targets.sort(Comparator.comparingLong(e -> e.getBlockPos().asLong()));
        return targets;
    }
    public static Selection inspect(ServerPlayer player, BlockPos position) {
        var targets = targets(player, position);
        StringBuilder identity = new StringBuilder(); Set<String> tables = new TreeSet<>(); boolean occupied = false;
        for (var target : targets) {
            var nbt = target.saveWithFullMetadata(); // No getItem/isEmpty/unpackLootTable call.
            identity.append(target.getBlockPos()).append(target.getBlockState()).append(nbt);
            tables.add(nbt.getString("LootTable"));
            occupied |= !nbt.getList("Items", 10).isEmpty();
        }
        return new Selection(targets.stream().map(BlockEntity::getBlockPos).toList(), LootDocuments.digest(identity.toString()),
                String.join(" / ", tables), occupied);
    }
    public static void bind(ServerPlayer player, BlockPos position, String expected, ResourceLocation table, boolean replace) {
        var before = inspect(player, position);
        if (!before.revision().equals(expected)) throw new IllegalArgumentException("container_changed");
        if (before.occupied() && !replace) throw new IllegalArgumentException("container_occupied");
        if (!player.getServer().getLootData().getKeys(net.minecraft.world.level.storage.loot.LootDataType.TABLE).contains(table))
            throw new IllegalArgumentException("table_inactive");
        if (!LootLibrary.containerTable(player.getServer().getLootData().getLootTable(table))) throw new IllegalArgumentException("not_chest");
        var targets = targets(player, position);
        List<CompoundTag> originals = targets.stream().map(BlockEntity::saveWithFullMetadata).toList();
        try {
            for (int i = 0; i < targets.size(); i++) {
                var target = targets.get(i); var nbt = originals.get(i).copy();
                nbt.remove("Items"); nbt.putString("LootTable", table.toString()); nbt.putLong("LootTableSeed", 0);
                target.load(nbt); target.setChanged();
            }
        } catch (RuntimeException failure) {
            for (int i = 0; i < targets.size(); i++) { targets.get(i).load(originals.get(i)); targets.get(i).setChanged(); }
            throw failure;
        }
        for (var target : targets) player.serverLevel().sendBlockUpdated(target.getBlockPos(), target.getBlockState(), target.getBlockState(), 3);
    }
}
