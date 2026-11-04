package dev.felix.hostilehumans.perception;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Shares loaded-entity enumeration only; each observer still checks hostility and visibility. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class BattleNeighborhood {
    private static final int CELL = 16, MAX_CELLS = 64, MAX_SNAPSHOT = 1024;
    private static final Map<ServerLevel, Frame> FRAMES = new WeakHashMap<>();

    private record Cell(int x, int y, int z, int padding) {}
    private static final class Frame {
        long tick = Long.MIN_VALUE;
        final Map<Cell, List<LivingEntity>> snapshots = new HashMap<>();
    }

    private BattleNeighborhood() {}

    @SubscribeEvent
    public static void endLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            var frame = FRAMES.get(level);
            if (frame != null) frame.snapshots.clear();
        }
    }

    static void visit(ServerLevel level, AABB area, boolean share, Consumer<LivingEntity> visitor) {
        if (share) {
            var frame = FRAMES.computeIfAbsent(level, ignored -> new Frame());
            long tick = level.getGameTime();
            if (frame.tick != tick) {
                frame.tick = tick;
                frame.snapshots.clear();
            }
            var center = area.getCenter();
            int padding = Mth.ceil(Math.max(area.getXsize(), Math.max(area.getYsize(), area.getZsize())) / (2 * CELL));
            var cell = new Cell(Mth.floor(center.x / CELL), Mth.floor(center.y / CELL),
                    Mth.floor(center.z / CELL), padding);
            var snapshot = frame.snapshots.get(cell);
            if (snapshot == null && frame.snapshots.size() < MAX_CELLS) {
                BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.WORLD_QUERY, 1);
                snapshot = snapshot(level, cell);
                frame.snapshots.put(cell, snapshot);
            } else if (snapshot != null && snapshot.size() < MAX_SNAPSHOT) {
                BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.SHARED_QUERY, 1);
            }
            if (snapshot != null && snapshot.size() < MAX_SNAPSHOT) {
                BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.CANDIDATE_VISIT, snapshot.size());
                for (LivingEntity entity : snapshot)
                    if (entity.level() == level && !entity.isRemoved() && entity.getBoundingBox().intersects(area))
                        visitor.accept(entity);
                return;
            }
        }
        if (share) BattlePerceptionMetrics.add(level, BattlePerceptionMetrics.Work.WORLD_QUERY, 1);
        level.getEntities().get(EntityTypeTest.forClass(LivingEntity.class), area, entity -> {
            visitor.accept(entity);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
    }

    private static List<LivingEntity> snapshot(ServerLevel level, Cell cell) {
        double x = cell.x() * (double) CELL, y = cell.y() * (double) CELL, z = cell.z() * (double) CELL;
        var area = new AABB(x, y, z, x + CELL, y + CELL, z + CELL).inflate(cell.padding() * (double) CELL);
        List<LivingEntity> result = new ArrayList<>();
        level.getEntities(EntityTypeTest.forClass(LivingEntity.class), area, entity -> true, result, MAX_SNAPSHOT);
        return result;
    }
}
