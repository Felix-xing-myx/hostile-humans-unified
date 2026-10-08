package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

/** MOVE/LOOK arbitration for interception; no attacks or inventory swaps here. */
final class BodyguardMovementGoal extends Goal {
    private final Human human;
    private int nextPlan, blockUntil, nextBlock;
    private ItemStack ownedShield;
    private net.minecraft.world.level.pathfinder.Path ownedPath;
    private boolean followFallback;
    BodyguardMovementGoal(Human human) {
        this.human = human;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    @Override public boolean canUse() {
        if (!BodyguardProtection.isBodyguard(human) || human.isFleeing || human.isInLava()
                || human.isSeekingShore() || human.isNoAi() || human.isSleeping() || human.isPassenger()
                || HumanGunner.isManualInventoryOpen(human) || RecoverySupplies.hasHandCustody(human)
                || (human.isUsingItem() && !SpartanEquipmentCompat.isShield(human.getUseItem())
                && !GunSupport.get().isGun(human.getUseItem())
                && !(human.getUseItem().getItem() instanceof ProjectileWeaponItem)
                && !(human.getUseItem().getItem() instanceof TridentItem))) return false;
        ServerPlayer owner = BodyguardProtection.owner(human);
        if (owner == null || SoldierOrder.isReturningFromRetreat(human)) return false;
        var target = human.getTarget();
        // Close combat belongs to native weapon goals. Distant retaliation is
        // allowed only after taking a position beside the owner, not by chasing.
        return target == null || BodyguardPolicy.shouldReturn(human.distanceToSqr(owner))
                || (target.distanceToSqr(owner) > BodyguardPolicy.CHASE_SQUARED
                && BodyguardPolicy.needsRangedPosition(
                square(human.getX() - owner.getX()) + square(human.getZ() - owner.getZ()), human.getY() - owner.getY()));
    }
    private static double square(double value) { return value * value; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { nextPlan = human.tickCount; }
    private Vec3 destination(ServerPlayer owner) {
        var threat = BodyguardProtection.threat(human);
        Vec3 direction = threat == null ? owner.getLookAngle().scale(-1.0D) : threat.position().subtract(owner.position());
        direction = new Vec3(direction.x, 0.0D, direction.z);
        double spacing = threat == null ? 2.5D : BodyguardPolicy.interceptSpacing(direction.length());
        if (direction.lengthSqr() < 1.0E-6D) direction = new Vec3(1, 0, 0);
        direction = direction.normalize();
        // Several protectors occupy neighboring points rather than one stack.
        double side = (Math.floorMod(human.getId(), 3) - 1) * 0.65D;
        return owner.position().add(direction.scale(spacing)).add(-direction.z * side, 0, direction.x * side);
    }
    @Override public void tick() {
        ServerPlayer owner = BodyguardProtection.owner(human);
        if (owner == null) return;
        if (SoldierFollowNavigation.rescueIfStuck(human, owner, 4.0)) return;
        Vec3 goal = destination(owner);
        boolean returning = human.distanceToSqr(owner) > 36.0D;
        MovementSpeedController.combat(human, returning);
        double speed = UnifiedConfig.get().followSpeed(TierAttributes.key(human), returning);
        human.getNavigation().setSpeedModifier(speed);
        if (human.tickCount >= nextPlan) {
            nextPlan = human.tickCount + 5;
            if (human.position().distanceToSqr(goal) > 1.0D) {
                if (followFallback) {
                    SoldierFollowNavigation.tick(human, owner);
                    followFallback = false;
                } else if (BasicNavigationBudget.tryAcquire(human, 3)) {
                    var old = human.getNavigation().getPath();
                    boolean accepted = false;
                    for (int attempt = 0; attempt < 3 && !accepted; attempt++) {
                        Vec3 point = SafePositions.nearOwner(human, owner,
                                Mth.floor(goal.x) + (attempt == 1 ? 1 : attempt == 2 ? -1 : 0), Mth.floor(goal.z));
                        if (point == null) continue;
                        var path = human.getNavigation().createPath(BlockPos.containing(point), 0);
                        if (path != null && path.canReach() && human.getNavigation().moveTo(path, speed)) {
                            ownedPath = path;
                            accepted = true;
                        }
                    }
                    if (!accepted) {
                        if (old != null && !old.isDone()) human.getNavigation().moveTo(old, speed);
                        followFallback = true;
                        nextPlan = human.tickCount + 1;
                    }
                    BasicNavigationBudget.cancel(human);
                } else nextPlan = human.tickCount + 1;
            } else {
                if (MovementContinuity.ownsRoute(human, ownedPath)) human.getNavigation().stop();
                SoldierFollowNavigation.stopOwned(human);
                MovementSpeedController.normal(human);
            }
        }
        var threat = BodyguardProtection.threat(human);
        if (threat != null) {
            human.getLookControl().setLookAt(threat, 75.0F, 35.0F);
            // Passive interception blocks without assigning an attack target.
            // Active firing resumes once this positioning goal yields MOVE.
            if (ownedShield != null && human.tickCount >= blockUntil) releaseShield();
            if (human.getTarget() == null && human.shieldCoolDown <= 0 && !human.isUsingItem()
                    && human.tickCount >= nextBlock && human.distanceToSqr(owner) <= 16.0D) {
                for (InteractionHand hand : new InteractionHand[] {InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND}) {
                    if (!SpartanEquipmentCompat.isShield(human.getItemInHand(hand))) continue;
                    ownedShield = human.getItemInHand(hand);
                    human.humanGunner$startProjectileShield(hand);
                    blockUntil = human.tickCount + 8;
                    nextBlock = human.tickCount + 16;
                    break;
                }
            }
            if (ownedShield != null) human.markShieldFacing(threat);
        } else releaseShield();
    }
    private void releaseShield() {
        if (ownedShield != null && human.isUsingItem() && human.getUseItem() == ownedShield) human.stopUsingItem();
        ownedShield = null;
    }
    @Override public void stop() {
        releaseShield();
        SoldierFollowNavigation.stopOwned(human);
        if (MovementContinuity.ownsRoute(human, ownedPath)) human.getNavigation().stop();
        ownedPath = null;
        followFallback = false;
        BasicNavigationBudget.cancel(human);
    }
}
