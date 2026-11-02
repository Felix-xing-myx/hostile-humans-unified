package dev.felix.hostilehumans.monitor.combat;

/** Optional gun mechanics boundary. No client or optional-mod types escape into the monitor controller. */
public interface MonitorGunActions {
    MonitorGunActions NONE = new MonitorGunActions() {};
    /** Includes a backend-owned charged release, never arbitrary idle firing. */
    default boolean fireIntent(boolean attack) { return attack; }
    default void tick(boolean attack, boolean aim, boolean reload) {}
    default void cancel() {}
}
