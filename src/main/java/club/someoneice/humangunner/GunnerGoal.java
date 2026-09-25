package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.HumanUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadTime;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class GunnerGoal<T extends PathfinderMob> extends Goal {
    private static final double RETREAT_RANGE = 4.0D;
    private static final double CLOSE_MELEE_ENTER_RANGE = 2.0D;
    private static final double CLOSE_MELEE_EXIT_RANGE = 4.0D;
    private static final int MIN_AIM_TICKS = 2;
    private static final long RELOAD_RETRY_TICKS = 4L;
    private static final double MAX_LEAD_TICKS = 8.0D;
    private static final String GUN_STATE = "humangunner:gun_state";
    private static final String RELOAD_DEADLINE = "humangunner:gun_reload_deadline";
    private static final String RELOAD_HARD_DEADLINE = "humangunner:gun_reload_hard_deadline";
    private static final String RELOAD_GUN_ID = "humangunner:gun_reloading_id";
    private static final String NEXT_RELOAD_ATTEMPT = "humangunner:gun_next_reload_attempt";
    private static final String STALL_DEADLINE = "humangunner:gun_stall_deadline";
    private static final String BOLT_HARD_DEADLINE = "humangunner:gun_bolt_hard_deadline";
    private final T mob;
    private final IGunOperator operator;
    private int aimTicks;
    private int tacticalMoveCooldown;
    private int nextCloseMeleeAttackTick;
    private int nextCloseRetreatPathTick;
    private int nextRangeRetreatPathTick;
    private int retreatFailureTicks;
    private double lastThreatDistance = Double.POSITIVE_INFINITY;
    private boolean retreatingForSpace;
    private boolean approachingTarget;

    public GunnerGoal(T mob) {
        this.mob = mob;
        this.operator = IGunOperator.fromLivingEntity(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
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
        boolean closeDefense = mob instanceof Human human
                && GunCustody.isCloseDefenseActive(human);
        if (target == null || !target.isAlive()
                || (!isGun(mob.getMainHandItem()) && !closeDefense)) {
            return false;
        }
        if (mob instanceof Human human && human.isFleeing) {
            return false;
        }
        double distanceSqr = mob.distanceToSqr(target);
        double fireRangeSqr = firingRangeSqr(preferredRange(mob.getMainHandItem()));
        return distanceSqr <= fireRangeSqr
                || (mob instanceof Human human && target instanceof Player player
                && HumanRelationshipPolicy.shouldApproachBeaconPlayerWithoutFiring(
                        HumanRelations.isForcedHostileTo(human, player), distanceSqr,
                        fireRangeSqr));
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        aimTicks = 0;
        retreatFailureTicks = 0;
        lastThreatDistance = Double.POSITIVE_INFINITY;
        retreatingForSpace = false;
        approachingTarget = false;
        tacticalMoveCooldown = mob.getRandom().nextInt(12, 25);
        if (isGun(mob.getMainHandItem())) {
            operator.draw(mob::getMainHandItem);
        }
    }

    @Override
    public void stop() {
        aimTicks = 0;
        retreatingForSpace = false;
        approachingTarget = false;
        operator.aim(false);
        if (mob instanceof Human human) {
            MovementSpeedController.combat(human, false);
        }
        clearNpcSprintLock(mob, operator);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        ItemStack weapon = mob.getMainHandItem();
        boolean closeDefense = mob instanceof Human human
                && GunCustody.isCloseDefenseActive(human);
        if (target == null || !target.isAlive()
                || (!isGun(weapon) && !closeDefense)) {
            return;
        }

        double distance = mob.distanceTo(target);
        GunRangePolicy.Band range = preferredRange(weapon);
        boolean holdingPosition = mob instanceof Human human
                && SoldierOrder.isHoldingPosition(human);
        if (mob instanceof Human human) {
            if (closeDefense) {
                if (distance < CLOSE_MELEE_EXIT_RANGE) {
                    tickCloseMeleeDefense(human, target, distance);
                    return;
                }
                GunCustody.restorePrimaryGun(human, "close_defense_distance");
                weapon = mob.getMainHandItem();
                aimTicks = 0;
                if (!isGun(weapon)) {
                    return;
                }
                operator.draw(mob::getMainHandItem);
                human.getPersistentData().putString(
                        "humangunner:ai_phase", "gun_close_defense_return"
                );
                range = preferredRange(weapon);
            } else {
                updateRetreatPressure(distance, range);
            }
            if (!closeDefense
                    && distance <= CLOSE_MELEE_ENTER_RANGE
                    && retreatFailureTicks >= 12
                    && GunCustody.beginCloseMeleeCounter(human)) {
                tickCloseMeleeDefense(human, target, distance);
                return;
            }
        }

        if (mob instanceof Human human) {
            // TaCZ refuses every shot while sprintTimeS is positive. Gun users
            // still move at the configured combat attribute, but never retain
            // the vanilla sprint flag while this firing goal is active.
            MovementSpeedController.combat(human, false);
        }

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
            operator.aim(false);
            if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
                mob.getNavigation().moveTo(target, 1.0D);
                approachingTarget = true;
            }
            return;
        }
        if (!mob.getSensing().hasLineOfSight(target)) {
            aimTicks = 0;
            operator.aim(false);
            if (holdingPosition) {
                if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                    mob.getNavigation().stop();
                }
                return;
            }
            moveForFiringPosition(target, distance, range, true);
            return;
        }

        if (holdingPosition) {
            if (!SoldierOrder.isReturningToHoldPosition((Human)mob)) {
                mob.getNavigation().stop();
            }
        } else {
            moveForFiringPosition(target, distance, range, false);
        }

        operator.aim(true);
        if (distance <= RETREAT_RANGE) {
            // Close pressure must not turn the gun into a silent prop. TaCZ
            // retains draw/melee/sprint locks independently from the held
            // ItemStack, so clear only those transient blockers before the
            // urgent counter-shot while continuing to retreat.
            prepareUrgentCloseShot(mob, operator);
        }
        AimRotation aim = calculateAim(target, weapon);
        mob.setXRot(aim.pitch());
        mob.setYRot(aim.yaw());
        mob.setYHeadRot(aim.yaw());
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
        ShootResult result = operator.shoot(shotAim::pitch, shotAim::yaw);
        handleShootResult(mob, operator, weapon, result);
    }

    private void tickCloseMeleeDefense(Human human, LivingEntity target, double distance) {
        operator.aim(false);
        aimTicks = 0;
        clearNpcSprintLock(mob, operator);
        human.getLookControl().setLookAt(target, 45.0F, 35.0F);
        human.getPersistentData().putString(
                "humangunner:ai_phase", "gun_close_melee_defense"
        );

        if (SoldierOrder.isHoldingPosition(human)) {
            if (!SoldierOrder.isReturningToHoldPosition(human)) {
                human.getNavigation().stop();
            }
        } else if (human.tickCount >= nextCloseRetreatPathTick
                || human.getNavigation().isDone()
                || human.getNavigation().isStuck()) {
            retreatFrom(target);
            nextCloseRetreatPathTick = human.tickCount + 8;
        }
        if (distance > CLOSE_MELEE_ENTER_RANGE
                || human.isUsingItem()
                || human.tickCount < nextCloseMeleeAttackTick
                || !(HumanUtil.isMeleeWeapon(human.getMainHandItem())
                || HumanLootManager.isDedicatedMeleeWeapon(human.getMainHandItem()))) {
            return;
        }
        human.swing(InteractionHand.MAIN_HAND);
        human.doHurtTarget(target);
        nextCloseMeleeAttackTick = human.tickCount + MeleeAttackTiming.nextCooldown(human);
    }

    static void handleShootResult(
            PathfinderMob mob, IGunOperator operator, ItemStack weapon, ShootResult result
    ) {
        long gameTime = mob.level().getGameTime();
        mob.getPersistentData().putString(GUN_STATE, result.name().toLowerCase());
        if (result == ShootResult.SUCCESS) {
            mob.getPersistentData().putLong("humangunner:last_gunshot_game_time", gameTime);
            clearRecoveryTimers(mob);
            return;
        }

        if (result == ShootResult.IS_SPRINTING) {
            clearNpcSprintLock(mob, operator);
            if (mob instanceof Human human) {
                MovementSpeedController.combat(human, false);
            }
            return;
        }

        if (result == ShootResult.NO_AMMO || result == ShootResult.IS_RELOADING) {
            operator.aim(false);
            IGun gun = IGun.getIGunOrNull(weapon);
            if (gun == null) return;
            String gunId = gun.getGunId(weapon).toString();
            if (!gunId.equals(mob.getPersistentData().getString(RELOAD_GUN_ID))) {
                clearRecoveryTimers(mob);
                mob.getPersistentData().putString(RELOAD_GUN_ID, gunId);
            }
            long deadline = mob.getPersistentData().getLong(RELOAD_DEADLINE);
            if (deadline <= 0) {
                int delay = nativeReloadRecoveryDelay(weapon, gun);
                deadline = gameTime + delay;
                mob.getPersistentData().putLong(RELOAD_DEADLINE, deadline);
                mob.getPersistentData().putLong(RELOAD_HARD_DEADLINE,
                        gameTime + GunReloadPolicy.hardLimitTicks(delay));
            }
            if (result == ShootResult.NO_AMMO
                    && gameTime >= mob.getPersistentData().getLong(NEXT_RELOAD_ATTEMPT)) {
                operator.reload();
                mob.getPersistentData().putLong(NEXT_RELOAD_ATTEMPT, gameTime + RELOAD_RETRY_TICKS);
            }
            if (gameTime >= deadline) {
                boolean stillReloading = operator.getSynReloadState().getStateType().isReloading();
                if (stillReloading
                        && gameTime < mob.getPersistentData().getLong(RELOAD_HARD_DEADLINE)) {
                    // The game's own reload is progressing: do not replace it
                    // with an instant full magazine at the expected deadline.
                    mob.getPersistentData().putLong(RELOAD_DEADLINE, gameTime + 20L);
                } else {
                    recoverStalledGun(mob, operator, weapon, "reload_timeout");
                }
            }
            return;
        }

        if (result == ShootResult.NEED_BOLT || result == ShootResult.IS_BOLTING) {
            long deadline = mob.getPersistentData().getLong(STALL_DEADLINE);
            if (deadline <= 0) {
                int delay = nativeBoltRecoveryDelay(weapon);
                deadline = gameTime + delay;
                mob.getPersistentData().putLong(STALL_DEADLINE, deadline);
                mob.getPersistentData().putLong(BOLT_HARD_DEADLINE,
                        gameTime + GunReloadPolicy.hardLimitTicks(delay));
            }
            if (result == ShootResult.NEED_BOLT) {
                operator.bolt();
            }
            if (gameTime >= deadline) {
                if (operator.getSynIsBolting()
                        && gameTime < mob.getPersistentData().getLong(BOLT_HARD_DEADLINE)) {
                    mob.getPersistentData().putLong(STALL_DEADLINE, gameTime + 20L);
                } else {
                    recoverStalledGun(mob, operator, weapon, "bolt_timeout");
                }
            }
            return;
        }

        if (result == ShootResult.NOT_DRAW) {
            operator.draw(mob::getMainHandItem);
            return;
        }
        if (result == ShootResult.IS_MELEE) {
            prepareUrgentCloseShot(mob, operator);
            return;
        }
        if (mob.getPersistentData().contains(BOLT_HARD_DEADLINE)) {
            mob.getPersistentData().remove(STALL_DEADLINE);
            mob.getPersistentData().remove(BOLT_HARD_DEADLINE);
        }
        if (result == ShootResult.COOL_DOWN
                || result == ShootResult.IS_DRAWING
                || result == ShootResult.OVERHEATED) {
            return;
        }

        long deadline = mob.getPersistentData().getLong(STALL_DEADLINE);
        if (deadline <= 0) {
            mob.getPersistentData().putLong(STALL_DEADLINE, gameTime + 60L);
        } else if (gameTime >= deadline) {
            recoverStalledGun(mob, operator, weapon, result.name().toLowerCase());
        }
    }

    private static void recoverStalledGun(
            PathfinderMob mob, IGunOperator operator, ItemStack weapon, String reason
    ) {
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) {
            clearRecoveryTimers(mob);
            return;
        }
        TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).ifPresent(index -> {
            operator.cancelReload();
            if (!reason.contains("bolt")) {
                int capacity = Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(
                        weapon, index.getGunData()));
                gun.setCurrentAmmoCount(weapon, capacity);
            }
            gun.setBulletInBarrel(weapon, true);
            operator.draw(mob::getMainHandItem);
            mob.setSprinting(false);
            mob.getPersistentData().putInt(
                    "humangunner:gun_recovery_count",
                    mob.getPersistentData().getInt("humangunner:gun_recovery_count") + 1
            );
            HumanGunner.LOGGER.debug(
                    "Recovered stalled TaCZ gun {} for NPC {} after {}",
                    gun.getGunId(weapon), mob.getUUID(), reason
            );
        });
        clearRecoveryTimers(mob);
    }

    /**
     * TaCZ remembers recent sprint time independently from LivingEntity's
     * sprint flag. Swimming can therefore leave an NPC permanently returning
     * IS_SPRINTING even after setSprinting(false).
     */
    static void clearNpcSprintLock(PathfinderMob mob, IGunOperator operator) {
        mob.setSprinting(false);
        operator.getDataHolder().sprintTimeS = 0.0F;
        operator.getDataHolder().sprintTimestamp = -1L;
    }

    static void prepareUrgentCloseShot(PathfinderMob mob, IGunOperator operator) {
        clearNpcSprintLock(mob, operator);
        operator.getDataHolder().meleeTimestamp = -1L;
        operator.getDataHolder().meleePrepTickCount = 0;
    }

    private static int nativeReloadRecoveryDelay(ItemStack weapon, IGun gun) {
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null) return 100;
        GunReloadData reload = index.getGunData().getReloadData();
        if (reload == null) return 100;
        GunReloadTime feed = reload.getFeed();
        GunReloadTime cooldown = reload.getCooldown();
        double feedSeconds = feed == null ? 0.0D : feed.getEmptyTime();
        double cooldownSeconds = cooldown == null ? 0.0D : cooldown.getEmptyTime();
        int capacity = Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(
                weapon, index.getGunData()));
        int missing = Math.max(1, capacity - gun.getCurrentAmmoCount(weapon));
        return GunReloadPolicy.recoveryDelayTicks(reload.getType() == FeedType.MANUAL,
                missing, feedSeconds, cooldownSeconds);
    }

    private static int nativeBoltRecoveryDelay(ItemStack weapon) {
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return 60;
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null) return 60;
        return GunReloadPolicy.boltRecoveryDelayTicks(
                index.getGunData().getBoltActionTime(), index.getGunData().getBoltFeedTime());
    }

    private static void clearRecoveryTimers(PathfinderMob mob) {
        mob.getPersistentData().remove(RELOAD_DEADLINE);
        mob.getPersistentData().remove(RELOAD_HARD_DEADLINE);
        mob.getPersistentData().remove(RELOAD_GUN_ID);
        mob.getPersistentData().remove(NEXT_RELOAD_ATTEMPT);
        mob.getPersistentData().remove(STALL_DEADLINE);
        mob.getPersistentData().remove(BOLT_HARD_DEADLINE);
    }

    private AimRotation calculateAim(LivingEntity target, ItemStack weapon) {
        Vec3 origin = mob.getEyePosition();
        Vec3 aimPoint = target.getEyePosition();

        IGun gun = IGun.getIGunOrNull(weapon);
        CommonGunIndex index = gun == null
                ? null
                : TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index != null) {
            BulletData bullet = index.getBulletData();
            double blocksPerTick = Math.max(0.1D, bullet.getSpeed() / 20.0D);
            double travelTicks = Mth.clamp(origin.distanceTo(aimPoint) / blocksPerTick, 0.0D, MAX_LEAD_TICKS);
            aimPoint = aimPoint.add(target.getDeltaMovement().scale(travelTicks));
            aimPoint = aimPoint.add(0.0D, 0.5D * bullet.getGravity() * travelTicks * travelTicks, 0.0D);
        }

        Vec3 delta = aimPoint.subtract(origin);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new AimRotation(pitch, yaw);
    }

    private AimRotation applyShotSpread(AimRotation exactAim, ItemStack weapon) {
        double standardDeviation = mob instanceof Human human
                ? RangedAccuracy.gunSpreadDegrees(human, weapon)
                : HumanGunnerConfig.get().gunnerSpreadDegrees();
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

    private void retreatFrom(LivingEntity target) {
        Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, target.position());
        if (away != null) {
            if (mob instanceof Human human) {
                MovementSpeedController.retreat(human, false);
            }
            mob.getNavigation().moveTo(
                    away.x,
                    away.y,
                    away.z,
                    1.0D
            );
        } else {
            mob.getNavigation().stop();
            mob.getMoveControl().strafe(-0.80F, mob.getRandom().nextBoolean() ? 0.25F : -0.25F);
        }
    }

    private void moveForFiringPosition(LivingEntity target, double distance,
                                       GunRangePolicy.Band range, boolean obscured) {
        if (distance < range.minimum()) {
            retreatingForSpace = true;
        } else if (distance >= range.retreatResume()) {
            retreatingForSpace = false;
        }
        if (retreatingForSpace) {
            approachingTarget = false;
            if (mob.tickCount >= nextRangeRetreatPathTick
                    || mob.getNavigation().isDone()
                    || mob.getNavigation().isStuck()) {
                retreatFrom(target);
                nextRangeRetreatPathTick = mob.tickCount + 8;
            }
            return;
        }
        if (distance > range.maximum()) {
            if (!approachingTarget || mob.getNavigation().isDone()
                    || mob.tickCount % 10 == 0) {
                mob.getNavigation().moveTo(target, 1.0D);
                approachingTarget = true;
            }
            return;
        }
        if (approachingTarget) {
            // A direct chase path must not keep running after reaching gun range.
            mob.getNavigation().stop();
            approachingTarget = false;
            tacticalMoveCooldown = 6;
        }
        if (--tacticalMoveCooldown <= 0) {
            strafeAround(target, range);
            tacticalMoveCooldown = (obscured ? 8 : CombatAiConfig.get().tacticalRepositionInterval())
                    + mob.getRandom().nextInt(8);
        }
    }

    private void updateRetreatPressure(double distance, GunRangePolicy.Band range) {
        if (distance >= range.minimum()) {
            retreatFailureTicks = 0;
            lastThreatDistance = distance;
            return;
        }
        boolean notGainingGround = Double.isFinite(lastThreatDistance)
                && distance <= lastThreatDistance + 0.05D;
        boolean pathFailed = mob.getNavigation().isDone() || mob.getNavigation().isStuck();
        if (notGainingGround || pathFailed) {
            retreatFailureTicks = Math.min(40, retreatFailureTicks + 1);
        } else {
            retreatFailureTicks = Math.max(0, retreatFailureTicks - 2);
        }
        lastThreatDistance = distance;
        if (mob instanceof Human human) {
            human.getPersistentData().putInt(
                    "humangunner:gun_retreat_failure_ticks", retreatFailureTicks
            );
        }
    }

    private void strafeAround(LivingEntity target, GunRangePolicy.Band range) {
        Vec3 radial = mob.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (radial.lengthSqr() < 0.01D) {
            radial = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            radial = radial.normalize();
        }
        double side = mob.getRandom().nextBoolean() ? 1.0D : -1.0D;
        Vec3 lateral = new Vec3(-radial.z, 0.0D, radial.x).scale(5.0D * side);
        double distance = mob.distanceTo(target);
        // Orbit within the band; do not let every side-step pull the gunner
        // inward toward a target that is already at a safe firing distance.
        double radialAdjustment = distance < range.minimum()
                ? range.retreatResume() - distance
                : Math.min(0.0D, range.maximum() - distance);
        Vec3 correction = radial.scale(radialAdjustment);
        Vec3 candidate = mob.position().add(lateral).add(correction);
        Path path = mob.getNavigation().createPath(BlockPos.containing(candidate), 0);
        if (path == null) {
            candidate = mob.position().subtract(lateral).add(correction);
            path = mob.getNavigation().createPath(BlockPos.containing(candidate), 0);
        }
        if (path != null) {
            mob.getNavigation().moveTo(path, 1.0D);
        }
    }

    private static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && IGun.getIGunOrNull(stack) != null;
    }

    private GunRangePolicy.Band preferredRange(ItemStack weapon) {
        IGun gun = IGun.getIGunOrNull(weapon);
        CommonGunIndex index = gun == null ? null
                : TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        return GunRangePolicy.forType(index == null ? "" : index.getType());
    }

    private double firingRangeSqr(GunRangePolicy.Band range) {
        // A sniper can use the full configured discovery range, including the
        // beacon's temporary follow-range extension, once it has a target.
        double reach = range.fireRange() > 64.0D
                ? Math.max(range.fireRange(), mob.getAttributeValue(Attributes.FOLLOW_RANGE))
                : range.fireRange();
        return reach * reach;
    }

    private record AimRotation(float pitch, float yaw) {
    }

}
