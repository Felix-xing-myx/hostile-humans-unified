package dev.felix.hostilehumans.observer;

import java.util.List;
import java.util.UUID;

/** One replace-all observer frame prevents stale removed entities and bounds aggregate traffic. */
public record NavigationDebugFrame(List<Entry> entries, UUID focused) {
    public static final int MAX_ENTITIES = 8;
    public static final int SENT_SAMPLES = 16;
    public static final NavigationDebugFrame EMPTY = new NavigationDebugFrame(List.of(), null);
    public record Entry(int slot, NavigationDebugSnapshot snapshot) {}
}
