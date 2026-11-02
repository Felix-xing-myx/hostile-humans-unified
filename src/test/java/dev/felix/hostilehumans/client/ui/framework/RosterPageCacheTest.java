package dev.felix.hostilehumans.client.ui.framework;

import java.util.List;

public final class RosterPageCacheTest {
    public static void main(String[] args) {
        RosterPageCache<Integer> c = new RosterPageCache<>(8);
        c.filter(-1, -1);
        for (int i = 0; i < 8; i++) c.put(i, List.of(i));
        c.get(0);
        c.put(8, List.of(8));
        check(c.size() == 8, "bounded at eight pages");
        check(c.get(1).isEmpty() && !c.get(0).isEmpty(), "LRU preserves touched page");
        c.filter(2, -1);
        check(c.size() == 0, "tier change clears stale pages");
        c.put(0, List.of(3));
        c.filter(2, 4);
        check(c.size() == 0, "squad change clears stale pages");
        c.put(0, List.of(3));
        c.clear();
        check(c.size() == 0, "mutation/session close invalidates cache");
        System.out.println("RosterPageCacheTest: all checks passed");
    }

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
