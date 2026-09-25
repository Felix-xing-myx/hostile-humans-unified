package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public final class HumanRelations {
    static final String HIRED_TIER = "humangunner:hired_tier";
    static final String TEMP_OWNER = "humangunner:temporary_owner";
    static final String TEMP_UNTIL = "humangunner:temporary_until";
    static final String NO_DROPS = "humangunner:no_equipment_drops";
    static final String FORCED_HOSTILE_TARGET = "humangunner:forced_hostile_target";
    private static final String GUARD_TARGET = "humangunner:guard_target";
    private static final String GUARD_UNTIL = "humangunner:guard_until";
    private static final double BADGE_GUARD_RADIUS_SQR = 48.0D * 48.0D;
    private static final int BEACON_RETALIATION_TICKS = 200;
    private static final double BEACON_RETALIATION_RANGE_SQR = 64.0D * 64.0D;
    private static final UUID BEACON_FOLLOW_RANGE_ID =
            UUID.fromString("cc24aaaf-a833-4750-9b85-150000000128");
    private static final AttributeModifier BEACON_FOLLOW_RANGE = new AttributeModifier(
            BEACON_FOLLOW_RANGE_ID, "Hostile beacon pursuit range", 88.0D,
            AttributeModifier.Operation.ADDITION);
    private static final UUID GUN_FOLLOW_RANGE_ID =
            UUID.fromString("cc24aaaf-a833-4750-9b85-150000000064");

    private HumanRelations() {}

    static boolean isOwnedBy(Human human, Player player) {
        return human.hasOwner() && player.getUUID().equals(human.getOwnerUUID());
    }

    static boolean isTemporaryFor(Human human, Player player) {
        return human.getPersistentData().hasUUID(TEMP_OWNER)
                && human.getPersistentData().getUUID(TEMP_OWNER).equals(player.getUUID());
    }

    static boolean isControlledBy(Human human, Player player) {
        return isOwnedBy(human, player) || isTemporaryFor(human, player);
    }

    static boolean protects(Human human, Player player) {
        if (isForcedHostileTo(human, player)) return false;
        return isOwnedBy(human, player) || isTemporaryFor(human, player)
                || (effectiveOwner(human) == null
                && IdentityBadgeAccess.isFriendlyTo(human, player)
                && !IdentityBadgeAccess.mayRetaliateAgainst(human, player)
                && human.distanceToSqr(player) <= BADGE_GUARD_RADIUS_SQR);
    }

    static boolean allied(Human human, Entity entity) {
        if (entity instanceof Player player) {
            UUID owner = effectiveOwner(human);
            if (owner != null && owner.equals(player.getUUID())) return true;
            if (isForcedHostileTo(human, player)) return false;
            if (owner != null) return HumanRelationshipPolicy.ownedHumanAlliedToPlayer(
                    owner, player.getUUID(), UnifiedConfig.get().allowHiredPvpDamage());
            return protects(human, player) || IdentityBadgeAccess.blocksInitiatedHostility(human, player);
        }
        if (!(entity instanceof Human other)) return false;
        UUID first = effectiveOwner(human), second = effectiveOwner(other);
        if (first != null && second != null) return HumanRelationshipPolicy.ownedHumansAllied(
                first, second, UnifiedConfig.get().allowHiredPvpDamage());
        // A hired human and a wild human normally keep their original faction
        // relation. An explicit owner/self-defense authorization temporarily
        // overrides it so same-tier wild humans cannot attack the owner with
        // impunity after the owner removes their badge.
        if (first != null && second == null
                && (SoldierCombatMode.isExplicitlyAuthorizedAgainst(human, other)
                || HumanTargeting.isAutonomousWildEnemy(human, other)
                || beaconThreatensOwner(other, first)
                || isRecentHumanAttacker(other, human))) return false;
        if (second != null && first == null
                && (SoldierCombatMode.isExplicitlyAuthorizedAgainst(other, human)
                || HumanTargeting.isAutonomousWildEnemy(other, human)
                || beaconThreatensOwner(human, second)
                || isRecentHumanAttacker(human, other))) return false;
        if (HumanGunner.areSameFaction(human, other)) return true;
        for (Player player : human.level().players()) {
            if (protects(human, player) && protects(other, player)) return true;
        }
        return false;
    }

    static UUID effectiveOwner(Human human) {
        if (human.hasOwner()) return human.getOwnerUUID();
        return human.getPersistentData().hasUUID(TEMP_OWNER)
                ? human.getPersistentData().getUUID(TEMP_OWNER) : null;
    }

    static void dismiss(ServerPlayer owner, Human human) {
        RecruitmentLedger.get(owner.server).dismiss(human.getUUID());
        human.setOwnerUUID(null);
        human.setTame(false);
        human.setOrderedToSit(false);
        human.getPersistentData().remove(HIRED_TIER);
        human.getPersistentData().remove(TEMP_OWNER);
        human.getPersistentData().remove(TEMP_UNTIL);
        human.getPersistentData().remove(NO_DROPS);
        human.getPersistentData().remove(FORCED_HOSTILE_TARGET);
        human.getPersistentData().remove(GUARD_TARGET);
        human.getPersistentData().remove(GUARD_UNTIL);
        SoldierOrder.clear(human);
        SoldierCombatMode.clear(human);
        HumanManagedLoadout.clear(human);
        SoldierPickupPolicy.clear(human);
        OwnerOfflinePolicy.clearForDismiss(human);
        HumanServerData data = HumanServerData.get();
        if (data != null) data.updateOrRegisterHumanMob(human);
    }

    public static boolean isForcedHostileTo(Human human, Player player) {
        return human.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)
                && human.getPersistentData().getUUID(FORCED_HOSTILE_TARGET).equals(player.getUUID());
    }

    private static boolean beaconThreatensOwner(Human wild, UUID owner) {
        return wild.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)
                && wild.getPersistentData().getUUID(FORCED_HOSTILE_TARGET).equals(owner);
    }

    static boolean isRecentBeaconAttacker(Human beacon, LivingEntity attacker) {
        return beacon.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)
                && isRecentHumanAttacker(beacon, attacker);
    }

    static boolean isRecentHumanAttacker(Human wild, LivingEntity attacker) {
        int age = wild.tickCount - wild.getLastHurtByMobTimestamp();
        return effectiveOwner(wild) == null && attacker != null
                && attacker == wild.getLastHurtByMob()
                && HumanRelationshipPolicy.recentRetaliation(age, BEACON_RETALIATION_TICKS);
    }

    static void defend(Player player, LivingEntity attacker) {
        if (!(player.level() instanceof ServerLevel level) || attacker == player) return;
        for (Human human : level.getEntitiesOfClass(Human.class,
                player.getBoundingBox().inflate(48.0D), h -> h.isAlive() && protects(h, player))) {
            if (effectiveOwner(human) != null) {
                if (!SoldierCombatMode.authorizeOwnerDefense(human, attacker) || allied(human, attacker)) continue;
            } else if (allied(human, attacker)) {
                continue;
            }
            assignGuardTarget(human, attacker, level.getGameTime());
        }
    }

    static void followOwnerAssault(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level) || target == player) return;
        for (Human human : level.getEntitiesOfClass(Human.class,
                player.getBoundingBox().inflate(48.0D), h -> h.isAlive() && isControlledBy(h, player))) {
            if (suppressesBadgeBearerAssault(player, target)
                    || !SoldierCombatMode.authorizeOwnerAssault(human, target)
                    || allied(human, target)) continue;
            assignGuardTarget(human, target, level.getGameTime());
        }
    }

    private static boolean suppressesBadgeBearerAssault(Player player, LivingEntity target) {
        if (!(target instanceof Human wild)) return false;
        return HumanRelationshipPolicy.suppressBadgeBearerAssault(
                effectiveOwner(wild) == null,
                IdentityBadgeAccess.highestClearance(player) >= 0,
                isForcedHostileTo(wild, player));
    }

    private static void assignGuardTarget(Human human, LivingEntity target, long now) {
        human.getPersistentData().putUUID(GUARD_TARGET, target.getUUID());
        human.getPersistentData().putLong(GUARD_UNTIL, now + 200L);
        human.setTarget(target);
    }

    static void tick(Human human) {
        boolean beaconHostile = human.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET);
        AttributeInstance followRange = human.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            if (beaconHostile && followRange.getModifier(BEACON_FOLLOW_RANGE_ID) == null) {
                followRange.addTransientModifier(BEACON_FOLLOW_RANGE);
            } else if (!beaconHostile && followRange.getModifier(BEACON_FOLLOW_RANGE_ID) != null) {
                followRange.removeModifier(BEACON_FOLLOW_RANGE_ID);
            }
            // The firing reach is useful only if a gunner can actually acquire
            // a target there. The beacon modifier already grants a longer range;
            // never stack another pursuit bonus on top of it.
            double gunBonus = 0.0D;
            if (!beaconHostile && GunSupport.get().isGun(human.getMainHandItem())) {
                double fireRange = GunRangePolicy.forType(
                        GunSupport.get().gunType(human.getMainHandItem())).fireRange();
                gunBonus = Math.max(0.0D, fireRange - followRange.getBaseValue());
            }
            AttributeModifier currentGunBonus = followRange.getModifier(GUN_FOLLOW_RANGE_ID);
            if (currentGunBonus != null && currentGunBonus.getAmount() != gunBonus) {
                followRange.removeModifier(GUN_FOLLOW_RANGE_ID);
                currentGunBonus = null;
            }
            if (gunBonus > 0.0D && currentGunBonus == null) {
                followRange.addTransientModifier(new AttributeModifier(GUN_FOLLOW_RANGE_ID,
                        "Armed gunner detection range", gunBonus,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        if (beaconHostile
                && human.level() instanceof ServerLevel level) {
            Player player = level.getPlayerByUUID(human.getPersistentData().getUUID(FORCED_HOSTILE_TARGET));
            LivingEntity attacker = human.getLastHurtByMob();
            boolean activeRetaliation = isRecentBeaconAttacker(human, attacker) && attacker.isAlive()
                    && attacker.level() == human.level()
                    && human.distanceToSqr(attacker) <= BEACON_RETALIATION_RANGE_SQR
                    && human.canAttack(attacker)
                    && !allied(human, attacker);
            boolean returnToPlayer = HumanRelationshipPolicy.shouldReturnToBeaconPlayer(
                    activeRetaliation, player != null && player.isAlive(),
                    player == null ? Double.POSITIVE_INFINITY : human.distanceToSqr(player));
            if (activeRetaliation) {
                if (human.getTarget() != attacker) human.setTarget(attacker);
            } else if (returnToPlayer && human.getTarget() != player) {
                human.setTarget(player);
            }
        }
        if (!human.getPersistentData().hasUUID(GUARD_TARGET)
                || human.level().getGameTime() <= human.getPersistentData().getLong(GUARD_UNTIL)) return;
        if (human.getTarget() != null && human.getTarget().getUUID().equals(
                human.getPersistentData().getUUID(GUARD_TARGET))) human.setTarget(null);
        human.getPersistentData().remove(GUARD_TARGET);
        human.getPersistentData().remove(GUARD_UNTIL);
    }
}
