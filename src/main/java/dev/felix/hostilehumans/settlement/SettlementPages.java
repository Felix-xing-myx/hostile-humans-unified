package dev.felix.hostilehumans.settlement;

import java.util.List;

/** Bounds legitimate management snapshots without expanding author geometry or client authority. */
public final class SettlementPages {
    public static final int SIZE = 4;

    private SettlementPages() {}

    public static int pages(int total) {
        if (total < 0) throw new IllegalArgumentException("negative module total");
        return total == 0 ? 1 : 1 + (total - 1) / SIZE;
    }

    public static int page(int requested, int total) {
        return Math.max(0, Math.min(requested, pages(total) - 1));
    }

    public static <T> List<T> slice(List<T> modules, int requested) {
        int start = page(requested, modules.size()) * SIZE;
        return List.copyOf(modules.subList(start, Math.min(modules.size(), start + SIZE)));
    }
}
