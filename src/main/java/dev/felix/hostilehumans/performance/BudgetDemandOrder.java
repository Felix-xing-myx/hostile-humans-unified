package dev.felix.hostilehumans.performance;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanFactions;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
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

    static <K, V> Iterator<Map.Entry<K, V>> interleave(Map<K, V> waiting, Function<V, String> group, String lastGroup) {
        if (waiting.size() < 2) return List.copyOf(waiting.entrySet()).iterator();
        Map<String, ArrayDeque<Map.Entry<K, V>>> groups = new LinkedHashMap<>();
        for (var entry : waiting.entrySet())
            groups.computeIfAbsent(group.apply(entry.getValue()), ignored -> new ArrayDeque<>()).add(entry);
        if (groups.size() == 1) return groups.values().iterator().next().iterator();

        var cycle = new ArrayDeque<>(groups.values());
        if (groups.containsKey(lastGroup)) {
            var last = groups.get(lastGroup);
            while (cycle.peekFirst() != last) cycle.addLast(cycle.removeFirst());
            cycle.addLast(cycle.removeFirst());
        }
        return new Iterator<>() {
            @Override public boolean hasNext() { return !cycle.isEmpty(); }

            @Override public Map.Entry<K, V> next() {
                if (cycle.isEmpty()) throw new NoSuchElementException();
                var next = cycle.removeFirst();
                var entry = next.removeFirst();
                if (!next.isEmpty()) cycle.addLast(next);
                return entry;
            }
        };
    }
}
