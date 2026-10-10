package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.goal.MeleeAttackGoal;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Owns non-fleeing food spacing; counterattacks reuse native melee cooldown and upswing. */
final class CombatEatingMovementGoal extends MeleeAttackGoal {
    private final Human human;
    private int nextPathTick;
    private int nextJumpTick;
    private Path route;
    private Vec3 previousPosition;

    CombatEatingMovementGoal(Human human) {
        super(human, 1.05D, true);
        this.human = human;
    }

    @Override public boolean canUse() {
        LivingEntity target = human.getTarget();
        return CombatAiConfig.forHuman(human).enabled() && RecoverySupplies.isEatingFood(human)
                && !human.isFleeing && target != null && target.isAlive() && human.canAttack(target)
                && !human.isInWater() && !human.isInLava() && !human.isSeekingShore()
                && !human.isShoreTransitionPending();
    }

    @Override public boolean canContinueToUse() { return canUse(); }

    @Override public void start() {
        route = null;
        previousPosition = human.position();
        nextPathTick = human.tickCount;
        human.getNavigation().stop();
        human.setAggressive(true);
    }

    @Override public void stop() {
        if (route != null && human.getNavigation().getPath() == route) human.getNavigation().stop();
        route = null;
        super.stop();
    }

    @Override public void tick() {
        LivingEntity target = human.getTarget();
        if (target == null || !canUse()) return;
        MeleeCombatRange.faceTarget(human, target);
        // Only the main hand changes; the inventory-backed offhand meal keeps its custody.
        if (human.distanceToSqr(target) <= 36) {
            if (GunSupport.isGunItem(human.getMainHandItem())) GunCustody.beginCloseMeleeCounter(human);
            else if (RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem()))
                RangedWeaponCustody.beginCloseMelee(human);
        }
        if (human.getMainHandItem().isEmpty() || HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem()))
            tickAttackOnly(target);

        if (SoldierOrder.isHoldingPosition(human) || human.onClimbable() || human.isPassenger()) return;
        if (human.tickCount >= nextPathTick && human.distanceToSqr(target) < 64) {
            nextPathTick = human.tickCount + 12;
            boolean needsRoute = route == null || route.isDone() || human.getNavigation().isStuck()
                    || !RetreatPathSafety.isSafe(human, target, route, true);
            if (needsRoute && route != null) {
                if (human.getNavigation().getPath() == route) human.getNavigation().stop();
                route = null;
            }
            if (needsRoute && BasicNavigationBudget.tryAcquire(human, 1)) {
                Vec3 away = DefaultRandomPos.getPosAway(human, 8, 3, target.position());
                if (away != null && SoldierOrder.allowsCombatPosition(human, away)) {
                    Path candidate = human.getNavigation().createPath(away.x, away.y, away.z, 0);
                    if (RetreatPathSafety.isSafe(human, target, candidate, true)
                            && RetreatPathSafety.tryMove(human, candidate, 1.05D))
                        route = human.getNavigation().getPath();
                }
            }
        }
        boolean moving = route != null && human.getNavigation().getPath() == route && !route.isDone();
        MovementSpeedController.combat(human, moving);
        boolean retreatHop = retreatHopReady(target, moving);
        previousPosition = human.position();
        if (retreatHop && human.tickCount >= nextJumpTick) {
            CombatAiConfig config = CombatAiConfig.forHuman(human);
            nextJumpTick = human.tickCount + config.combatJumpIntervalMin()
                    + human.getRandom().nextInt(Math.max(1,
                    config.combatJumpIntervalMax() - config.combatJumpIntervalMin() + 1));
            if (human.getRandom().nextDouble() < config.combatJumpChance()
                    && HumanTerrainSafety.safeCombatJump(human)) human.getJumpControl().jump();
        }
    }
    /** A queued route alone is not movement: require actual progress away from the opponent. */
    private boolean retreatHopReady(LivingEntity target, boolean moving) {
        if (!moving || !human.isSprinting() || !human.onGround() || human.horizontalCollision
                || human.swinging || human.humanGunner$hasPendingMeleeAttack()
                || MeleeCombatRange.canStrike(human, target) || previousPosition == null) return false;
        Vec3 progress = human.position().subtract(previousPosition).multiply(1, 0, 1);
        Vec3 away = NavigationSupport.horizontalDirection(target.position(), human.position());
        Vec3 pathDirection = NavigationSupport.horizontalDirection(human.position(),
                Vec3.atBottomCenterOf(route.getNextNodePos()));
        return progress.lengthSqr() > 0.0049D && away.lengthSqr() > 0.01D
                && progress.normalize().dot(away) >= 0.75D
                && pathDirection.dot(away) >= 0.80D;
    }
}
