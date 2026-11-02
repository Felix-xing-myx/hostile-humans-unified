package dev.felix.hostilehumans.behavior.combat;

import dev.felix.hostilehumans.entity.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.WeakHashMap;

/** Input authority only. Weapon executors retain cooldowns, payment, damage and action-resource ownership.
 * Autonomous combat is the default; an external controller must own and explicitly revoke its lease. */
public final class HumanCombatAuthority {
    public interface Controller {
        boolean active();
        boolean targetAllowed(LivingEntity target);
        boolean targetOwned(LivingEntity target);
        boolean weaponOwned(ItemStack weapon);
        boolean gunRequested(ItemStack weapon);
        boolean reloadRequested(ItemStack weapon);
        boolean arrowRequested(ItemStack weapon, boolean followup);
    }
    private static final Map<Human, Controller> CONTROLLERS = new WeakHashMap<>();
    private HumanCombatAuthority() { }
    public static void bind(Human human, Controller controller) {
        if (human.level().isClientSide || CONTROLLERS.putIfAbsent(human, controller) != null)
            throw new IllegalStateException("Combat controller already bound or client-owned");
    }
    public static void release(Human human, Controller controller) { CONTROLLERS.remove(human, controller); }
    private static Controller current(Human human) {
        Controller controller = CONTROLLERS.get(human);
        return controller != null && controller.active() ? controller : null;
    }
    public static boolean bound(Human human) { return CONTROLLERS.containsKey(human); }
    public static boolean live(Human human) { return current(human) != null; }
    /** Manual aim is not visual recognition and never creates an AI memory/report. */
    public static boolean hasTargetInput(Human human, LivingEntity target) {
        var control = current(human);
        return control != null ? control.targetAllowed(target)
                : dev.felix.hostilehumans.perception.HumanPerception.canTarget(human, target);
    }
    public static boolean allowsTarget(Human human, LivingEntity target) {
        var control = current(human); return control != null && control.targetAllowed(target);
    }
    public static boolean ownsTarget(Human human, LivingEntity target) {
        var control = current(human); return control != null && control.targetOwned(target);
    }
    public static boolean ownsWeapon(Human human, ItemStack weapon) {
        var control = current(human); return control != null && control.weaponOwned(weapon);
    }
    public static boolean gunIntent(Human human, ItemStack weapon) {
        var control = current(human); return control != null && control.gunRequested(weapon);
    }
    public static boolean reloadIntent(Human human, ItemStack weapon) {
        var control = current(human); return control != null && control.reloadRequested(weapon);
    }
    public static boolean arrowIntent(Human human, ItemStack weapon, boolean followup) {
        var control = current(human); return control != null && control.arrowRequested(weapon, followup);
    }
}
