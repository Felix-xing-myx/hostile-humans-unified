package dev.felix.hostilehumans.monitor.client;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;

/** Read-only preflight for callbacks intentionally excluded from predictive physics. */
final class MonitorPredictionEnvironment {
    enum Reason { PREDICTING, WAITING_FOR_AUTHORITY, SERVER_STATE, CONTACT_CALLBACK, CUSTOM_FLUID, EQUIPMENT_SYNC,
        UNLOADED_CHUNK, COLLISION_BUDGET, COMPATIBILITY_FAILURE, HISTORY_RESET, INACTIVE_INPUT, STALE_AUTHORITY }
    private static final Map<Block, Boolean> CONTACT_CALLBACKS = new ConcurrentHashMap<>();
    private MonitorPredictionEnvironment() {}
    static Reason inspect(Level level, AABB swept) {
        int minX = (int) Math.floor(swept.minX), minY = (int) Math.floor(swept.minY), minZ = (int) Math.floor(swept.minZ);
        int maxX = (int) Math.floor(swept.maxX), maxY = (int) Math.floor(swept.maxY), maxZ = (int) Math.floor(swept.maxZ);
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 4096 || volume <= 0) return Reason.COLLISION_BUDGET;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!level.hasChunk(x >> 4, z >> 4)) return Reason.UNLOADED_CHUNK;
            for (int y = minY; y <= maxY; y++) {
                pos.set(x, y, z);
                var state = level.getBlockState(pos);
                if (CONTACT_CALLBACKS.computeIfAbsent(state.getBlock(), MonitorPredictionEnvironment::hasContactCallback))
                    return Reason.CONTACT_CALLBACK;
                var fluid = state.getFluidState();
                if (!fluid.isEmpty() && fluid.getFluidType() != ForgeMod.WATER_TYPE.get()
                        && fluid.getFluidType() != ForgeMod.LAVA_TYPE.get()) return Reason.CUSTOM_FLUID;
            }
        }
        return Reason.PREDICTING;
    }
    private static boolean hasContactCallback(Block block) {
        boolean vanillaStair = block instanceof StairBlock && BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
        try {
            for (Class<?> c = block.getClass(); c != null && c != Block.class && c != BlockBehaviour.class; c = c.getSuperclass()) {
                // Vanilla stairs delegate stepOn to their inert model block; no movement callback is lost.
                if (vanillaStair && c == StairBlock.class) continue;
                for (Method m : c.getDeclaredMethods()) {
                    // Both names are deliberate: production SRG and the development official namespace.
                    if (switch (m.getName()) {
                        case "entityInside", "m_7892_", "stepOn", "m_141947_", "updateEntityAfterFallOn", "m_5548_" -> true;
                        default -> false;
                    }) return true;
                }
            }
            return false;
        } catch (LinkageError | SecurityException unavailable) {
            return true; // Unknown optional callback signatures fail closed without invoking the block.
        }
    }
}
