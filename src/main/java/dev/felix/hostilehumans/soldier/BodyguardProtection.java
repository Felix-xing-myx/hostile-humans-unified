package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CreeperSafetyPolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunRangePolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedCombatProfile;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.perception.HumanPerception;

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
    private static final class State {
        UUID threat, attack;
        int nextScan;
    }
    private BodyguardProtection() { }

    public static ServerPlayer owner(Human human) {
        if (!human.hasOwner() || !(human.level() instanceof ServerLevel level)) return null;
        var player = level.getPlayerByUUID(human.getOwnerUUID());
        return player instanceof ServerPlayer owner && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }
    public static void clear(Human human) { STATES.remove(human); }
    public static boolean isBodyguard(Human human) {
        return human.hasOwner() && SoldierOrder.get(human) == SoldierOrder.BODYGUARD;
    }
    /** Legacy read-only role recommendation; actual target assignment uses HumanTargetCoordinator. */
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
        if (GunSupport.get().isGun(item)) return GunRangePolicy.firingReach(human, item);
        if (RangedWeaponCustody.isBowOrCrossbow(item) || item.getItem() instanceof TridentItem)
            return RangedCombatProfile.of(human, item).maximum();
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
                SoldierCombatMode.get(human) == SoldierCombatMode.ACTIVE, human.distanceToSqr(target), reach * reach,
                OrderDistances.followSquared(human));
    }
    public static LivingEntity threat(Human human) { return resolve(human, STATES.get(human), false); }
    public static LivingEntity attack(Human human) { return resolve(human, STATES.get(human), true); }
    private static LivingEntity resolve(Human human, State state, boolean attack) {
        UUID id = state == null ? null : attack ? state.attack : state.threat;
        if (id == null || !(human.level() instanceof ServerLevel level)) return null;
        return level.getEntity(id) instanceof LivingEntity entity && entity.isAlive() && !entity.isRemoved() ? entity : null;
    }

    public static void update(Human human) {
        ServerPlayer owner = owner(human);
        if (!isBodyguard(human) || owner == null || human.isNoAi() || human.isSleeping()) {
            clear(human);
            return;
        }
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        // At most the bounded known-target table: invalidation/death can re-rank immediately.
        state.nextScan = human.tickCount + 5;
        LivingEntity bestThreat = null, bestAttack = null;
        double threatScore = Double.MAX_VALUE, attackScore = Double.MAX_VALUE;
        boolean active = SoldierCombatMode.get(human) == SoldierCombatMode.ACTIVE;
        double reach = rangedReach(human);
        double searchRadius = active && reach > 0.0D ? Math.max(OrderDistances.follow(human), reach + 12.0D)
                : OrderDistances.follow(human);
        for (LivingEntity candidate : HumanPerception.knownDynamicTargets(human)) {
            if (!HumanPerception.canTarget(human,candidate)) continue;
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

    private static boolean hostileToOwner(Human human, ServerPlayer owner, LivingEntity candidate) {
        if (candidate instanceof Human wild) {
            return HumanRelations.hostileToOwner(wild, owner);
        }
        return candidate instanceof Mob mob && (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER);
    }
}
