package dev.felix.hostilehumans.behavior.combat.throwable;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.jeg.JegCompat;
import dev.felix.hostilehumans.compat.jeg.JegThrowables;
import dev.felix.hostilehumans.compat.scguns.ScgunsCompat;
import dev.felix.hostilehumans.compat.scguns.ScgunsThrowableAdapter;
import dev.felix.hostilehumans.compat.scguns.ScgunsThrowables;
import dev.felix.hostilehumans.config.UnifiedConfig;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

/** One optional-backend boundary for spawn pools, quality and a shared paid throw transaction. */
public final class TacticalThrowables {
    private TacticalThrowables() {}
    public static boolean supported(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return ScgunsCompat.enabled() && ScgunsThrowableAdapter.supported(stack)
                || JegCompat.enabled() && JegThrowables.supported(stack);
    }
    public static void tick(Human human) {
        PotionThrowing.tick(human);
        if (UnifiedConfig.get().scguns().enabled() && (ScgunsCompat.enabled() || JegCompat.enabled()))
            ScgunsThrowables.tick(human);
    }
    public static ThrowableProjectile create(Human human, ItemStack stack, int fuse) {
        if (ScgunsCompat.enabled() && ScgunsThrowableAdapter.supported(stack)) {
            return ScgunsThrowableAdapter.create(human, stack, fuse);
        }
        if (JegCompat.enabled() && JegThrowables.supported(stack)) return JegThrowables.create(human, stack, fuse);
        return null;
    }
    public static ScgunsThrowables.FlightProfile flightProfile(ItemStack stack, int fuse) {
        return JegCompat.enabled() && JegThrowables.supported(stack)
                ? JegThrowables.flightProfile(stack, fuse) : ScgunsThrowableAdapter.flightProfile(stack, fuse);
    }
    public static double safetyRadius(ItemStack stack, double configured) {
        return JegCompat.enabled() && JegThrowables.supported(stack)
                ? JegThrowables.safetyRadius(stack, configured) : ScgunsThrowableAdapter.safetyRadius(stack, configured);
    }
}
