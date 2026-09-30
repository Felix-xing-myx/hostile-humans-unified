package club.someoneice.humangunner;

import java.util.UUID;

/** Transient entity-local excursion clock, measured in server game ticks. */
public final class GuardCombatState {
    public static final long EXCURSION_TICKS = 400;
    private long outsideSince = -1;
    private UUID attacker;
    private UUID combatTarget;
    private long retaliationUntil;
    private boolean returning;

    public void reset() {
        outsideSince = -1;
        attacker = null;
        combatTarget = null;
        retaliationUntil = 0;
        returning = false;
    }

    public void attacked(UUID id, long now, boolean outside) {
        attacker = id;
        retaliationUntil = now + EXCURSION_TICKS;
        returning = false;
        outsideSince = outside ? now : -1;
    }

    public boolean retaliating(UUID id, long now) {
        return id != null && id.equals(attacker) && now < retaliationUntil;
    }

    public void tick(long now, boolean outside, boolean fighting) {
        if (!outside) { outsideSince = -1; return; }
        if (!fighting || returning) return;
        if (outsideSince < 0 || now < outsideSince) outsideSince = now;
        if (now - outsideSince >= EXCURSION_TICKS) { returning = true; combatTarget = null; }
    }

    public boolean returning() { return returning; }
    public void retainTarget(UUID id) { combatTarget = id; }
    public boolean retainsTarget(UUID id) { return !returning && id != null && id.equals(combatTarget); }
    public UUID attacker(long now) { return now < retaliationUntil ? attacker : null; }
    public void returnedToPost() { returning = false; outsideSince = -1; }
}
