package dev.felix.hostilehumans.client.ui.framework;

import java.util.UUID;

public final class PanelSessionViewTest {
    public static void main(String[] args) {
        int[] calls = {0, 0};
        PanelSessionView s =
                new PanelSessionView(UUID.randomUUID(), 20, () -> calls[0]++, () -> calls[1]++);
        for (int i = 0; i < 39; i++) s.tick();
        check(calls[0] == 1, "20 tick heartbeat");
        s.removed();
        s.removed();
        s.tick();
        check(calls[1] == 1, "close at most once");
        PanelSessionView closed =
                new PanelSessionView(UUID.randomUUID(), 1, () -> calls[0]++, () -> calls[1]++);
        closed.suppressClose();
        closed.removed();
        closed.tick();
        check(calls[0] == 1 && calls[1] == 1, "server close/disconnect suppress reply");
        System.out.println("PanelSessionViewTest: all checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
