package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ThrowableProjectile;

/** One optional-backend boundary for spawn pools, quality and a shared paid throw transaction. */
final class TacticalThrowables {
    private TacticalThrowables() {}
    static boolean supported(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return ScgunsCompat.enabled() && ScgunsThrowableAdapter.supported(stack)
                || JegCompat.enabled() && JegThrowables.supported(stack);
    }
    static void tick(Human human) {
        if (UnifiedConfig.get().scguns().enabled() && (ScgunsCompat.enabled() || JegCompat.enabled()))
            ScgunsThrowables.tick(human);
    }
    static ThrowableProjectile create(Human human, ItemStack stack, int fuse) {
        if (ScgunsCompat.enabled() && ScgunsThrowableAdapter.supported(stack)) {
            return ScgunsThrowableAdapter.create(human, stack, fuse);
        }
        if (JegCompat.enabled() && JegThrowables.supported(stack)) return JegThrowables.create(human, stack, fuse);
        return null;
    }
    static ScgunsThrowables.FlightProfile flightProfile(ItemStack stack, int fuse) {
        return JegCompat.enabled() && JegThrowables.supported(stack)
                ? JegThrowables.flightProfile(stack, fuse) : ScgunsThrowableAdapter.flightProfile(stack, fuse);
    }
    static double safetyRadius(ItemStack stack, double configured) {
        return JegCompat.enabled() && JegThrowables.supported(stack)
                ? JegThrowables.safetyRadius(stack, configured) : ScgunsThrowableAdapter.safetyRadius(stack, configured);
    }
}
