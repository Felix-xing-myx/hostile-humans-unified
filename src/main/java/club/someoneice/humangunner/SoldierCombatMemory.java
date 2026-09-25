package club.someoneice.humangunner;

import java.util.LinkedHashMap;
import java.util.UUID;

/** Entity-owned transient state: no entity references, world globals or saved timers. */
public final class SoldierCombatMemory {
    public static final long NO_DAMAGE_TICKS = 600;
    public static final long RETRY_TICKS = 60;
    private final LinkedHashMap<UUID, Long> ignored = new LinkedHashMap<>();
    private UUID target;
    private long lastDamage;

    public boolean allowed(UUID candidate, long now) {
        ignored.values().removeIf(until -> now >= until);
        return !ignored.containsKey(candidate);
    }

    public boolean timedOut(UUID candidate, long now) {
        if (!candidate.equals(target)) { target = candidate; lastDamage = now; }
        return now - lastDamage >= NO_DAMAGE_TICKS;
    }

    public void hit(UUID victim, long now) {
        if (victim.equals(target)) lastDamage = now;
    }

    public void threatened(UUID attacker, long now) {
        if (attacker.equals(target)) lastDamage = now;
    }

    public void forget(UUID candidate, long now) {
        if (candidate != null) {
            ignored.put(candidate, now + RETRY_TICKS);
            while (ignored.size() > 16) ignored.remove(ignored.keySet().iterator().next());
        }
        target = null;
    }
}
