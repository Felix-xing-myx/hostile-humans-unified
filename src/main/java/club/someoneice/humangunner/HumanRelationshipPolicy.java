package club.someoneice.humangunner;

import java.util.UUID;

/** Pure decisions shared by vanilla, TaCZ and command-event relationship paths. */
final class HumanRelationshipPolicy {
    static final double BEACON_PLAYER_REACQUIRE_RANGE_SQR = 128.0D * 128.0D;
    private HumanRelationshipPolicy() {}

    static boolean suppressBadgeBearerAssault(boolean targetIsWild, boolean ownerHasBadge,
                                              boolean forcedHostileToOwner) {
        return targetIsWild && ownerHasBadge && !forcedHostileToOwner;
    }

    static boolean activeRecruitMayAttackWild(boolean formallyHired, boolean active,
                                              boolean targetIsWild, boolean ownerHasBadge,
                                              boolean forcedHostileToOwner) {
        return formallyHired && active && targetIsWild
                && (!ownerHasBadge || forcedHostileToOwner);
    }

    static boolean recentRetaliation(int ageTicks, int windowTicks) {
        return ageTicks >= 0 && ageTicks <= windowTicks;
    }

    static boolean shouldReturnToBeaconPlayer(boolean activeRetaliation,
                                              boolean playerAvailable, double distanceSqr) {
        return !activeRetaliation && playerAvailable
                && distanceSqr <= BEACON_PLAYER_REACQUIRE_RANGE_SQR;
    }

    static boolean shouldApproachBeaconPlayerWithoutFiring(boolean markedPlayer,
                                                           double distanceSqr, double gunRangeSqr) {
        return markedPlayer && distanceSqr > gunRangeSqr
                && distanceSqr <= BEACON_PLAYER_REACQUIRE_RANGE_SQR;
    }

    static boolean blockHumanDamage(boolean attackerHasEffectiveOwner,
                                    boolean defenderHasEffectiveOwner,
                                    boolean otherwiseAllied) {
        if (!attackerHasEffectiveOwner && defenderHasEffectiveOwner) return false;
        return otherwiseAllied;
    }

    static boolean ownedHumanAlliedToPlayer(UUID humanOwner, UUID player,
                                             boolean allowHiredPvpDamage) {
        return humanOwner != null
                && (humanOwner.equals(player) || !allowHiredPvpDamage);
    }

    static boolean ownedHumansAllied(UUID firstOwner, UUID secondOwner,
                                     boolean allowHiredPvpDamage) {
        return firstOwner != null && secondOwner != null
                && (firstOwner.equals(secondOwner) || !allowHiredPvpDamage);
    }
}
