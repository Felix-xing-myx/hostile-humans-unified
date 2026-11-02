package dev.felix.hostilehumans.monitor.combat;

/** A new weapon or resumed input requires a neutral frame before accepting combat buttons. */
public final class MonitorCombatInputGate {
    private boolean armed;
    public boolean accept(boolean active, boolean attack, boolean use, boolean reload) {
        if (!active) { armed = false; return false; }
        if (!armed) {
            armed = !attack && !use && !reload;
            return false;
        }
        return true;
    }
    public void reset() { armed = false; }
}
