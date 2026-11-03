package dev.felix.hostilehumans.performance;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanFactions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;

/** Interleaves already waiting groups, keeping FIFO order within each group. */
public final class BudgetDemandOrder {
    private BudgetDemandOrder() {}

    public static String faction(Entity actor) {
        return actor instanceof Human human ? HumanFactions.shortId(human) : "";
    }

    static <K, V> List<Map.Entry<K, V>> interleave(Map<K, V> waiting, Function<V, String> group, String lastGroup) {
        if (waiting.size() < 2) return List.copyOf(waiting.entrySet());
        Map<String, ArrayDeque<Map.Entry<K, V>>> groups = new LinkedHashMap<>();
        for (var entry : waiting.entrySet())
            groups.computeIfAbsent(group.apply(entry.getValue()), ignored -> new ArrayDeque<>()).add(entry);
        if (groups.size() == 1) return List.copyOf(waiting.entrySet());

        var cycle = new ArrayDeque<>(groups.values());
        if (groups.containsKey(lastGroup)) {
            var last = groups.get(lastGroup);
            while (cycle.peekFirst() != last) cycle.addLast(cycle.removeFirst());
            cycle.addLast(cycle.removeFirst());
        }
        List<Map.Entry<K, V>> order = new ArrayList<>(waiting.size());
        while (!cycle.isEmpty()) {
            var next = cycle.removeFirst();
            order.add(next.removeFirst());
            if (!next.isEmpty()) cycle.addLast(next);
        }
        return order;
    }
}
