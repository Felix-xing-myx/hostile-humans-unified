package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunReloadData;
import com.tacz.guns.resource.pojo.data.gun.GunReloadTime;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.WeakHashMap;

/** Native TaCZ state recovery; loaded only by the installed TaCZ adapter. */
final class TaczGunMechanics {
    private static final Map<PathfinderMob, GunDrawContinuity> DRAW_BINDINGS = new WeakHashMap<>();
    private static final long RELOAD_RETRY_TICKS = 4L;
    private static final String GUN_STATE = "hostile_humans_unified:gun_state";
    private static final String RELOAD_DEADLINE = "hostile_humans_unified:gun_reload_deadline";
    private static final String RELOAD_HARD_DEADLINE = "hostile_humans_unified:gun_reload_hard_deadline";
    private static final String RELOAD_GUN_ID = "hostile_humans_unified:gun_reloading_id";
    private static final String NEXT_RELOAD_ATTEMPT = "hostile_humans_unified:gun_next_reload_attempt";
    private static final String STALL_DEADLINE = "hostile_humans_unified:gun_stall_deadline";
    private static final String BOLT_HARD_DEADLINE = "hostile_humans_unified:gun_bolt_hard_deadline";
    private TaczGunMechanics() {}
    static void handleShootResult(
            PathfinderMob mob, IGunOperator operator, ItemStack weapon, ShootResult result
    ) {
        long gameTime = mob.level().getGameTime();
        mob.getPersistentData().putString(GUN_STATE, result.name().toLowerCase());
        if (result == ShootResult.SUCCESS) {
            mob.getPersistentData().putLong("hostile_humans_unified:last_gunshot_game_time", gameTime);
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
            if (result == ShootResult.NO_AMMO && mob instanceof Human human
                    && (human.isUsingItem() || !HumanAmmo.canReload(human, weapon))) {
                // Eating/blocking and exhausted reserves are ordinary waiting states.
                // Repeated draw recovery would reset the gun without fixing either.
                clearRecoveryTimers(mob);
                return;
            }
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
                    && gameTime >= mob.getPersistentData().getLong(NEXT_RELOAD_ATTEMPT)
                    && (!(mob instanceof Human human) || HumanAmmo.canReload(human, weapon))) {
                boolean wasReloading = operator.getDataHolder().reloadStateType.isReloading();
                operator.reload();
                mob.getPersistentData().putLong(NEXT_RELOAD_ATTEMPT, gameTime + RELOAD_RETRY_TICKS);
                if (!wasReloading && operator.getDataHolder().reloadStateType.isReloading()) {
                    // Waiting without reserves or under draw cooldown must not
                    // spend most of the newly paid cycle's recovery budget.
                    int delay = nativeReloadRecoveryDelay(weapon, gun);
                    deadline = gameTime + delay;
                    mob.getPersistentData().putLong(RELOAD_DEADLINE, deadline);
                    mob.getPersistentData().putLong(RELOAD_HARD_DEADLINE,
                            gameTime + GunReloadPolicy.hardLimitTicks(delay));
                }
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
            drawCurrentGun(mob, operator);
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
            LivingEntity target = mob.getTarget();
            HumanGunner.LOGGER.debug(
                    "TaCZ recovery context npc={} reason={} target={} rounds={} chamber={} usingItem={} reload={} reserves={}",
                    mob.getUUID(), reason, target == null ? "none" : target.getUUID(),
                    gun.getCurrentAmmoCount(weapon), gun.hasBulletInBarrel(weapon), mob.isUsingItem(),
                    operator.getDataHolder().reloadStateType,
                    mob instanceof Human human ? HumanAmmo.magazineCount(human) : -1);
            operator.cancelReload();
            // Recovery only clears stalled operator state. Native bolting must
            // move a real magazine round into the chamber; recovery never adds
            // rounds, even after a paid reload was interrupted or timed out.
            drawCurrentGun(mob, operator);
            mob.setSprinting(false);
            mob.getPersistentData().putInt(
                    "hostile_humans_unified:gun_recovery_count",
                    mob.getPersistentData().getInt("hostile_humans_unified:gun_recovery_count") + 1
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

    static boolean ensureGunDrawn(PathfinderMob mob, IGunOperator operator) {
        ItemStack weapon = mob.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return false;
        var current = operator.getDataHolder().currentGunItem;
        boolean bound = current != null && current.get() == weapon;
        return DRAW_BINDINGS.computeIfAbsent(mob, ignored -> new GunDrawContinuity())
                .ensure(weapon, gun.getGunId(weapon).toString(), bound, () -> drawCurrentGun(mob, operator));
    }

    private static boolean drawCurrentGun(PathfinderMob mob, IGunOperator operator) {
        ItemStack weapon = mob.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return false;
        java.util.function.Supplier<ItemStack> requested = mob::getMainHandItem;
        operator.draw(requested);
        // TaCZ publishes the supplier only after an uncancelled GunDrawEvent.
        if (operator.getDataHolder().currentGunItem != requested) return false;
        DRAW_BINDINGS.computeIfAbsent(mob, ignored -> new GunDrawContinuity())
                .drawn(weapon, gun.getGunId(weapon).toString());
        return true;
    }

    static void prepareUrgentCloseShot(PathfinderMob mob, IGunOperator operator) {
        clearNpcSprintLock(mob, operator);
        operator.getDataHolder().meleeTimestamp = -1L;
        // TaCZ executes a queued gun-butt hit at zero; -1 means no pending melee.
        operator.getDataHolder().meleePrepTickCount = -1;
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

}
