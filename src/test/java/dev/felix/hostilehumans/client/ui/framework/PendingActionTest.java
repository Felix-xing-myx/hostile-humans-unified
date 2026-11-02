package dev.felix.hostilehumans.client.ui.framework;

import java.util.UUID;

public final class PendingActionTest {
    public static void main(String[] args) {
        PendingAction<String> p = new PendingAction<>();
        UUID id = UUID.randomUUID();
        p.begin(id, "original request");
        check(!p.acknowledge(new UUID(0, 0)) && p.busy(), "unsolicited snapshot cannot unlock");
        check(!p.acknowledge(UUID.randomUUID()) && p.busy(), "unrelated reply cannot unlock");
        check(p.request().equals("original request"), "retry retains original payload/id");
        check(p.acknowledge(id) && !p.busy(), "exact ack unlocks even if revision did not change");
        check(!p.acknowledge(id), "duplicate reply harmless");
        System.out.println("PendingActionTest: all checks passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
