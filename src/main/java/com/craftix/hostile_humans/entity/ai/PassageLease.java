package com.craftix.hostile_humans.entity.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One bounded close lease per passage, shared by normal goals and collision recovery. */
@Mod.EventBusSubscriber(modid = "hostile_humans")
public final class PassageLease {
    private static final Map<Level, Map<BlockPos, Lease>> LEVELS = new WeakHashMap<>();
    private record Lease(long openedAt, net.minecraft.world.level.block.Block block) {}
    private PassageLease() {}

    public static boolean handOpenable(BlockState state) {
        return state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
    }

    public static BlockPos normalize(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Call only after changing a closed passage to open. Pre-opened doors have no lease. */
    public static void opened(Mob mob, BlockPos position) {
        if (mob.level().isClientSide) return;
        BlockPos pos = normalize(mob.level(), position).immutable();
        LEVELS.computeIfAbsent(mob.level(), ignored -> new HashMap<>())
                .putIfAbsent(pos, new Lease(mob.level().getGameTime(), mob.level().getBlockState(pos).getBlock()));
    }

    @SubscribeEvent public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END) tick(event.level);
    }

    public static void tick(Level level) {
        if (level.isClientSide) return;
        long now = level.getGameTime();
        Map<BlockPos, Lease> leases = LEVELS.get(level);
        if (leases == null) return;
        leases.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            Lease lease = entry.getValue();
            long elapsed = now - lease.openedAt();
            // Never load chunks for cleanup, or retain leases indefinitely.
            if (!level.hasChunkAt(pos)) return InteractionSafetyPolicy.abandonClose(elapsed);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != lease.block() || !state.hasProperty(BlockStateProperties.OPEN)
                    || !state.getValue(BlockStateProperties.OPEN)) return true;
            if (state.hasProperty(BlockStateProperties.POWERED)
                    && state.getValue(BlockStateProperties.POWERED)) return true;
            if (elapsed < InteractionSafetyPolicy.CLOSE_DELAY) return false;
            AABB doorway = new AABB(pos).expandTowards(0, state.getBlock() instanceof DoorBlock ? 1 : 0, 0);
            boolean occupied = !level.getEntitiesOfClass(Entity.class, doorway.inflate(0.2D),
                    entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
            boolean approaching = level.getEntitiesOfClass(Mob.class, doorway.inflate(3.0D)).stream()
                    .anyMatch(mob -> needsPassage(mob, pos));
            if (InteractionSafetyPolicy.closePassage(elapsed, occupied, approaching)) {
                if (state.getBlock() instanceof DoorBlock door && handOpenable(state))
                    door.setOpen(null, level, state, pos, false);
                else if (state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock)
                    level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, false), 10);
                return true;
            }
            // At the deadline keep occupied/in-use passages open and relinquish ownership.
            return InteractionSafetyPolicy.abandonClose(elapsed);
        });
        if (leases.isEmpty()) LEVELS.remove(level);
    }

    private static boolean needsPassage(Mob mob, BlockPos pos) {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        int next = path.getNextNodeIndex();
        for (int i = next; i < InteractionSafetyPolicy.nearbyNodeLimit(next, path.getNodeCount()); i++) {
            BlockPos node = path.getNodePos(i);
            if (node.getX() == pos.getX() && node.getZ() == pos.getZ()
                    && Math.abs(node.getY() - pos.getY()) <= 2) return true;
        }
        return false;
    }
}
