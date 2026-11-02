package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.behavior.combat.gun.GunRangePolicy;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.defense.ShieldDefensePolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunCombatProfile;
import dev.felix.hostilehumans.behavior.combat.gun.GunPositioning;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.melee.MeleeCombatRange;
import dev.felix.hostilehumans.behavior.combat.ranged.AmmoWeaponSelection;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedStrafeSpeed;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.equipment.EquipmentArchetypes;
import dev.felix.hostilehumans.equipment.FactionEquipmentConfig;
import dev.felix.hostilehumans.inventory.HumanLootManager;
import dev.felix.hostilehumans.navigation.MovementSpeedController;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.PerceptionBudget;
import dev.felix.hostilehumans.soldier.SoldierOrder;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/** Combat-only coordination seam: shared facts and admitted policies, no path or projectile execution. */
public final class HumanCombatDirector {
    private static final class State {
        int nextProjectiles, lastProjectiles = Integer.MIN_VALUE;
        List<Projectile> projectiles = List.of();
    }
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private HumanCombatDirector() { }
    /** Shared strategy selection is read-only and follows live faction configuration, never a profession cache. */
    public static CombatStrategySet strategies(Human human) {
        return CombatStrategySet.forFaction(FactionEquipmentConfig.key(human));
    }

    /** One ammo-aware movement intent for gun, bow/crossbow custody and hybrid attacks. */
    public static boolean keepsRangedDistance(Human human, net.minecraft.world.item.ItemStack weapon) {
        if (!strategies(human).keepsRangedDistance()) return false;
        if (GunSupport.isGunItem(weapon)) return AmmoWeaponSelection.usableGun(human, weapon);
        if (RangedWeaponCustody.isBowOrCrossbow(weapon)) return HumanAmmo.hasArrows(human);
        return weapon.getItem() instanceof net.minecraft.world.item.TridentItem;
    }

    public static boolean prefersRangedSeparation(Human human) {
        var identity = EquipmentArchetypes.describe(human, RangedWeaponCustody.preferredForClearance(human));
        var style = strategies(human).ranged(identity.core(), identity.enabled());
        return style != CombatStrategySet.RangedStyle.VOLLEY && style != CombatStrategySet.RangedStyle.ADVANCE;
    }

    /** Attack facts plus selected doctrine produce an intent; executors keep route and safety ownership. */
    public static GunPositioning.Plan gunPositioning(Human human, LivingEntity target, double distance,
                                               GunRangePolicy.Band range, boolean obscured) {
        boolean advance = GunCombatProfile.of(human, human.getMainHandItem()).advance();
        return GunPositioning.choose(distance, range.fireRange(), range.maximum(),
                advance ? MeleeCombatRange.reach(human, target) : range.maximum(), advance, obscured);
    }

    /** Select once from attack facts and current command/environment; movement keeps execution and safety. */
    public static dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision rangedPositioning(
            net.minecraft.world.entity.PathfinderMob mob, double distanceSqr, boolean visible,
            double firingRangeSqr, double minimumBand, double maximumBand, boolean advance, int seenTicks, boolean crossbow) {
        boolean human = mob instanceof Human;
        boolean holding = mob instanceof Human actor && SoldierOrder.isHoldingPosition(actor);
        boolean waterMovement = mob instanceof Human actor && actor.shouldUseWaterMovement();
        var facts = new dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision.Facts(distanceSqr, firingRangeSqr,
                minimumBand, maximumBand, visible, advance, holding, human, mob.isInWater(), waterMovement, seenTicks);
        return dev.felix.hostilehumans.behavior.combat.ranged.RangedMovementDecision.choose(facts, crossbow);
    }

    public static void tick(Human human) {
        if (human.level().isClientSide) return;
        var shields = human.hhu$shieldDefense();
        shields.meleeReentryCooldownTicks(ShieldDefensePolicy.MELEE_REENTRY_TICKS);
        shields.validateDefenseLease();
        if (HumanTargetCoordinator.suspended(human) || !human.isAlive() || human.isRemoved()) {
            clear(human); return;
        }
        State state = STATES.computeIfAbsent(human, ignored -> new State());
        LivingEntity target = human.getTarget();
        if (!HumanPerception.canTarget(human, target) || human.isFleeing
                || GunSupport.isGunItem(human.getMainHandItem())
                || !(SpartanEquipmentCompat.isShield(human.getMainHandItem())
                    || SpartanEquipmentCompat.isShield(human.getOffhandItem())))
            shields.clearAttackObligation();
        // Existing recognized-memory facts are shared; defense cannot grant new offensive knowledge.
        List<LivingEntity> known = strategies(human).allowsOrdinaryShield() ? HumanPerception.knownDynamicTargets(human).stream()
                .filter(entity -> HumanPerception.canTarget(human, entity)).limit(64).toList() : List.of();
        List<LivingEntity> near = known.stream().filter(entity -> {
            var offset = entity.position().subtract(human.position());
            return offset.horizontalDistanceSqr() <= 16 && Math.abs(offset.y) <= 3;
        }).toList();
        var heldShield = HumanLootManager.bestOwnedShield(human);
        boolean shield = !heldShield.isEmpty();
        if (shield && strategies(human).allowsOrdinaryShield() && !GunSupport.isGunItem(human.getMainHandItem()) && human.shieldCoolDown <= 0
                && !RecoverySupplies.hasActiveUse(human)
                && human.tickCount >= state.nextProjectiles
                && PerceptionBudget.claim(human, "queries", 1, ":defense_projectiles")) {
            state.projectiles = human.level().getEntitiesOfClass(Projectile.class,
                    human.getBoundingBox().inflate(32,16,32), projectile -> projectile.isAlive()
                            && projectile.getOwner() != human).stream()
                    .sorted(java.util.Comparator.comparingDouble(human::distanceToSqr)).limit(32).toList();
            state.lastProjectiles = human.tickCount;
            state.nextProjectiles = human.tickCount + 3;
        }
        List<Projectile> projectiles = state.lastProjectiles != Integer.MIN_VALUE
                && human.tickCount - state.lastProjectiles <= 6 ? state.projectiles : List.of();
        shields.observeDefenseSnapshot(near, projectiles);
        var capability = EquipmentArchetypes.describe(human, heldShield);
        boolean advance = shield && strategies(human).shieldAdvance(capability.core(), capability.enabled())
                && human.onGround()
                && HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem())
                && HumanPerception.canTarget(human,target)
                && human.distanceToSqr(target) > MeleeCombatRange.reachSqr(human,target)
                && !SoldierOrder.isHoldingPosition(human) && !human.isFleeing;
        shields.tickAdvance(advance, 1);
    }
    public static void afterTick(Human human) {
        if (human.level().isClientSide) return;
        var shield = human.hhu$shieldDefense();
        MovementSpeedController.limit(human, MovementSpeedController.Limit.DEFENSE,
                shield.defenseSpeedFactor(), shield.defenseOwnsFacing());
        if (shield.defenseOwnsFacing()) RangedStrafeSpeed.clear(human);
    }
    public static void clear(Human human) {
        STATES.remove(human);
        human.hhu$shieldDefense().cancelDefenseSession();
        MovementSpeedController.clearLimits(human);
    }
}
