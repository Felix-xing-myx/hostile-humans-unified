package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.ai.goal.MeleeAttackGoal;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Ordinary melee authority, with an occasional budgeted attempt to regain bow/crossbow range. */
public final class RangedHybridMeleeGoal extends MeleeAttackGoal {
    private final Human human;
    private final RangedMeleeCycle cycle = new RangedMeleeCycle();
    private UUID sessionTarget;
    private ItemStack sessionWeapon;
    private Path separationRoute;
    private boolean ownsSeparationSearch;
    private Vec3 separationTarget, progressPosition;
    private int nextRouteCheck;

    public RangedHybridMeleeGoal(Human human) {
        super(human, 1.05D, true);
        this.human = human;
    }

    @Override
    public boolean canUse() {
        if (human.isFleeing || RangedTerrainCombat.isTerrainMelee(human)) return false;
        if (RangedWeaponCustody.isActive(human) && human.getMainHandItem().isEmpty()
                && isValidTarget(human.getTarget())) HumanLootManager.equipMeleeFallback(human);
        return RangedWeaponCustody.isActive(human)
                && (human.getMainHandItem().isEmpty()
                || HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem()))
                && isValidTarget(human.getTarget());
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        LivingEntity target = human.getTarget();
        if (target != null && (!target.getUUID().equals(sessionTarget) || sessionWeapon != human.getMainHandItem())) {
            cycle.reset();
            sessionTarget = target.getUUID();
            sessionWeapon = human.getMainHandItem();
            if (human.distanceToSqr(target) <= 16) SoldierDialogue.event(human, "melee_switch");
        }
        // Do not reset the ordinary attack cooldown or discard an effective
        // pursuit route every time a shield reaction returns movement to us.
        RangedStrafeSpeed.clear(human);
        human.clearRangedStrafeMotion();
        human.setAggressive(true);
    }

    @Override
    public void stop() {
        releaseSeparation();
        cycle.interrupt();
        super.stop();
        if (!RangedWeaponCustody.isActive(human)) {
            PursuitNavigation.clear(human);
            cycle.reset();
            sessionTarget = null;
            sessionWeapon = null;
        }
    }

    @Override
    public void tick() {
        if (!canUse()) return;
        LivingEntity target = human.getTarget();
        if (!target.getUUID().equals(sessionTarget) || sessionWeapon != human.getMainHandItem()) {
            releaseSeparation();
            cycle.reset();
            sessionTarget = target.getUUID();
            sessionWeapon = human.getMainHandItem();
        }
        // Share the gunner's timed close-range trial and pressure fallback, but
        // restore a real bow/crossbow through its own two-location custody.
        if (human.distanceToSqr(target) < 36 && CloseRangedTactics.tryBow(human, target)) {
            releaseSeparation();
            return;
        }
        boolean maySeparate = !SoldierOrder.isHoldingPosition(human)
                && !CloseRangedTactics.keepDefense(human)
                && !human.isInWater() && !human.isInLava() && !human.isSeekingShore()
                && !human.isShoreTransitionPending() && !human.onClimbable()
                && (cycle.separating() || (!human.humanGunner$hasPendingMeleeAttack() && !human.swinging))
                && !human.isUsingItem() && !CombatTerrainNavigation.hasWork(human, target);
        boolean progressed = progressPosition == null || human.position().distanceToSqr(progressPosition) >= 0.04D
                || (human.distanceToSqr(target) >= 36 && human.getSensing().hasLineOfSight(target));
        if (progressed) progressPosition = human.position();
        cycle.tick(maySeparate, progressed);

        if (cycle.searching()) {
            // Searching never takes movement away from ordinary melee. The existing
            // firing-lane planner spreads candidate paths over the shared budget.
            Path candidate = RangedFiringPosition.findVisiblePath(human, target, 7, 10, 10, 16);
            ownsSeparationSearch = RangedFiringPosition.hasPendingVisibleSearch(human);
            if (candidate != null && allowedSeparation(target, candidate)
                    && RetreatPathSafety.tryMove(human, candidate, 1.05D)) {
                // Install before releasing old planner metadata: a rejected candidate
                // must never delete the live melee route.
                PursuitNavigation.clear(human);
                separationRoute = human.getNavigation().getPath();
                separationTarget = target.position();
                progressPosition = human.position();
                nextRouteCheck = human.tickCount + 10;
                human.clearRangedStrafeMotion();
                cycle.beginSeparation();
            } else if (!RangedFiringPosition.hasPendingVisibleSearch(human)) {
                cycle.retry();
            }
        }
        if (cycle.separating() && tickSeparation(target)) {
            if (!RangedWeaponCustody.isActive(human)) return;
            human.getLookControl().setLookAt(target, 45, 35);
            MeleeCombatRange.faceTarget(human, target);
            tickAttackOnly(target);
            return;
        }
        if (!cycle.separating() && !cycle.searching()) releaseSeparation();
        // Same pursuit, safe spacing, terrain detours, jumps, oriented hit checks,
        // attack cooldown and Better Combat windup as an ordinary melee fighter.
        super.tick();
    }

    private boolean tickSeparation(LivingEntity target) {
        if (human.distanceToSqr(target) >= 36 && human.getSensing().hasLineOfSight(target)) {
            stopOwnedSeparationRoute();
            // Custody restores the bow only after any already-committed attack ends.
            RangedWeaponCustody.restorePreferred(human, "close_defense_separated");
            return true;
        }
        if (!MovementContinuity.ownsRoute(human, separationRoute) || human.getNavigation().isDone()) {
            cycle.retry();
            return false;
        }
        if (human.tickCount >= nextRouteCheck) {
            nextRouteCheck = human.tickCount + 10;
            if (target.position().distanceToSqr(separationTarget) > 4
                    || !allowedSeparation(target, human.getNavigation().getPath())) {
                cycle.retry();
                return false;
            }
        }
        return true;
    }

    private boolean allowedSeparation(LivingEntity target, Path path) {
        if (!CloseRangedTactics.allowsLocalPath(human, path)) return false;
        if (!RetreatPathSafety.isSafe(human, target, path, true)) return false;
        if (!SoldierOrder.allowsCombatPath(human, path)) return false;
        var node = path.getEndNode();
        BlockPos end = new BlockPos(node.x, node.y, node.z);
        Vec3 feet = Vec3.atBottomCenterOf(end);
        return target.distanceToSqr(feet) >= 36 && SoldierOrder.allowsCombatPosition(human, feet)
                && RangedShotClearance.fromPosition(human, feet, target);
    }

    private void stopOwnedSeparationRoute() {
        if (MovementContinuity.ownsRoute(human, separationRoute)) human.getNavigation().stop();
        separationRoute = null;
    }

    private void releaseSeparation() {
        stopOwnedSeparationRoute();
        if (ownsSeparationSearch) RangedFiringPosition.cancelVisibleSearch(human);
        ownsSeparationSearch = false;
        separationTarget = progressPosition = null;
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && human.canAttack(target);
    }
}
