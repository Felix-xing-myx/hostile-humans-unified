package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.entity.throwable.ThrowableItemEntity;
import top.ribs.scguns.item.*;
import club.someoneice.humangunner.ScgunsThrowables.FlightProfile;

/** Optional native factories and physical profiles; no independent tactical tick. */
final class ScgunsThrowableAdapter {
    private ScgunsThrowableAdapter() {}
    static boolean supported(ItemStack stack) {
        var item = stack.getItem();
        if (item instanceof StunGrenadeItem) return false;
        return item instanceof GrenadeItem || item instanceof GasGrenadeItem || item instanceof ChokeBombItem
                || item instanceof MolotovCocktailItem || item instanceof NailBombItem || item instanceof HellfireBombItem
                || item instanceof SwarmBombItem || item instanceof BeaconGrenadeItem
                || item instanceof ThrowableShotballItem;
    }
    /** Public factory dispatch preserves native entity ownership, detonation and effects. */
    static net.minecraft.world.entity.projectile.ThrowableProjectile create(Human human, ItemStack stack, int fuse) {
        ThrowableItemEntity result = createNative(human, stack, fuse);
        if (result != null) result.setItem(stack.copyWithCount(1));
        return result;
    }
    private static ThrowableItemEntity createNative(Human human, ItemStack stack, int fuse) {
        var item = stack.getItem();
        if (item instanceof GrenadeItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof GasGrenadeItem grenade) return grenade.create(human.level(), human, fuse, 10);
        if (item instanceof ChokeBombItem grenade) return grenade.create(human.level(), human, fuse, 10);
        if (item instanceof MolotovCocktailItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof NailBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof HellfireBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof SwarmBombItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof BeaconGrenadeItem grenade) return grenade.create(human.level(), human, fuse);
        if (item instanceof ThrowableShotballItem)
            return new top.ribs.scguns.entity.throwable.ThrowableShotballEntity(human.level(), human);
        return null;
    }
    static FlightProfile flightProfile(ItemStack stack, int fuse) {
        var item = stack.getItem();
        if (item instanceof GasGrenadeItem) return new FlightProfile((double) .05F, true, 21);
        if (item instanceof ChokeBombItem) return new FlightProfile((double) .05F, true, 41);
        if (item instanceof GrenadeItem) return new FlightProfile((double) .05F, true, fuse);
        if (item instanceof BeaconGrenadeItem) return new FlightProfile((double) .025F, true, fuse);
        if (item instanceof ThrowableShotballItem) return new FlightProfile((double) .04F, false, fuse);
        return new FlightProfile((double) .05F, false, fuse);
    }

    static double safetyRadius(ItemStack stack, double configured) {
        return stack.getItem() instanceof GasGrenadeItem || stack.getItem() instanceof ChokeBombItem
                ? Math.max(16, configured) : configured;
    }
}
