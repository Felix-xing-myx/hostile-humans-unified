package dev.felix.hostilehumans.behavior.combat;

import java.util.UUID;

/** Entity-local finite excursion clock. Hits, target changes and budget waits cannot extend it. */
public final class GuardCombatState {
    public static final long EXCURSION_TICKS = 400;
    private long outsideSince = -1, cooldownUntil;
    private UUID attacker, combatTarget;
    private long retaliationUntil;
    private boolean returning;
    private double returnRadius = -1;
    public void reset() {
        outsideSince = -1; attacker = null; combatTarget = null;
        retaliationUntil = 0; cooldownUntil = 0; returning = false; returnRadius = -1;
    }
    public void attacked(UUID id, long now, boolean outside) {
        attacker = id; retaliationUntil = now + EXCURSION_TICKS;
        if (outside && outsideSince < 0 && !returning && canExcursion(now)) outsideSince = now;
    }
    public boolean retaliating(UUID id, long now) { return id != null && id.equals(attacker) && now < retaliationUntil; }
    public void tick(long now, boolean outside, boolean fighting) { tick(now, outside, false, fighting, EXCURSION_TICKS); }
    public void tick(long now, boolean outside, boolean atLimit, boolean fighting, long duration) {
        if (returning) return;
        if (outside && outsideSince < 0) outsideSince = now;
        if (atLimit || (outside && !canExcursion(now))
                || (outsideSince >= 0 && (!fighting || now - outsideSince >= duration || now < outsideSince))) beginReturn();
        if (!outside && !fighting && outsideSince < 0) combatTarget = null;
    }
    public boolean canExcursion(long now) { return !returning && now >= cooldownUntil; }
    public boolean returning() { return returning; }
    public void beginReturn() { if (!returning) returnRadius = -1; returning = true; combatTarget = null; }
    public void bindReturnRadius(double radius) { if (returning && returnRadius < 0) returnRadius = radius; }
    public double returnRadius() { return returnRadius; }
    public void retainTarget(UUID id) { if (!returning) combatTarget = id; }
    public boolean retainsTarget(UUID id) { return !returning && id != null && id.equals(combatTarget); }
    public UUID attacker(long now) { return now < retaliationUntil ? attacker : null; }
    public void returnedToPost() { returnedToPost(0, 0); }
    public void returnedToPost(long now, long cooldown) {
        if (!returning) return;
        returning = false; outsideSince = -1; combatTarget = null; cooldownUntil = now + cooldown; returnRadius = -1;
    }
    public long outsideSince() { return outsideSince; }
    public long cooldownUntil() { return cooldownUntil; }
}
