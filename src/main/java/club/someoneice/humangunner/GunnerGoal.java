package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class GunnerGoal<T extends PathfinderMob> extends Goal {
    private static final double RETREAT_RANGE = 4.0D;
    private static final int MIN_AIM_TICKS = 2;
    private final T mob;
    private final GunSupport.Control operator;
    private int aimTicks;
    private final GunMovementExecution<T> movement;

    public GunnerGoal(T mob) {
        this.mob = mob;
        this.movement = new GunMovementExecution<>(mob);
        this.operator = mob instanceof Human human ? GunSupport.get().control(human) : GunSupport.Control.NONE;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return false;
        boolean shoreSeeking = isShoreSeeking();
        LivingEntity target = mob.getTarget();
        if (mob instanceof Human human
                && target instanceof Player player
                && HumanRelations.allied(human, player)) {
            mob.setTarget(null);
            return false;
        }
        if (mob instanceof Human attacker
                && target instanceof Human teammate
                && HumanRelations.allied(attacker, teammate)) {
            mob.setTarget(null);
            return false;
        }
        if(mob instanceof Human human&&!AmmoWeaponSelection.usableGun(human,human.getMainHandItem())) return false;
        if(target==null||!target.isAlive()||!isGun(mob.getMainHandItem())) return false;
        if (mob instanceof Human human && human.isFleeing && !shoreSeeking) {
            return false;
        }
        // Owning pursuit is independent of firing reach. Target selection and
        // SoldierOrder retain authority over whom/how far this mob may chase.
        return target.level() == mob.level() && mob.canAttack(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        aimTicks = 0;
        movement.start();
        if (isGun(mob.getMainHandItem())) operator.ensureDrawn();
    }

    @Override
    public void stop() {
        movement.stop();
        aimTicks = 0;
        setAiming(false);
        if (mob instanceof Human human) GunAttackMovementState.clear(human);
        operator.clearSprintLock();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public EnumSet<Flag> getFlags() {
        if (isShoreSeeking() && mob instanceof Human human && !human.isCombatFiringShore()) {
            return EnumSet.of(Flag.LOOK);
        }
        return EnumSet.of(Flag.MOVE, Flag.LOOK);
    }

    @Override
    public void tick() {
        if (mob instanceof Human human) {
            // PlayerLikeMovementGoal runs at a lower goal priority and reads
            // this short-lived hint to suppress combat hops while aiming.
            GunAttackMovementState.clear(human);
            MovementSpeedController.limit(human,MovementSpeedController.Limit.RANGED,1,false);
        }
        LivingEntity target = mob.getTarget();
        ItemStack weapon = mob.getMainHandItem();
        if (target != null && mob instanceof Human human && !human.canAttack(target)) {
            human.setTarget(null);
            setAiming(false);
            return;
        }
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != mob.level()
                || !isGun(weapon)) {
            aimTicks = 0;
            setAiming(false);
            return;
        }
        if (mob instanceof Human human) {
            human.updateCombatFiringShore(target, 1.0D);
        }
        if (movement.targetChanged(target)) aimTicks = 0;

        double distance = mob.distanceTo(target);
        GunRangePolicy.Band range = preferredRange(weapon);
        boolean holdingPosition = mob instanceof Human human
                && SoldierOrder.isHoldingPosition(human);
        if(mob instanceof Human human) {
            if(human.isUsingItem()&&SpartanEquipmentCompat.isShield(human.getUseItem())) {
                setAiming(false);movement.stopLateralMovement();return;
            }
            if(GunCustody.beginCloseMeleeCounter(human)) { setAiming(false); return; }
        }

        if (mob instanceof Human human) {
            // Clear sprint and TaCZ's independent sprint timer before combat
            // processing. Pursuit branches may re-enable sprint only when
            // they return without aiming or firing.
            operator.clearSprintLock();
            if (!isShoreSeeking()) {
                MovementSpeedController.combat(human, false);
            }
        }

        if (operator.ensureDrawn()) aimTicks = 0;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double fireRangeSqr = firingRangeSqr(range);
        if (mob instanceof Human human
                && target instanceof Player player
                && HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        HumanRelations.isForcedHostileTo(human, player), distance * distance,
                        fireRangeSqr)) {
            // Keep moving toward the marked player after a side fight, but do
            // not turn the beacon's pursuit range into extra weapon reach.
            aimTicks = 0;
            movement.visible();
            setAiming(false);
            if (!ShoreSeekingPolicy.shouldMoveTowardCombatTarget(isShoreSeeking())) {
                // The environmental MOVE lease must remain pointed at dry
                // ground; this special beacon pursuit used to replace it with
                // a path straight toward the player, often deeper into water.
                movement.clearLateralInputPreservingNavigation();
                return;
            }
            movement.stopLateralMovement();
            movement.approachTarget(target, range);
            movement.enablePursuitSprint(human, target);
            return;
        }
        if (!(mob instanceof Human human ? RangedShotClearance.canShoot(human, target)
                : mob.getSensing().hasLineOfSight(target))) {
            movement.occluded();
            aimTicks = 0;
            setAiming(false);
            if (isShoreSeeking()) {
                movement.clearLateralInputPreservingNavigation();
                return;
            }
            if (holdingPosition) {
                if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                    mob.getNavigation().stop();
                }
                return;
            }
            moveForFiringPosition(target, distance, range, true);
            if (mob instanceof Human human) {
                movement.enablePursuitSprint(human, target);
            }
            return;
        }
        movement.visible();

        if (isShoreSeeking()) {
            // LeaveWaterWhenIdleGoal owns navigation until the Human is dry.
            // Keep aiming and shooting below active without replacing its path.
            movement.clearLateralInputPreservingNavigation();
        } else if (holdingPosition) {
            movement.stopLateralMovement();
            if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                mob.getNavigation().stop();
            }
        } else {
            moveForFiringPosition(target, distance, range, false);
        }

        // canUse deliberately retains distant targets so movement can close
        // the gap. Enforce the weapon's hard reach here, before aiming/shooting.
        if (distance * distance > fireRangeSqr) {
            aimTicks = 0;
            setAiming(false);
            if (mob instanceof Human human) movement.enablePursuitSprint(human, target);
            return;
        }
        if(mob instanceof Human human&&!HumanAttention.canAim(human,target)){
            aimTicks=0;setAiming(false);return;
        }
        setAiming(true);
        if (mob instanceof Human human) {
            GunAttackMovementState.markAttackActive(human);
        }
        if (distance <= RETREAT_RANGE) {
            // Close pressure must not turn the gun into a silent prop. TaCZ
            // retains draw/melee/sprint locks independently from the held
            // ItemStack, so clear only those transient blockers before the
            // urgent counter-shot while continuing to retreat.
            operator.prepareUrgentShot();
        }
        AimRotation aim = calculateAim(target, weapon);
        mob.setXRot(aim.pitch());
        mob.setYRot(aim.yaw());
        mob.setYHeadRot(aim.yaw());
        mob.setYBodyRot(aim.yaw());
        if (mob instanceof Human human) {
            human.markRangedFacing(target, aim.yaw());
        }
        // The synchronized aiming-progress value is presentation state, not a
        // server-side ballistic requirement. It can remain at zero while the
        // Human is swimming, which used to suppress every underwater shot.
        if (++aimTicks < MIN_AIM_TICKS) {
            return;
        }

        // TaCZ expects pitch followed by the shooter's head yaw. Using the
        // body's Y rotation makes bullets leave at a different angle from the
        // direction selected by LookControl whenever the head is tracking a
        // target independently. Supply the already calculated ballistic aim so
        // the projectile, animation and AI target all agree.
        AimRotation shotAim = applyShotSpread(aim, weapon);
        operator.shoot(weapon, target, shotAim.pitch(), shotAim.yaw());
    }

    private void moveForFiringPosition(LivingEntity target, double distance, GunRangePolicy.Band range, boolean obscured) {
        var plan = mob instanceof Human human
                ? HumanCombatDirector.gunPositioning(human, target, distance, range, obscured)
                : GunPositioning.choose(distance, Math.sqrt(firingRangeSqr(range)), range.maximum(), range.maximum(), false, obscured);
        movement.moveForFiringPosition(target, distance, range, plan);
    }

    private void setAiming(boolean aiming) {
        operator.aim(aiming);
    }

    private AimRotation calculateAim(LivingEntity target, ItemStack weapon) {
        Vec3 origin = RangedShotClearance.gunOrigin(mob).add(GunSupport.get().shotOriginOffset(weapon, mob));
        Vec3 aimPoint = RangedShotClearance.gunAim(origin, weapon, target);

        Vec3 delta = aimPoint.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new AimRotation(pitch, yaw);
    }

    private AimRotation applyShotSpread(AimRotation exactAim, ItemStack weapon) {
        if (GunSupport.get().ownsShotSpread(weapon)) return exactAim;
        double standardDeviation = mob instanceof Human human
                ? RangedAccuracy.gunSpreadDegrees(human, weapon)*GunCombatProfile.spread(human,weapon)
                : 0.6D;
        if (standardDeviation <= 0.0D) {
            return exactAim;
        }

        // Add independent per-shot angular error around the corrected ballistic
        // aim. A 2.5-sigma cap keeps the occasional Gaussian outlier from
        // producing an implausibly wild shot while distance still naturally
        // makes the same angular error less forgiving.
        double limit = standardDeviation * 2.5D;
        float pitchOffset = (float) Mth.clamp(
                mob.getRandom().nextGaussian() * standardDeviation,
                -limit,
                limit
        );
        float yawOffset = (float) Mth.clamp(
                mob.getRandom().nextGaussian() * standardDeviation,
                -limit,
                limit
        );
        return new AimRotation(exactAim.pitch() + pitchOffset, exactAim.yaw() + yawOffset);
    }

    private boolean isShoreSeeking() {
        return mob instanceof Human human
                && ShoreSeekingPolicy.isShoreTransitionActive(
                        human.isSeekingShore(), human.isShoreTransitionPending());
    }

    private static boolean isGun(ItemStack stack) {
        return GunSupport.get().isGun(stack) && HumanGunAcceptance.accepts(stack);
    }

    private GunRangePolicy.Band preferredRange(ItemStack weapon) {
        return mob instanceof Human human?GunRangePolicy.forWeapon(human,weapon):GunRangePolicy.forWeapon(weapon);
    }

    private double firingRangeSqr(GunRangePolicy.Band range) {
        // A sniper can use the full configured discovery range, including the
        // beacon's temporary follow-range extension, once it has a target.
        double reach = GunRangePolicy.firingReach(range, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        return reach * reach;
    }

    private record AimRotation(float pitch, float yaw) {
    }

}
