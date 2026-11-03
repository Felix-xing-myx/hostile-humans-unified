package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import dev.felix.hostilehumans.behavior.combat.gun.GunAttackAction;
import dev.felix.hostilehumans.behavior.combat.gun.GunFireAuthorization;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.combat.MonitorCombatSession;
import dev.felix.hostilehumans.monitor.combat.MonitorGunActions;
import net.minecraft.world.item.ItemStack;

/** Native NPC operator only; uses paid reloads and accepted server shot results, never a Player surrogate. */
public final class TaczMonitorWeapons implements MonitorGunActions {
    private final Human human;
    private final MonitorCombatSession session;
    private final IGunOperator operator;
    private final ItemStack weapon;
    private final dev.felix.hostilehumans.monitor.combat.MonitorGunTrigger trigger = new dev.felix.hostilehumans.monitor.combat.MonitorGunTrigger();
    private boolean previousReload;
    private long reloadUntil = -1;
    public TaczMonitorWeapons(Human human, MonitorCombatSession session) {
        this.human = human; this.session = session; this.weapon = human.getMainHandItem();
        operator = IGunOperator.fromLivingEntity(human);
    }
    public static void cycleFireMode(Human human) {
        var operator = IGunOperator.fromLivingEntity(human);
        var data = operator.getDataHolder();
        if (data.currentGunItem != null && data.currentGunItem.get() == human.getMainHandItem()
                && !TaczHumanReloadTransactions.isPaidReloading(human, data)) operator.fireSelect();
    }

    @Override public void tick(boolean attack, boolean aim, boolean reload) {
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null || weapon != human.getMainHandItem()) { cancel(); return; }
        boolean triggerReady = trigger.held(attack);
        long now = human.level().getGameTime();
        if (reload && !previousReload) reloadUntil = now + 100;
        previousReload = reload;
        boolean drawing = TaczGunMechanics.ensureGunDrawn(human, operator);
        operator.aim(aim);
        if (attack || aim) TaczGunMechanics.clearNpcSprintLock(human, operator);
        if (reloadUntil >= now) {
            if (TaczHumanReloadTransactions.isPaidReloading(human, operator.getDataHolder())) reloadUntil = -1;
            else if (!HumanAmmo.canReload(human, weapon)) reloadUntil = -1;
            else if (!drawing && operator.getSynDrawCoolDown() <= 0) {
                session.reloadDispatch(operator::reload);
                // One explicit attempt after draw completes: full magazines/cancelled hooks never loop.
                reloadUntil = -1;
            }
        }
        if (!triggerReady || drawing || !GunFireAuthorization.allows(human, weapon, null)) return;
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null) return;
        TaczNpcMagazine.normalize(weapon, index);
        GunAttackAction.run(human, () -> {
            if (!GunFireAuthorization.allows(human, weapon, null)) return;
            // The two-supplier API exists in both supported releases and owns timestamps/native cooldowns.
            double tierSpread = dev.felix.hostilehumans.progression.TierAttributes.of(human)
                    .gunSpreadDegrees(dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().gunType(weapon));
            double accuracy = dev.felix.hostilehumans.progression.HumanGrowth.gunSpreadMultiplier(human)
                    * dev.felix.hostilehumans.behavior.combat.gun.GunCombatProfile.spread(human, weapon);
            double deviation = TaczMonitorSpread.standardDeviation(operator.getSynAimingProgress(), tierSpread, accuracy);
            var error = TaczMonitorSpread.sample(deviation, human.getRandom()::nextGaussian);
            ShootResult result = operator.shoot(
                    () -> net.minecraft.util.Mth.clamp(human.getXRot() + error.pitch(), -90, 90),
                    () -> human.getYRot() + error.yaw());
            if (result == ShootResult.NEED_BOLT) operator.bolt();
            if (result == ShootResult.SUCCESS) {
                FireMode mode = gun.getFireMode(weapon);
                boolean continuous = mode == FireMode.AUTO || mode == FireMode.BURST
                        && index.getGunData().getBurstData() != null && index.getGunData().getBurstData().isContinuousShoot();
                trigger.accepted(continuous);
            }
        });
    }
    @Override public void cancel() {
        operator.aim(false);
        if (TaczHumanReloadTransactions.isPaidReloading(human, operator.getDataHolder())) {
            TaczHumanReloadTransactions.end(operator.getDataHolder());
            TaczHumanReloadTransactions.restoreHeldBinding(human, operator.getDataHolder());
        }
        trigger.reset(); previousReload = false; reloadUntil = -1;
    }
}
