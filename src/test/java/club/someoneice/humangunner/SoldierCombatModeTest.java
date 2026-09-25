package club.someoneice.humangunner;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;

public final class SoldierCombatModeTest {
    private static int checks;

    public static void main(String[] args) {
        check(SoldierCombatMode.permits(SoldierCombatMode.ACTIVE, "SELF"),
                "active mode retaliates for itself");
        check(SoldierCombatMode.permits(SoldierCombatMode.ACTIVE, "OWNER_DEFENSE"),
                "active mode protects its owner");
        check(SoldierCombatMode.permits(SoldierCombatMode.ACTIVE, "OWNER_ASSAULT"),
                "active mode follows its owner's attack");

        check(SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "SELF"),
                "passive protection retains self-defense");
        check(SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "OWNER_DEFENSE"),
                "passive protection reacts when its owner is hurt");
        check(!SoldierCombatMode.permits(SoldierCombatMode.PASSIVE_PROTECTION, "OWNER_ASSAULT"),
                "passive protection ignores its owner's attack");

        check(SoldierCombatMode.permits(SoldierCombatMode.SELF_DEFENSE, "SELF"),
                "fully neutral mode retains self-defense");
        check(!SoldierCombatMode.permits(SoldierCombatMode.SELF_DEFENSE, "OWNER_DEFENSE"),
                "fully neutral mode ignores attacks against its owner");
        check(!SoldierCombatMode.permits(SoldierCombatMode.SELF_DEFENSE, "OWNER_ASSAULT"),
                "fully neutral mode ignores attacks made by its owner");
        check(!SoldierCombatMode.permits(SoldierCombatMode.ACTIVE, "INVALID"),
                "invalid authorization cannot grant a target");

        check(HumanRelationshipPolicy.suppressBadgeBearerAssault(true, true, false),
                "matching badge suppresses an ordered attack on a wild human");
        check(!HumanRelationshipPolicy.suppressBadgeBearerAssault(true, false, false),
                "without a badge an active recruit may follow its owner's assault");
        check(!HumanRelationshipPolicy.suppressBadgeBearerAssault(true, true, true),
                "beacon wave targeted at the owner bypasses badge assault suppression");
        check(!HumanRelationshipPolicy.suppressBadgeBearerAssault(false, true, false),
                "a badge cannot suppress combat against another owned human");

        check(HumanRelationshipPolicy.activeRecruitMayAttackWild(true, true, true, false, false),
                "active recruit autonomously attacks wild humans when its owner has no badge");
        check(!HumanRelationshipPolicy.activeRecruitMayAttackWild(true, true, true, true, false),
                "a badge prevents an unprovoked attack on ordinary wild humans");
        check(HumanRelationshipPolicy.activeRecruitMayAttackWild(true, true, true, true, true),
                "active recruit attacks an owner-targeted beacon wave despite the badge");
        check(!HumanRelationshipPolicy.activeRecruitMayAttackWild(true, false, true, false, true),
                "passive and self-defense modes do not proactively attack wild humans");
        check(!HumanRelationshipPolicy.activeRecruitMayAttackWild(true, true, false, false, true),
                "other players' hired soldiers are not treated as wild enemies");
        check(!HumanRelationshipPolicy.activeRecruitMayAttackWild(false, true, true, false, true),
                "unhired humans do not inherit a player's aggressive recruit policy");
        check(HumanRelationshipPolicy.recentRetaliation(0, 200)
                        && HumanRelationshipPolicy.recentRetaliation(200, 200),
                "a beacon keeps its attacker through the retaliation window");
        check(!HumanRelationshipPolicy.recentRetaliation(201, 200)
                        && !HumanRelationshipPolicy.recentRetaliation(-1, 200),
                "expired or invalid retaliation yields back to the primary player target");
        check(!HumanRelationshipPolicy.shouldReturnToBeaconPlayer(true, true, 60.0D * 60.0D),
                "beacon does not abandon a live retaliation target");
        check(HumanRelationshipPolicy.shouldReturnToBeaconPlayer(false, true, 60.0D * 60.0D)
                        && HumanRelationshipPolicy.shouldReturnToBeaconPlayer(false, true, 128.0D * 128.0D),
                "beacon reacquires its marked player well beyond ordinary human follow range");
        check(!HumanRelationshipPolicy.shouldReturnToBeaconPlayer(false, true, 129.0D * 129.0D)
                        && !HumanRelationshipPolicy.shouldReturnToBeaconPlayer(false, false, 60.0D * 60.0D),
                "beacon does not path toward an unavailable player or beyond the bounded chase range");
        check(HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        true, 100.0D * 100.0D, 56.0D * 56.0D)
                        && !HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        false, 100.0D * 100.0D, 56.0D * 56.0D),
                "only marked beacon gunners close from beyond their ordinary firing range");
        check(!HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        true, 50.0D * 50.0D, 56.0D * 56.0D)
                        && !HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        true, 129.0D * 129.0D, 56.0D * 56.0D),
                "beacon pursuit does not expand gun firing distance or exceed the chase boundary");

        check(!HumanRelationshipPolicy.blockHumanDamage(false, true, true),
                "wild human can land the initiating hit on an owned human");
        check(HumanRelationshipPolicy.blockHumanDamage(true, false, true),
                "owned human cannot hit an allied wild human before authorization");
        check(!HumanRelationshipPolicy.blockHumanDamage(true, false, false),
                "authorized owned human can retaliate against a wild human");
        check(HumanRelationshipPolicy.blockHumanDamage(true, true, true),
                "allied humans sharing an ownership relation retain friendly fire protection");

        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        check(HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, owner, true),
                "a hired human can never attack its own owner");
        check(HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, otherOwner, false),
                "other players are protected while hired PvP is disabled");
        check(!HumanRelationshipPolicy.ownedHumanAlliedToPlayer(owner, otherOwner, true),
                "other players become damageable when hired PvP is enabled");
        check(HumanRelationshipPolicy.ownedHumansAllied(owner, owner, true),
                "same-owner recruits always retain friendly-fire protection");
        check(HumanRelationshipPolicy.ownedHumansAllied(owner, otherOwner, false),
                "other owners' recruits are protected while hired PvP is disabled");
        check(!HumanRelationshipPolicy.ownedHumansAllied(owner, otherOwner, true),
                "other owners' recruits become damageable when hired PvP is enabled");

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID[] targets = new UUID[8];
        long[] expiries = new long[8];
        check(SoldierCombatMode.selectAuthorizationSlot(first, targets, expiries, 100L) == 0,
                "first attacker uses the first free authorization slot");
        targets[0] = first;
        expiries[0] = 200L;
        check(SoldierCombatMode.selectAuthorizationSlot(first, targets, expiries, 100L) == 0,
                "repeated attacker refreshes its existing slot");
        targets[1] = second;
        expiries[1] = 90L;
        check(SoldierCombatMode.selectAuthorizationSlot(third, targets, expiries, 100L) == 1,
                "new attacker reuses an expired slot without evicting live threats");
        for (int slot = 0; slot < targets.length; slot++) {
            targets[slot] = UUID.randomUUID();
            expiries[slot] = 300L + slot;
        }
        expiries[5] = 250L;
        check(SoldierCombatMode.selectAuthorizationSlot(third, targets, expiries, 100L) == 5,
                "ninth attacker replaces the authorization closest to expiry");

        ResourceLocation allowedGun = new ResourceLocation("tacz", "allowed");
        ResourceLocation blockedGun = new ResourceLocation("tacz", "blocked");
        check(HumanGunAcceptance.acceptsGunId(Map.of(), allowedGun),
                "an empty manual whitelist accepts every valid gun id");
        check(!HumanGunAcceptance.acceptsGunId(Map.of(), null),
                "an unreadable gun id is never accepted");
        check(HumanGunAcceptance.acceptsGunId(Map.of(allowedGun, 5), allowedGun),
                "a positive whitelist entry accepts that gun");
        check(!HumanGunAcceptance.acceptsGunId(Map.of(allowedGun, 5), blockedGun),
                "a nonempty whitelist rejects unlisted guns");
        check(!HumanGunAcceptance.acceptsGunId(Map.of(allowedGun, 0), allowedGun),
                "a zero-weight whitelist entry rejects that gun");
        check(HumanGunAcceptance.acceptsHiredGunId(
                        Map.of(allowedGun, 5), Map.of(blockedGun, 1), blockedGun),
                "the hired-only whitelist extends a nonempty wild whitelist");
        check(!HumanGunAcceptance.acceptsHiredGunId(
                        Map.of(allowedGun, 5), Map.of(), blockedGun),
                "an empty hired-only whitelist adds no guns");
        check(HumanGunAcceptance.acceptsHiredGunId(
                        Map.of(), Map.of(blockedGun, 0), blockedGun),
                "an empty wild whitelist retains the accept-all rule for hired humans");

        System.out.println("SoldierCombatModeTest: " + checks + " behavior checks passed");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
