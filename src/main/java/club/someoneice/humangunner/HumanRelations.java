package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;

public final class HumanRelations {
    static final String HIRED_TIER = "hostile_humans_unified:hired_tier";
    public static final String TEMP_OWNER = "hostile_humans_unified:temporary_owner";
    static final String TEMP_UNTIL = "hostile_humans_unified:temporary_until";
    static final String NO_DROPS = "hostile_humans_unified:no_equipment_drops";
    static final String FORCED_HOSTILE_TARGET = "hostile_humans_unified:forced_hostile_target";
    static final String HOSTILE_BEACON_ORIGIN = "hostile_humans_unified:hostile_beacon_origin";
    private static final String GUARD_TARGET = "hostile_humans_unified:guard_target";
    private static final String GUARD_UNTIL = "hostile_humans_unified:guard_until";
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
    // Only the spatial broad phase is memoized. Ownership, badges and combat
    // authorization must be read live for every individual attacker event.
    private static final Map<Player, NearbyHumans> NEARBY_HUMANS = new WeakHashMap<>();
    private record NearbyHumans(long tick, ResourceKey<Level> dimension, AABB bounds,
                                List<WeakReference<Human>> humans) {}

    private HumanRelations() {}

    /** Immutable origin identity plus existing bounded permission/incident facts, not resolved relationships. */
    public record OriginIdentity(UUID human, UUID owner, long ownerUntil, String tier,
                                 UUID forcedHostilePlayer, UUID lastAttacker, long retaliationUntil,
                                 SoldierCombatMode.OriginPermissions commands, StandingEffects.OriginPolitics politics) {
        public UUID effectiveOwner(long now) { return ownerUntil == 0 || now < ownerUntil ? owner : null; }
        public net.minecraft.nbt.CompoundTag save() {
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putUUID("human", human);
            if (owner != null) tag.putUUID("owner", owner);
            tag.putLong("owner_until", ownerUntil); tag.putString("tier", tier);
            if (forcedHostilePlayer != null) tag.putUUID("forced", forcedHostilePlayer);
            if (lastAttacker != null) tag.putUUID("last_attacker", lastAttacker);
            tag.putLong("retaliation_until", retaliationUntil);
            tag.put("commands", commands.save()); tag.put("politics", politics.save()); return tag;
        }
        public static OriginIdentity load(net.minecraft.nbt.CompoundTag tag) {
            if (!tag.hasUUID("human")) return null;
            return new OriginIdentity(tag.getUUID("human"), tag.hasUUID("owner") ? tag.getUUID("owner") : null,
                    tag.getLong("owner_until"), tag.getString("tier"), tag.hasUUID("forced") ? tag.getUUID("forced") : null,
                    tag.hasUUID("last_attacker") ? tag.getUUID("last_attacker") : null, tag.getLong("retaliation_until"),
                    SoldierCombatMode.OriginPermissions.load(tag.getCompound("commands")),
                    StandingEffects.OriginPolitics.load(tag.getCompound("politics")));
        }
    }
    public static OriginIdentity originIdentity(Human human) {
        long now = human.level().getGameTime();
        UUID owner = effectiveOwner(human);
        LivingEntity attacker = human.getLastHurtByMob();
        int age = human.tickCount - human.getLastHurtByMobTimestamp();
        boolean recent = attacker != null && HumanRelationshipPolicy.recentRetaliation(age, BEACON_RETALIATION_TICKS);
        return new OriginIdentity(human.getUUID(), owner, human.hasOwner() ? 0 : human.getPersistentData().getLong(TEMP_UNTIL),
                TierAttributes.key(human), human.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)
                    ? human.getPersistentData().getUUID(FORCED_HOSTILE_TARGET) : null,
                recent ? attacker.getUUID() : null, recent ? now + BEACON_RETALIATION_TICKS - age : Long.MIN_VALUE,
                SoldierCombatMode.originPermissions(human), StandingEffects.originPolitics(human));
    }
    /** The same relationship branches as relation(Human,Entity), with current target/owner politics. */
    public static boolean originAlliedToHuman(OriginIdentity source, Human target) {
        long now = target.level().getGameTime();
        UUID owner = source.effectiveOwner(now), second = effectiveOwner(target);
        if (owner != null && second != null)
            return HumanRelationshipPolicy.ownedHumansAllied(owner, second, UnifiedConfig.get().allowHiredPvpDamage());
        if (owner != null) {
            if (source.commands().permits(target.getUUID(), now)) return false;
            LivingEntity last = target.getLastHurtByMob();
            if (last != null && last.getUUID().equals(source.human())
                    && HumanRelationshipPolicy.recentRetaliation(target.tickCount - target.getLastHurtByMobTimestamp(), BEACON_RETALIATION_TICKS)) return false;
            if (beaconThreatensOwner(target, owner)) return false;
            Player player = target.level().getPlayerByUUID(owner);
            return player != null && relation(target, player).friendly();
        }
        if (second != null) {
            if (SoldierCombatMode.originPermissions(target).permits(source.human(), now)) return false;
            if (source.lastAttacker() != null && source.lastAttacker().equals(target.getUUID()) && now <= source.retaliationUntil()) return false;
            if (second.equals(source.forcedHostilePlayer())) return false;
            Player player = target.level().getPlayerByUUID(second);
            return player != null && !player.getUUID().equals(source.forcedHostilePlayer())
                    && StandingEffects.neutralTo(source.politics(), player, now);
        }
        if (source.politics().roamer() || target.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER) return false;
        return HumanFactions.enabled() ? FactionDefinitions.allies(source.politics().faction(), HumanFactions.id(target))
                : source.tier().equals(TierAttributes.key(target));
    }

    static boolean isOwnedBy(Human human, Player player) {
        return HumanRelationshipPolicy.isFormalOwner(human.hasOwner(), human.getOwnerUUID(), player.getUUID());
    }

    static boolean isTemporaryFor(Human human, Player player) {
        return temporaryOwner(human) != null && temporaryOwner(human).equals(player.getUUID());
    }

    static boolean isControlledBy(Human human, Player player) {
        return isOwnedBy(human, player) || isTemporaryFor(human, player);
    }

    static boolean protects(Human human, Player player) {
        if (isOwnedBy(human, player) || isTemporaryFor(human, player)) return true;
        if (isForcedHostileTo(human, player)) return false;
        return (effectiveOwner(human) == null
                && StandingEffects.isFriendlyTo(human, player)
                && !StandingEffects.mayRetaliateAgainst(human, player)
                && human.distanceToSqr(player) <= BADGE_GUARD_RADIUS_SQR);
    }

    enum RelationReason { SAME_OWNER, PVP_PROTECTION, OWNER_POLITICS, SELF_DEFENSE, EXPLICIT_PERMISSION,
        ROAMER, SAME_FACTION, FACTION_ENEMY, PLAYER_STANDING, SHORT_TERM_ANGER, BEACON, UNKNOWN }
    record Relation(boolean friendly, boolean hostile, RelationReason reason) {}

    /** One relationship resolver for targeting, damage and interception. It never confers perception. */
    static Relation relation(Human human, Entity entity) {
        UUID owner = effectiveOwner(human);
        if (entity instanceof Player player) {
            if (owner != null && owner.equals(player.getUUID())) return new Relation(true, false, RelationReason.SAME_OWNER);
            if (owner != null) return new Relation(!UnifiedConfig.get().allowHiredPvpDamage(),
                    UnifiedConfig.get().allowHiredPvpDamage(), RelationReason.PVP_PROTECTION);
            if (isForcedHostileTo(human, player)) return new Relation(false, true, RelationReason.BEACON);
            if (human.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER)
                return new Relation(false, true, RelationReason.ROAMER);
            if (StandingEffects.mayRetaliateAgainst(human, player)) return new Relation(false, true, RelationReason.SHORT_TERM_ANGER);
            boolean peaceful = StandingEffects.isNeutralTo(human, player);
            return new Relation(peaceful, !peaceful, RelationReason.PLAYER_STANDING);
        }
        if (entity instanceof Human other) {
            UUID second = effectiveOwner(other);
            if (owner != null && second != null) {
                boolean friendly = HumanRelationshipPolicy.ownedHumansAllied(owner, second, UnifiedConfig.get().allowHiredPvpDamage());
                return new Relation(friendly, !friendly, owner.equals(second) ? RelationReason.SAME_OWNER : RelationReason.PVP_PROTECTION);
            }
            if (owner != null || second != null) {
                Human controlled = owner != null ? human : other;
                Human wild = owner != null ? other : human;
                UUID controlling = owner != null ? owner : second;
                if (SoldierCombatMode.isExplicitlyAuthorizedAgainst(controlled, wild))
                    return new Relation(false, true, RelationReason.EXPLICIT_PERMISSION);
                if (isRecentHumanAttacker(wild, controlled) || beaconThreatensOwner(wild, controlling))
                    return new Relation(false, true, RelationReason.SELF_DEFENSE);
                Player player = human.level().getPlayerByUUID(controlling);
                if (player != null) {
                    Relation political = relation(wild, player);
                    return new Relation(political.friendly(), political.hostile(), RelationReason.OWNER_POLITICS);
                }
                return new Relation(false, false, RelationReason.UNKNOWN);
            }
            if (HumanFactions.same(human, other)) return new Relation(true, false, RelationReason.SAME_FACTION);
            if (isRecentHumanAttacker(human, other)) return new Relation(false, true, RelationReason.SELF_DEFENSE);
            if (HumanFactions.enemies(human, other)) return new Relation(false, true,
                    human.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER
                    || other.getTier() == com.craftix.hostile_humans.entity.entities.HumanTier.ROAMER
                    ? RelationReason.ROAMER : RelationReason.FACTION_ENEMY);
            return new Relation(false, false, RelationReason.UNKNOWN);
        }
        if (entity instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwnerUUID() != null) {
            Player player = human.level().getPlayerByUUID(owned.getOwnerUUID());
            if (player != null) return relation(human, player);
            if (owner != null && owner.equals(owned.getOwnerUUID())) return new Relation(true, false, RelationReason.SAME_OWNER);
        }
        return new Relation(false, false, RelationReason.UNKNOWN);
    }

    static boolean hostileToOwner(Human wild, Player owner) {
        return effectiveOwner(wild) == null && relation(wild, owner).hostile();
    }
    static boolean allied(Human human, Entity entity) { return relation(human, entity).friendly(); }
    static boolean humanHostile(Human human, Human target) { return relation(human, target).hostile(); }
    private static UUID temporaryOwner(Human human) {
        var data = human.getPersistentData();
        return data.hasUUID(TEMP_OWNER) && data.getLong(TEMP_UNTIL) > human.level().getGameTime()
                ? data.getUUID(TEMP_OWNER) : null;
    }

    static UUID effectiveOwner(Human human) {
        return human.hasOwner() ? human.getOwnerUUID() : temporaryOwner(human);
    }

    static void dismiss(ServerPlayer owner, Human human) {
        dismiss(owner.server, human);
    }

    static void dismiss(MinecraftServer server, Human human) {
        persistHostileBeaconOrigin(human);
        RecruitmentLedger.get(server).dismiss(human.getUUID());
        SquadData.get(server).removeMember(human.getUUID());
        human.setTarget(null);
        human.forgetSoldierTarget();
        human.getNavigation().stop();
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
        if (data != null) {
            if (human.getData() != null) data.updateOrRegisterHumanMob(human);
            else data.humanGunner$removeHuman(human.getUUID());
        }
    }

    public static boolean isForcedHostileTo(Human human, Player player) {
        return human.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)
                && human.getPersistentData().getUUID(FORCED_HOSTILE_TARGET).equals(player.getUUID());
    }

    /** Persists independently of the temporary target so old beacon waves remain identifiable after hiring. */
    static boolean isHostileBeaconOrigin(Human human) {
        persistHostileBeaconOrigin(human);
        return human.getPersistentData().getBoolean(HOSTILE_BEACON_ORIGIN);
    }

    private static void persistHostileBeaconOrigin(Human human) {
        if (human.getPersistentData().hasUUID(FORCED_HOSTILE_TARGET)) {
            human.getPersistentData().putBoolean(HOSTILE_BEACON_ORIGIN, true);
        }
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
        defend(player, attacker, false);
    }

    static void defendAfterDamage(Player player, LivingEntity attacker) {
        defend(player, attacker, true);
    }

    private static void defend(Player player, LivingEntity attacker, boolean damagedOwner) {
        if (!(player.level() instanceof ServerLevel level) || attacker == player) return;
        AABB bounds = player.getBoundingBox().inflate(48.0D);
        for (WeakReference<Human> reference : nearbyHumans(player, level)) {
            Human human = reference.get();
            if (!isNearbyLiveHuman(human, bounds, level) || !protects(human, player)) continue;
            if (!damagedOwner && BodyguardProtection.isBodyguard(human)
                    && SoldierCombatMode.get(human) == SoldierCombatMode.PASSIVE_PROTECTION) continue;
            if (effectiveOwner(human) != null) {
                if (!SoldierCombatMode.authorizeOwnerDefense(human, attacker) || allied(human, attacker)) continue;
            } else if (allied(human, attacker)) {
                continue;
            }
            assignGuardTarget(human, attacker, level.getGameTime(), true);
        }
    }

    static void followOwnerAssault(Player player, LivingEntity target) {
        if (HumanAwareness.passiveCreature(target)
                || !(player.level() instanceof ServerLevel level) || target == player) return;
        if (suppressesBadgeBearerAssault(player, target)) return;
        AABB bounds = player.getBoundingBox().inflate(48.0D);
        for (WeakReference<Human> reference : nearbyHumans(player, level)) {
            Human human = reference.get();
            if (!isNearbyLiveHuman(human, bounds, level) || !isControlledBy(human, player)) continue;
            if (!SoldierCombatMode.authorizeOwnerAssault(human, target)
                    || allied(human, target)) continue;
            assignGuardTarget(human, target, level.getGameTime(), false);
        }
    }

    private static List<WeakReference<Human>> nearbyHumans(Player player, ServerLevel level) {
        AABB bounds = player.getBoundingBox().inflate(48.0D);
        NearbyHumans cached = NEARBY_HUMANS.get(player);
        long tick = level.getGameTime();
        if (cached != null && cached.tick() == tick && cached.dimension().equals(level.dimension())
                && cached.bounds().equals(bounds)) return cached.humans();
        List<WeakReference<Human>> humans = new ArrayList<>();
        for (Human human : level.getEntitiesOfClass(Human.class, bounds)) {
            humans.add(new WeakReference<>(human));
        }
        NEARBY_HUMANS.put(player, new NearbyHumans(tick, level.dimension(), bounds, humans));
        return humans;
    }

    private static boolean isNearbyLiveHuman(Human human, AABB bounds, ServerLevel level) {
        return human != null && !human.isRemoved() && human.isAlive() && human.level() == level
                && human.getBoundingBox().intersects(bounds);
    }

    private static boolean suppressesBadgeBearerAssault(Player player, LivingEntity target) {
        return target instanceof Human wild && HumanRelations.effectiveOwner(wild) == null
                && !HumanRelations.isForcedHostileTo(wild, player)
                && StandingEffects.blocksInitiatedHostility(wild, player);
    }

    private static void assignGuardTarget(Human human, LivingEntity target, long now, boolean defendingOwner) {
        human.getPersistentData().putUUID(GUARD_TARGET, target.getUUID());
        human.getPersistentData().putLong(GUARD_UNTIL, now + 200L);
        HumanPerception.ownerClue(human,target,defendingOwner);
    }

    static void reconcileTarget(Human human) {
        LivingEntity target = human.getTarget();
        if (target != null && (allied(human, target) || target instanceof Human other && !humanHostile(human, other)))
            human.setTarget(null);
    }

    static void tick(Human human) {
        reconcileTarget(human);
        persistHostileBeaconOrigin(human);
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
                double fireRange = GunRangePolicy.forWeapon(human, human.getMainHandItem()).fireRange();
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
        BeaconReportingService.tick(human);
        if (!human.getPersistentData().hasUUID(GUARD_TARGET)
                || human.level().getGameTime() <= human.getPersistentData().getLong(GUARD_UNTIL)) return;
        if (human.getTarget() != null && human.getTarget().getUUID().equals(
                human.getPersistentData().getUUID(GUARD_TARGET))
                && !BodyguardProtection.isBodyguard(human)) human.setTarget(null);
        human.getPersistentData().remove(GUARD_TARGET);
        human.getPersistentData().remove(GUARD_UNTIL);
    }

    private static boolean hasImmediateTacticalThreat(Human human) {
        LivingEntity current = human.getTarget();
        return current != null && current.isAlive()
                && current.level() == human.level()
                && human.distanceToSqr(current) <= 36.0D
                && human.canAttack(current)
                && HumanTargeting.isAutonomousPlayerEnemy(human, current);
    }
}
