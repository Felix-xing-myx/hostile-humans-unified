package club.someoneice.humangunner;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.common.Gun;
import ttv.migami.jeg.common.ReloadType;
import ttv.migami.jeg.init.ModEnchantments;
import ttv.migami.jeg.util.GunEnchantmentHelper;

/** Native server ReloadTracker schedule adapted to NPC custody and universal magazines. */
final class JegReloadPlan {
    record Step(int ticks, int rounds) {}
    private JegReloadPlan() {}
    static List<Step> create(ItemStack stack, Gun gun, int loaded, int capacity) {
        int missing = capacity - loaded;
        if (missing <= 0) return List.of();
        var reload = gun.getReloads();
        int extra = Math.max(0, reload.getAdditionalReloadTimer());
        int interval = Math.max(1, GunEnchantmentHelper.getReloadInterval(stack,
                reload.getReloadTimer() + (loaded == 0 ? extra : 0)));
        ReloadType type = reload.getReloadType();
        if (type == ReloadType.MAG_FED || type == ReloadType.INVENTORY_FED || type == ReloadType.SINGLE_ITEM) {
            int quick = stack.getEnchantmentLevel(ModEnchantments.QUICK_HANDS.get());
            interval = Math.max(1, Math.round(interval * (quick >= 2 ? .5F : quick == 1 ? .75F : 1F)));
            return List.of(new Step(interval + 1, missing));
        }
        if (type != ReloadType.MANUAL) return List.of(); // Unknown reload contracts fail closed.
        List<Step> result = new ArrayList<>();
        int amount = Math.max(1, reload.getReloadAmount());
        int time = interval + extra; // Native firstReload adds setup time even for a partial magazine.
        int regular = Math.max(1, GunEnchantmentHelper.getReloadInterval(stack, reload.getReloadTimer()));
        int elapsed = 0;
        for (int added = 0; added < missing; added += amount) {
            result.add(new Step(time, Math.min(amount, missing - added)));
            // Upstream checks absolute elapsed tick modulo its now nonempty interval.
            elapsed += time;
            time = regular - elapsed % regular;
        }
        return List.copyOf(result);
    }
}
