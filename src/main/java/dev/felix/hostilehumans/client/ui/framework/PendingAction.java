package dev.felix.hostilehumans.client.ui.framework;

import java.util.Objects;
import java.util.UUID;

/**
 * A revision is not an acknowledgement. Only the exact transaction id releases the pending gate.
 */
public final class PendingAction<T> {
    private UUID id;
    private T request;

    public boolean busy() {
        return id != null;
    }

    public void begin(UUID id, T request) {
        if (busy()) throw new IllegalStateException("operation still pending");
        if (new UUID(0, 0).equals(id)) throw new IllegalArgumentException("zero id is unsolicited");
        this.id = Objects.requireNonNull(id);
        this.request = Objects.requireNonNull(request);
    }

    public boolean acknowledge(UUID ack) {
        if (id != null && id.equals(ack)) {
            id = null;
            request = null;
            return true;
        }
        return false;
    }

    public T request() {
        return request;
    }
}
