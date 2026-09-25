package club.someoneice.humangunner;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Loaded only when Curios is present; the data pack declares the dedicated slot. */
final class CuriosBadgeAccess {
    private static final String SLOT = "identity_badge";

    private CuriosBadgeAccess() {}

    static int highestClearance(Player player) {
        final int[] clearance = {-1};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.getStacksHandler(SLOT).ifPresent(handler -> {
                    for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(slot);
                        if (stack.getItem() instanceof IdentityBadgeItem badge) {
                            clearance[0] = Math.max(clearance[0], badge.clearance());
                        }
                    }
                }));
        return clearance[0];
    }
}
