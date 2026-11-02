package dev.felix.hostilehumans.compat.jeg;

import dev.felix.hostilehumans.behavior.combat.gun.GunAttackAction;
import dev.felix.hostilehumans.behavior.combat.gun.GunFireAuthorization;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.combat.MonitorCombatSession;
import dev.felix.hostilehumans.monitor.combat.MonitorGunActions;
import dev.felix.hostilehumans.monitor.combat.MonitorGunTrigger;
import net.minecraft.world.item.ItemStack;

/** No target selection or player surrogate: native projectile direction comes from the bound Human. */
public final class JegMonitorWeapons implements MonitorGunActions {
    private final Human human;
    private final MonitorCombatSession session;
    private final ItemStack weapon;
    private final JegIntegration backend = new JegIntegration();
    private final GunSupport.Control control;
    private final MonitorGunTrigger trigger = new MonitorGunTrigger();
    private boolean previousReload;
    private int heldTicks;

    @Override public boolean fireIntent(boolean attack) {
        if (weapon != human.getMainHandItem() || !backend.isGun(weapon)) return false;
        if (!backend.releaseTrigger(weapon)) return attack;
        return !attack && heldTicks > 5 && !human.isUsingItem() && !backend.isReloading(human, weapon)
                && backend.hasLoadedAmmo(weapon) && backend.nextShotAt(human) <= human.level().getGameTime();
    }

    public JegMonitorWeapons(Human human, MonitorCombatSession session) {
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
            if (attack && !human.isUsingItem() && !backend.isReloading(human, weapon) && backend.hasLoadedAmmo(weapon))
                heldTicks = Math.min(heldTicks + 1, 6);
            else heldTicks = 0;
            control.aim(attack);
            if (releaseShot && GunFireAuthorization.allows(human, weapon, null))
                GunAttackAction.run(human, () -> control.shoot(weapon, null, human.getXRot(), human.getYRot()));
            return;
        }
        // Releasing the trigger cancels the native NPC windup even when aim remains held.
        control.aim(attack);
        if (!trigger.held(attack) || !GunFireAuthorization.allows(human, weapon, null)) return;
        long before = backend.nextShotAt(human);
        GunAttackAction.run(human, () -> control.shoot(weapon, null, human.getXRot(), human.getYRot()));
        if (backend.nextShotAt(human) != before) trigger.accepted(backend.continuousTrigger(weapon));
    }

    @Override public void cancel() {
        control.aim(false);
        backend.cancelReload(human, weapon);
        trigger.reset(); previousReload = false; heldTicks = 0;
    }
}
