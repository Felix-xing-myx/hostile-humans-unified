package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.item.*;
import ttv.migami.jeg.Config;

/** Native LivingEntity factories. Inventory settlement is owned by the shared planner. */
final class JegThrowables {
    private JegThrowables() {}
    static boolean supported(ItemStack stack) {
        var item = stack.getItem();
        // A sticky long-duration charge is not an ordinary combat grenade.
        return !(item instanceof ExplosiveChargeItem) && (item instanceof GrenadeItem
                || item instanceof WaterBombItem || item instanceof MolotovCocktailItem);
    }
    static ThrowableProjectile create(Human human, ItemStack stack, int fuse) {
        var item = stack.getItem();
        ttv.migami.jeg.entity.throwable.ThrowableGrenadeEntity result;
        if (!supported(stack)) return null;
        if (item instanceof WaterBombItem water) result = water.create(human.level(), human, fuse);
        else if (item instanceof MolotovCocktailItem molotov) result = molotov.create(human.level(), human, fuse);
        else result = ((GrenadeItem) item).create(stack.copyWithCount(1), human.level(), human, fuse);
        result.setItem(stack.copyWithCount(1));
        return result;
    }
    static ScgunsThrowables.FlightProfile flightProfile(ItemStack stack, int fuse) {
        var item = stack.getItem();
        if (item instanceof StunGrenadeItem) return new ScgunsThrowables.FlightProfile((double) .05F, true, 20);
        if (item instanceof WaterBombItem) return new ScgunsThrowables.FlightProfile((double) .05F, false, 120);
        if (item instanceof MolotovCocktailItem) return new ScgunsThrowables.FlightProfile((double) .05F, false, 60);
        return new ScgunsThrowables.FlightProfile((double) .05F, true, fuse);
    }
    static double safetyRadius(ItemStack stack, double configured) {
        var item = stack.getItem();
        if (item instanceof StunGrenadeItem)
            return Math.max(configured, Math.max(Config.COMMON.stunGrenades.deafen.criteria.radius.get(),
                    Config.COMMON.stunGrenades.blind.criteria.radius.get()) + 2);
        if (item instanceof SmokeGrenadeItem)
            return Math.max(configured, Config.COMMON.smokeGrenades.smokeGrenadeCloudDiameter.get() * .5 + 2);
        if (item instanceof MolotovCocktailItem) return Math.max(configured, 16);
        return Math.max(configured, Math.max(5, Config.COMMON.grenades.explosionRadius.get()) + 2);
    }
}
