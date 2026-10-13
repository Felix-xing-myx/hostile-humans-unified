package club.someoneice.humangunner;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.item.ItemStack;

/** NPC capacity policy. Native saved round/chamber fields remain the sole ammunition ledger. */
final class TaczNpcMagazine {
    private TaczNpcMagazine() {}

    static boolean inventoryFed(ItemStack stack, CommonGunIndex index) {
        IGun gun = IGun.getIGunOrNull(stack);
        var reload = index.getGunData().getReloadData();
        return gun != null && (gun.useInventoryAmmo(stack)
                || reload != null && reload.getType() == FeedType.INVENTORY);
    }

    static int capacity(ItemStack stack, CommonGunIndex index) {
        return inventoryFed(stack, index) ? UnifiedConfig.get().taczInventoryFedCapacity()
                : Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData()));
    }

    /** Server only: cap legacy/native oversized reserves; never fill an empty or partially used gun. */
    static void normalize(ItemStack stack, CommonGunIndex index) {
        if (!inventoryFed(stack, index)) return;
        IGun gun = IGun.getIGunOrNull(stack);
        boolean chamber = index.getGunData().getBolt() != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack);
        int limit = Math.max(0, capacity(stack, index) - (chamber ? 1 : 0));
        int previous = gun.getCurrentAmmoCount(stack);
        int bounded = Math.max(0, Math.min(previous, limit));
        if (previous != bounded) gun.setCurrentAmmoCount(stack, bounded);
        if (index.getGunData().getBolt() == Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack))
            gun.setBulletInBarrel(stack, false);
    }

    static void fill(ItemStack stack, CommonGunIndex index) {
        IGun gun = IGun.getIGunOrNull(stack);
        boolean chamber = index.getGunData().getBolt() != Bolt.OPEN_BOLT;
        int capacity = capacity(stack, index);
        // Ordinary magazine guns retain their native capacity plus chamber convention.
        gun.setCurrentAmmoCount(stack, capacity - (inventoryFed(stack, index) && chamber ? 1 : 0));
        gun.setBulletInBarrel(stack, chamber);
    }
}
