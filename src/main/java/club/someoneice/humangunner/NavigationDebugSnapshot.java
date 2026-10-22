package club.someoneice.humangunner;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server geometry and bounded diagnostic values; no world/entity references or client-side authority. */
public record NavigationDebugSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid,
        String name, String mode, String state, String navigator, String budget, String obstruction,
        float health, double fallBudget, int maxDrop, double followRange,
        Vec3 position, BlockPos requested, Vec3 enemy, Vec3 endpoint, List<Vec3> path, int firstNode, int totalNodes,
        boolean complete, boolean truncated, double searchMillis, List<Sample> samples, PerceptionDebugSnapshot perception, BehaviorDebugSnapshot behavior) {
    public NavigationDebugSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid,
            String name, String mode, String state, String navigator, String budget, String obstruction,
            float health, double fallBudget, int maxDrop, double followRange,
            Vec3 position, BlockPos requested, Vec3 enemy, Vec3 endpoint, List<Vec3> path, int firstNode, int totalNodes,
            boolean complete, boolean truncated, double searchMillis, List<Sample> samples) {
        this(dimension, entityId, entityUuid, name, mode, state, navigator, budget, obstruction, health,
                fallBudget, maxDrop, followRange, position, requested, enemy, endpoint, path, firstNode, totalNodes,
                complete, truncated, searchMillis, samples, PerceptionDebugSnapshot.EMPTY);
    }
    public NavigationDebugSnapshot(ResourceLocation dimension, int entityId, UUID entityUuid,
            String name, String mode, String state, String navigator, String budget, String obstruction,
            float health, double fallBudget, int maxDrop, double followRange,
            Vec3 position, BlockPos requested, Vec3 enemy, Vec3 endpoint, List<Vec3> path, int firstNode, int totalNodes,
            boolean complete, boolean truncated, double searchMillis, List<Sample> samples, PerceptionDebugSnapshot perception) {
        this(dimension, entityId, entityUuid, name, mode, state, navigator, budget, obstruction, health,
                fallBudget, maxDrop, followRange, position, requested, enemy, endpoint, path, firstNode, totalNodes,
                complete, truncated, searchMillis, samples, perception, BehaviorDebugSnapshot.EMPTY);
    }
    public static final int MAX_DIMENSION_ID = 256;
    public static final int MAX_PATH = 256;
    public static final int MAX_SAMPLES = 48;
    public record Sample(Vec3 position, String source, String result, double score) {}
}
