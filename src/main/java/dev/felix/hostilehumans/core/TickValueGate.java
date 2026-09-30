package dev.felix.hostilehumans.core;

import java.util.Objects;

/** Coalesces identical notifications only within the same tick and area/value. */
public final class TickValueGate<T> {
    private boolean present;
    private long tick;
    private T value;

    public boolean accept(long tick, T value) {
        if (present && this.tick == tick && Objects.equals(this.value, value)) return false;
        present = true;
        this.tick = tick;
        this.value = value;
        return true;
    }
}
