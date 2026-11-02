package dev.felix.hostilehumans.faction;

public final class BadgePolicy {
    public enum Relation { HOSTILE, NEUTRAL, FRIENDLY }
    private BadgePolicy() {}

    public static Relation relation(int clearance, int humanRank) {
        if (clearance < humanRank) return Relation.HOSTILE;
        if (clearance == humanRank) return Relation.NEUTRAL;
        return Relation.FRIENDLY;
    }
}
