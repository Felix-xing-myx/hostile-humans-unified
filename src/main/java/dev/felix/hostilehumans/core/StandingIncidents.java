package dev.felix.hostilehumans.core;

import java.util.*;

/** Per-victim short-term memory. No political scores or entity references are stored here. */
public final class StandingIncidents {
    public enum Reason { NONE, SELF_DEFENSE, REPEATED_HITS, DAMAGE_THRESHOLD, HIGHER_RANK, GROUP_ALERT }
    public record Outcome(int standingPenalty, boolean notifyGroup, Reason reason) {}
    private static final int MAX_PLAYERS = 16;
    public record Settings(int hitWindow, int hitLimit, int damageWindow, double damageFraction,
                           int angerTicks, int groupTicks, int crossTierWindow, int crossTierHits) {
        public Settings {
            if (hitWindow <= 0 || hitLimit <= 0 || damageWindow <= 0 || !Double.isFinite(damageFraction)
                    || damageFraction <= 0 || damageFraction > 1 || angerTicks <= 0 || groupTicks <= 0
                    || crossTierWindow <= 0 || crossTierHits <= 0) throw new IllegalArgumentException("Invalid incident settings");
        }
        public static Settings defaults() { return new Settings(300, 5, 800, .30, 600, 600, 100, 2); }
    }
    private Settings settings;
    public StandingIncidents() { this(Settings.defaults()); }
    public StandingIncidents(Settings settings) { this.settings = Objects.requireNonNull(settings); }
    public void configure(Settings settings) { this.settings = Objects.requireNonNull(settings); }
    private final Map<UUID, Memory> players = new LinkedHashMap<>();
    private static final class Damage {
        final long tick;
        double amount;
        Damage(long tick, double amount) { this.tick = tick; this.amount = amount; }
    }
    private static final class Action {
        final long tick;
        final String weapon, nativeId;
        int charged;
        Action(long tick, String weapon, String nativeId) { this.tick = tick; this.weapon = weapon; this.nativeId = nativeId; }
    }
    private static final class Memory {
        long lastActivity, angerUntil;
        double maximumHealth;
        Reason reason = Reason.NONE;
        final Deque<Action> actions = new ArrayDeque<>();
        final Deque<Damage> damage = new ArrayDeque<>();
        void prune(long now, Settings settings) {
            actions.removeIf(a -> a.tick > now || now - a.tick >= Math.max(settings.hitWindow(), Math.max(settings.damageWindow(), settings.crossTierWindow())));
            damage.removeIf(d -> d.tick > now || now - d.tick >= settings.damageWindow());
            if (angerUntil <= now || lastActivity > now) { angerUntil = 0; reason = Reason.NONE; }
        }
    }
    private Memory memory(UUID player, long now) {
        players.values().forEach(m -> m.prune(now, settings));
        players.entrySet().removeIf(e -> e.getValue().angerUntil <= now && e.getValue().actions.isEmpty() && e.getValue().damage.isEmpty());
        Memory found = players.get(player);
        if (found != null) return found;
        if (players.size() >= MAX_PLAYERS) {
            // Inactive memories are evicted first, then the least recently active if all sixteen are angry.
            UUID victim = players.entrySet().stream().min(Comparator
                    .comparingInt((Map.Entry<UUID, Memory> e) -> e.getValue().angerUntil > now ? 1 : 0)
                    .thenComparingLong(e -> e.getValue().lastActivity)).orElseThrow().getKey();
            players.remove(victim);
        }
        Memory created = new Memory(); created.lastActivity = now; players.put(player, created); return created;
    }
    private static Action action(Memory memory, long now, String weapon, String nativeId) {
        for (Iterator<Action> it = memory.actions.descendingIterator(); it.hasNext();) {
            Action a = it.next();
            if (nativeId != null ? nativeId.equals(a.nativeId) : a.nativeId == null && now >= a.tick && now - a.tick < 2 && a.weapon.equals(weapon))
                return a;
        }
        Action next = new Action(now, weapon, nativeId); memory.actions.addLast(next); return next;
    }
    /** effectiveStanding and bandGrade must be sampled before applying the returned political penalty. */
    public Outcome damage(UUID player, long now, String weapon, String nativeAction, double amount,
                          double maximumHealth, int effectiveStanding, int bandGrade, int victimGrade) {
        if (player == null || weapon == null || !Double.isFinite(amount) || amount <= 0
                || !Double.isFinite(maximumHealth) || maximumHealth <= 0) return new Outcome(0, false, Reason.NONE);
        Memory m = memory(player, now);
        if (m.damage.isEmpty()) m.maximumHealth = maximumHealth;
        Damage last = m.damage.peekLast();
        if (last != null && last.tick == now) last.amount += amount; else m.damage.addLast(new Damage(now, amount));
        Action action = action(m, now, weapon, nativeAction);
        int penalty = action.charged == 0 ? 6 : 0;
        action.charged = Math.max(action.charged, 6);
        m.lastActivity = now;
        boolean tolerant = bandGrade > 0 && effectiveStanding >= -99;
        boolean group = false;
        Reason reason = m.angerUntil > now ? m.reason : Reason.NONE;
        if (!tolerant) reason = Reason.SELF_DEFENSE;
        else if (victimGrade > bandGrade) {
            long count = m.actions.stream().filter(a -> now - a.tick < settings.crossTierWindow()).count();
            if (count >= (effectiveStanding >= 50 ? settings.crossTierHits() : 1)) { reason = Reason.HIGHER_RANK; group = penalty > 0; }
        } else {
            long count = m.actions.stream().filter(a -> now - a.tick < settings.hitWindow()).count();
            double harm = m.damage.stream().mapToDouble(d -> d.amount).sum();
            if (count >= settings.hitLimit()) reason = Reason.REPEATED_HITS;
            else if (harm >= m.maximumHealth * settings.damageFraction()) reason = Reason.DAMAGE_THRESHOLD;
        }
        if (reason != Reason.NONE) { m.reason = reason; m.angerUntil = Math.max(m.angerUntil, now + settings.angerTicks()); }
        return new Outcome(penalty, group, reason);
    }
    /** Completes the lethal action to -18 total; calling it twice has no additional effect. */
    public int death(UUID player, long now, String weapon, String nativeAction) {
        Memory memory = memory(player, now);
        Action action = action(memory, now, weapon, nativeAction);
        int remaining = Math.max(0, 18 - action.charged);
        action.charged = 18; memory.lastActivity = now;
        return remaining;
    }
    public void groupAlert(UUID player, long now) {
        Memory memory = memory(player, now);
        memory.reason = Reason.GROUP_ALERT; memory.angerUntil = now + settings.groupTicks(); memory.lastActivity = now;
    }
    public Reason reason(UUID player, long now) {
        Memory memory = players.get(player);
        if (memory == null) return Reason.NONE;
        memory.prune(now, settings);
        return memory.angerUntil > now ? memory.reason : Reason.NONE;
    }
    public int size() { return players.size(); }
}
