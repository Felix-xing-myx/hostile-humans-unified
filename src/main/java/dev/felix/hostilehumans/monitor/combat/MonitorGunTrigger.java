package dev.felix.hostilehumans.monitor.combat;

/** Trigger edges are independent of native cooldowns: only an accepted shot consumes a semi-auto press. */
public final class MonitorGunTrigger {
    private boolean latched;
    public boolean held(boolean pressed) {
        if (!pressed) latched = false;
        return pressed && !latched;
    }
    public void accepted(boolean continuous) { latched = !continuous; }
    public void reset() { latched = false; }
}
