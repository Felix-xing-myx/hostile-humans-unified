package club.someoneice.humangunner;

/** Only wild humans can be enemies of a player's maid. */
final class MaidHumanRelationPolicy {
    private MaidHumanRelationPolicy() {}

    static boolean allowsAttack(boolean ownerPresent, boolean controlledHuman,
                                int clearance, int humanRank,
                                boolean forcedHostile, boolean defending) {
        if (!ownerPresent || controlledHuman) return false;
        return forcedHostile || defending
                || BadgePolicy.relation(clearance, humanRank) == BadgePolicy.Relation.HOSTILE;
    }
}
