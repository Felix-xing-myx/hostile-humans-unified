package dev.felix.hostilehumans.compat.scguns;

import dev.felix.hostilehumans.behavior.combat.gun.GunAttackAction;
import dev.felix.hostilehumans.behavior.combat.gun.GunFireAuthorization;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.combat.MonitorCombatSession;
import dev.felix.hostilehumans.monitor.combat.MonitorGunActions;
import dev.felix.hostilehumans.monitor.combat.MonitorGunTrigger;
import net.minecraft.world.item.ItemStack;

/** Direction-only input enters the same paid reload and native NPC shot executor as autonomous fire. */
public final class ScgunsMonitorWeapons implements MonitorGunActions {
    private final Human human;
    private final MonitorCombatSession session;
    private final ItemStack weapon;
    private final ScgunsIntegration backend = new ScgunsIntegration();
    private final GunSupport.Control control;
    private final MonitorGunTrigger trigger = new MonitorGunTrigger();
    private boolean previousReload;
    private long windupUntil = -1;
    private boolean pulseHeld;

    @Override public boolean fireIntent(boolean attack) {
        if (weapon != human.getMainHandItem() || !backend.isGun(weapon)) return false;
        if (!backend.releaseTrigger(weapon)) return attack;
        return !attack && pulseHeld && !human.isUsingItem() && !backend.isReloading(human, weapon)
                && backend.hasLoadedAmmo(weapon) && backend.nextShotAt(human) <= human.level().getGameTime();
    }

    public ScgunsMonitorWeapons(Human human, MonitorCombatSession session) {
        this.human = human; this.session = session; weapon = human.getMainHandItem();
        control = backend.control(human);
    }

    @Override public void tick(boolean attack, boolean aim, boolean reload) {
        if (weapon != human.getMainHandItem() || !backend.isGun(weapon)) { cancel(); return; }
        backend.tickMonitorReload(human, weapon);
        if (reload && !previousReload) session.reloadDispatch(() -> backend.beginReload(human, weapon));
        previousReload = reload;
        if (backend.releaseTrigger(weapon)) {
            boolean releaseShot = fireIntent(attack);
            pulseHeld = attack && !human.isUsingItem() && !backend.isReloading(human, weapon) && backend.hasLoadedAmmo(weapon);
            control.aim(attack);
            if (releaseShot && GunFireAuthorization.allows(human, weapon, null))
                GunAttackAction.run(human, () -> control.shoot(weapon, null, human.getXRot(), human.getYRot()));
            return;
        }
        control.aim(aim || attack);
        boolean ready = trigger.held(attack);
        long now = human.level().getGameTime();
        if (!attack || human.isUsingItem() || backend.isReloading(human, weapon) || !backend.hasLoadedAmmo(weapon)) windupUntil = -1;
        if (human.isUsingItem() || backend.isReloading(human, weapon) || !backend.hasLoadedAmmo(weapon)) return;
        if (!ready || !GunFireAuthorization.allows(human, weapon, null)) return;
        if (windupUntil < 0) windupUntil = now + backend.monitorWindup(weapon);
        if (now < windupUntil) return;
        long before = backend.nextShotAt(human);
        GunAttackAction.run(human, () -> control.shoot(weapon, null, human.getXRot(), human.getYRot()));
        if (backend.nextShotAt(human) != before) {
            trigger.accepted(backend.continuousTrigger(weapon));
            // Native automatic fire warms up once per hold; each subsequent shot only pays its native rate.
            if (!backend.continuousTrigger(weapon)) windupUntil = -1;
        }
    }

    @Override public void cancel() {
        control.aim(false);
        backend.cancelReload(human, weapon);
        trigger.reset(); previousReload = false; windupUntil = -1; pulseHeld = false;
    }
}
