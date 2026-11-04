package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.navigation.BasicNavigationBudget;
import dev.felix.hostilehumans.navigation.LongRangeNavigation;
import dev.felix.hostilehumans.navigation.OptionalPathBudget;
import dev.felix.hostilehumans.navigation.OptionalPathBudget.Kind;
import dev.felix.hostilehumans.perception.PerceptionBudget;
import dev.felix.hostilehumans.perception.BattlePerceptionMetrics;
import dev.felix.hostilehumans.performance.AdaptiveActivityBudget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Read-only server counters. Opening the panel does not discover entities or reserve work. */
final class GlobalBudgetSnapshot {
    private GlobalBudgetSnapshot() {}

    static List<String> read(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        String dimension = player.serverLevel().dimension().location().toString();
        List<String> rows = new ArrayList<>();
        rows.add("overview|活动档位 · " + AdaptiveActivityBudget.describe(server));
        rows.add("overview|平均服务器 tick 耗时 · " + String.format(java.util.Locale.ROOT,"%.1f ms",server.getAverageTickTime()));
        rows.add("overview|服务器采样 tick · " + server.getTickCount());
        rows.add("perception|当前维度（首列） · " + dimension.substring(0,Math.min(128,dimension.length())));
        rows.add("perception|邻域查询 · " + PerceptionBudget.usage(server,dimension,"queries"));
        rows.add("perception|感知射线 · " + PerceptionBudget.usage(server,dimension,"rays"));
        rows.add("perception|遮挡复核 · " + PerceptionBudget.usage(server,dimension,"occlusion"));
        rows.add("perception|线索投递 · " + PerceptionBudget.usage(server,dimension,"notices"));
        rows.add("perception|声源采样 · " + PerceptionBudget.usage(server,dimension,"sources"));
        rows.add("perception|团战实际工作 · 上个完整 20 tick");
        for (String line : BattlePerceptionMetrics.summary(player.serverLevel())) rows.add("perception|" + line);
        rows.add("navigation|普通导航 · " + BasicNavigationBudget.usage(server));
        rows.add("navigation|扩展寻路 · " + LongRangeNavigation.usage(server));
        rows.add("navigation|战斗选点 · " + OptionalPathBudget.usage(server,Kind.FIRING_LANE));
        rows.add("navigation|紧急避险 · " + OptionalPathBudget.usage(server,Kind.RETREAT));
        rows.add("navigation|连续性补救 · " + OptionalPathBudget.usage(server,Kind.CONTINUITY));
        rows.add("navigation|增强路径跟随 · " + OptionalPathBudget.usage(server,Kind.PATH_FOLLOW));
        return List.copyOf(rows);
    }
}
