package dev.felix.hostilehumans.compat.maid;

import dev.felix.hostilehumans.faction.BadgePolicy;

/** Only wild humans can be enemies of a player's maid. */
public final class MaidHumanRelationPolicy {
    private MaidHumanRelationPolicy() {}

    public static boolean allowsWildTarget(boolean wildHuman, boolean maid, boolean defending) {
        return !wildHuman || !maid || defending;
    }

    static boolean allowsAttack(boolean ownerPresent, boolean controlledHuman,
                                int clearance, int humanRank,
                                boolean forcedHostile, boolean defending) {
        if (!ownerPresent || controlledHuman) return false;
        return forcedHostile || defending
                || BadgePolicy.relation(clearance, humanRank) == BadgePolicy.Relation.HOSTILE;
    }
}
