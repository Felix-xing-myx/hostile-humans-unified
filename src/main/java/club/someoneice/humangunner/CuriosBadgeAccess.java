package club.someoneice.humangunner;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/** Loaded only with Curios. Read functional slots, never cosmetic slots. */
final class CuriosBadgeAccess {

    private CuriosBadgeAccess() {}

    static int highestClearance(Player player) {
        final int[] clearance = {-1};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            // The dedicated slot is provided by our data pack. Curios also
            // allows tagged items in a generic curio slot, so recognize that too.
            for (var handler : inventory.getCurios().values()) {
                for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
                    ItemStack stack = handler.getStacks().getStackInSlot(slot);
                    if (stack.getItem() instanceof IdentityBadgeItem badge) {
                        clearance[0] = Math.max(clearance[0], badge.clearance());
                    }
                }
            }
        });
        return clearance[0];
    }
}
