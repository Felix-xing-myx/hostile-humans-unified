package dev.felix.hostilehumans.perception;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/** Counts actual work in fixed 20-tick windows without retaining entities. */
public final class BattlePerceptionMetrics {
    enum Work { WORLD_QUERY, SHARED_QUERY, CANDIDATE_VISIT, VISION_CLIP, VISION_RETRY_SKIPPED, REPORT_CLIP, REPORT_CACHE, REPORT_DIRECT, REPORT_DELIVERY }
    private static final Map<ServerLevel, Window> LEVELS = new WeakHashMap<>();
    private static final class Window {
        long epoch = Long.MIN_VALUE;
        long[] current = new long[Work.values().length], previous = new long[current.length];

        void advance(long next) {
            if (next == epoch) return;
            var spare = previous;
            previous = current;
            current = spare;
            java.util.Arrays.fill(current, 0);
            if (next - 1 != epoch) java.util.Arrays.fill(previous, 0);
            epoch = next;
        }
    }

    private BattlePerceptionMetrics() {}

    static void add(ServerLevel level, Work work, int amount) {
        var window = LEVELS.computeIfAbsent(level, ignored -> new Window());
        window.advance(level.getGameTime() / 20);
        window.current[work.ordinal()] += amount;
    }

    public static List<String> summary(ServerLevel level) {
        var window = LEVELS.get(level);
        if (window == null) return List.of("团战实际工作 · 暂无采样");
        long epoch = level.getGameTime() / 20;
        long[] counts = window.epoch == epoch ? window.previous : window.epoch == epoch - 1 ? window.current : null;
        if (counts == null) return List.of("团战实际工作 · 暂无采样");
        return List.of(
                "团战候选 · 世界扫描 " + counts[Work.WORLD_QUERY.ordinal()]
                        + " · 复用 " + counts[Work.SHARED_QUERY.ordinal()]
                        + " · 快照候选遍历 " + counts[Work.CANDIDATE_VISIT.ordinal()],
                "前线视觉 · 射线 " + counts[Work.VISION_CLIP.ordinal()]
                        + " · 预算不足略过重筛 " + counts[Work.VISION_RETRY_SKIPPED.ordinal()],
                "团战通报 · 射线 " + counts[Work.REPORT_CLIP.ordinal()]
                        + " · 联络缓存 " + counts[Work.REPORT_CACHE.ordinal()]
                        + " · 近距直达 " + counts[Work.REPORT_DIRECT.ordinal()]
                        + " · 投递 " + counts[Work.REPORT_DELIVERY.ordinal()]);
    }
}
