package dev.felix.hostilehumans.client.ui.framework;

import java.util.UUID;

/** Client lifecycle adapter, deliberately independent of channels and business payloads. */
public final class PanelSessionView {
    private final UUID token;
    private final Runnable heartbeat, close;
    private final int interval;
    private int ticks;
    private boolean closed, suppressed;

    public PanelSessionView(UUID token, int interval, Runnable heartbeat, Runnable close) {
        this.token = token;
        this.interval = interval;
        this.heartbeat = heartbeat;
        this.close = close;
    }

    public UUID token() {
        return token;
    }

    public void tick() {
        if (!closed && !suppressed && interval > 0 && ++ticks % interval == 0) heartbeat.run();
    }

    public void suppressClose() {
        suppressed = true;
    }

    public boolean suppressed() {
        return suppressed;
    }

    public void removed() {
        if (!closed) {
            closed = true;
            if (!suppressed) close.run();
        }
    }
}
