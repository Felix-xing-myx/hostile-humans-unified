package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Threat selection only; actual damage and weapon control remain in existing goals. */
public final class BodyguardProtection {
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Nearby> NEARBY = new WeakHashMap<>();
    private record Nearby(long tick, net.minecraft.world.phys.Vec3 center, String dimension, double radius, List<UUID> ids) { }
    private static final class State {
        UUID threat, attack;
        int nextScan;
    }
    private BodyguardProtection() { }

    static ServerPlayer owner(Human human) {
        if (!human.hasOwner() || !(human.level() instanceof ServerLevel level)) return null;
        var player = level.getPlayerByUUID(human.getOwnerUUID());
        return player instanceof ServerPlayer owner && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }
    static void clear(Human human) { STATES.remove(human); }
    static boolean isBodyguard(Human human) {
        return human.hasOwner() && SoldierOrder.get(human) == SoldierOrder.BODYGUARD;
    }
    /** All target writers must respect the owner-centered selector. */
    public static LivingEntity preferredAttack(Human human, LivingEntity requested) {
        if (!isBodyguard(human) || owner(human) == null || human.isFleeing) return requested;
        update(human);
        return attack(human);
    }
    /** A live imminent explosion beside the owner is a narrowly scoped rescue exception. */
    public static boolean protectingFromExplosion(Human human, LivingEntity candidate) {
        if (!(candidate instanceof net.minecraft.world.entity.monster.Creeper creeper) || !isBodyguard(human)) return false;
        ServerPlayer owner = owner(human);
        return owner != null
                && creeper.isAlive() && !creeper.isRemoved() && creeper.level() == human.level()
                && CreeperSafetyPolicy.rescue(true, creeper.isIgnited(), creeper.swell,
                        creeper.getSwellDir(), creeper.distanceToSqr(owner), creeper.isPowered());
    }
    static boolean ranged(Human human) {
        var item = human.getMainHandItem();
        return GunSupport.get().isGun(item) || item.getItem() instanceof ProjectileWeaponItem
                || item.getItem() instanceof TridentItem;
    }
    static double rangedReach(Human human) {
        var item = human.getMainHandItem();
        if (GunSupport.get().isGun(item)) return GunRangePolicy.firingReach(
                GunRangePolicy.forType(GunSupport.get().gunType(item)), human.getAttributeValue(Attributes.FOLLOW_RANGE));
        if (item.getItem() instanceof CrossbowItem) return 48.0D;
        if (item.getItem() instanceof ProjectileWeaponItem || item.getItem() instanceof TridentItem) return 36.0D;
        return 0.0D;
    }
    private static boolean recentAttacker(LivingEntity victim, LivingEntity candidate) {
        int elapsed = victim.tickCount - victim.getLastHurtByMobTimestamp();
        return victim.getLastHurtByMob() == candidate && elapsed >= 0 && elapsed <= 200;
    }
    static boolean directed(Human human, ServerPlayer owner, LivingEntity candidate) {
        return recentAttacker(owner, candidate) || recentAttacker(human, candidate)
                || (candidate instanceof Mob mob && (mob.getTarget() == owner || mob.getTarget() == human));
    }
    static boolean allowsTarget(Human human, LivingEntity target) {
        ServerPlayer owner = owner(human);
        double reach = rangedReach(human);
        return owner != null && BodyguardPolicy.attackInRange(human.distanceToSqr(owner),
                target.distanceToSqr(owner), ranged(human), directed(human, owner, target)
                || SoldierCombatMode.isExplicitlyAuthorizedAgainst(human, target),
                SoldierCombatMode.get(human) == SoldierCombatMode.ACTIVE, human.distanceToSqr(target), reach * reach);
    }
    static LivingEntity threat(Human human) { return resolve(human, STATES.get(human), false); }
    static LivingEntity attack(Human human) { return resolve(human, STATES.get(human), true); }
    private static LivingEntity resolve(Human human, State state, boolean attack) {
        UUID id = state == null ? null : attack ? state.attack : state.threat;
        if (id == null || !(human.level() instanceof ServerLevel level)) return null;
        return level.getEntity(id) instanceof LivingEntity entity && entity.isAlive() && !entity.isRemoved() ? entity : null;
    }

    static void update(Human human) {
        ServerPlayer owner = owner(human);
        if (!isBodyguard(human) || owner == null || human.isNoAi() || human.isSleeping()) {
            clear(human);
            return;
        }
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        if (human.tickCount < state.nextScan) return;
        state.nextScan = human.tickCount + 5;
        LivingEntity bestThreat = null, bestAttack = null;
        double threatScore = Double.MAX_VALUE, attackScore = Double.MAX_VALUE;
        boolean active = SoldierCombatMode.get(human) == SoldierCombatMode.ACTIVE;
        double reach = rangedReach(human);
        double searchRadius = active && reach > 0.0D ? Math.max(48.0D, reach + 12.0D) : 48.0D;
        for (UUID id : nearby(owner, searchRadius)) {
            var entity = ((ServerLevel) human.level()).getEntity(id);
            if (!(entity instanceof LivingEntity candidate)) continue;
            if (candidate == owner || candidate == human || !candidate.isAlive() || candidate.isRemoved()
                    || HumanRelations.allied(human, candidate)) continue;
            boolean aimed = directed(human, owner, candidate);
            boolean authorized = SoldierCombatMode.isExplicitlyAuthorizedAgainst(human, candidate);
            boolean hostile = hostileToOwner(human, owner, candidate);
            boolean rescuing = protectingFromExplosion(human, candidate);
            double distance = candidate.distanceToSqr(owner);
            boolean proactiveShot = active && reach > 0.0D && human.distanceToSqr(candidate) <= reach * reach;
            if ((!aimed && !authorized && !hostile) || distance > searchRadius * searchRadius
                    || (distance > BodyguardPolicy.CHASE_SQUARED && !aimed && !authorized && !proactiveShot)
                    || (!aimed && !authorized && !rescuing && !human.getSensing().hasLineOfSight(candidate))) continue;
            double score = BodyguardPolicy.priority(distance, aimed, candidate == human.getTarget());
            if (rescuing) score -= 1000000.0D;
            if (score < threatScore) { bestThreat = candidate; threatScore = score; }
            if (score < attackScore && allowsTarget(human, candidate) && human.canAttack(candidate)) {
                bestAttack = candidate;
                attackScore = score;
            }
        }
        // An unpermitted nearer threat still deserves interception. Do not keep
        // firing at a distant licensed target while passive/authorized guards
        // should be shielding the owner from the closer threat.
        if (bestThreat != bestAttack && threatScore < attackScore) bestAttack = null;
        state.threat = bestThreat == null ? null : bestThreat.getUUID();
        state.attack = bestAttack == null ? null : bestAttack.getUUID();
    }

    private static List<UUID> nearby(ServerPlayer owner, double radius) {
        long now = owner.level().getGameTime();
        String dimension = owner.level().dimension().location().toString();
        Nearby cached = NEARBY.get(owner);
        if (cached != null && now >= cached.tick() && now - cached.tick() < 5
                && cached.radius() >= radius && cached.dimension().equals(dimension)
                && cached.center().distanceToSqr(owner.position()) < 4.0D)
            return cached.ids();
        List<UUID> ids = owner.level().getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().inflate(radius), entity -> entity != owner && entity.isAlive())
                .stream().map(LivingEntity::getUUID).toList();
        NEARBY.put(owner, new Nearby(now, owner.position(), dimension, radius, ids));
        return ids;
    }

    private static boolean hostileToOwner(Human human, ServerPlayer owner, LivingEntity candidate) {
        if (candidate instanceof Human wild) {
            return HumanRelationshipPolicy.activeRecruitMayAttackWild(true, true,
                    HumanRelations.effectiveOwner(wild) == null,
                    IdentityBadgeAccess.highestClearance(owner) >= 0,
                    HumanRelations.isForcedHostileTo(wild, owner));
        }
        return candidate instanceof Mob mob && (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER);
    }
}
