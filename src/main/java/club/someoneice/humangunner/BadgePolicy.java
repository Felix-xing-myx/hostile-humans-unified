package club.someoneice.humangunner;

final class BadgePolicy {
    enum Relation { HOSTILE, NEUTRAL, FRIENDLY }
    private BadgePolicy() {}

    static Relation relation(int clearance, int humanRank) {
        if (clearance < humanRank) return Relation.HOSTILE;
        if (clearance == humanRank) return Relation.NEUTRAL;
        return Relation.FRIENDLY;
    }
}
