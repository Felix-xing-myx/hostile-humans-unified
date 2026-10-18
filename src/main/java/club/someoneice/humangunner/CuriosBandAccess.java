package club.someoneice.humangunner;

import net.minecraft.world.item.ItemStack;

import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Optional API adapter, loaded only after the Curios availability check. Cosmetic slots never confer identity. */
final class CuriosBandAccess {
    private CuriosBandAccess() {}
    /** Only handlers still owned by Curios are touched. Its private invalid queue is returned by
     * Curios itself; copying serialized old inventories here would duplicate those returns. */
    static void migrateRetiredSlot(net.minecraft.server.level.ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.getStacksHandler("identity_badge").ifPresent(handler -> {
                    returnStacks(player, handler.getStacks());
                    returnStacks(player, handler.getCosmeticStacks());
                }));
    }
    private static void returnStacks(net.minecraft.server.level.ServerPlayer player,
                                     top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks) {
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack source = stacks.getStackInSlot(slot);
            if (source.isEmpty()) continue;
            // The retired identifier accepts no items now. Other Curios identifiers are untouched.
            ItemStack remaining = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
                    new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.getInventory()), source.copy(), false);
            if (remaining.getCount() != source.getCount()) stacks.setStackInSlot(slot, remaining.copy());
            if (remaining.isEmpty()) continue;
            var dropped = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY() + .5, player.getZ(), remaining.copy());
            dropped.setDefaultPickUpDelay();
            if (player.serverLevel().addFreshEntity(dropped)) stacks.setStackInSlot(slot, ItemStack.EMPTY);
            // Rejected world insertion leaves exactly the undelivered remainder for the next retry.
        }
    }
    static IdentityBands.Selection selected(Player player, IdentityBands.Selection baseline) {
        final IdentityBands.Selection[] result = {baseline};
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            inventory.getCurios().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var stacks = entry.getValue().getStacks();
                int priority = entry.getKey().equals("identity_band") ? 0 : 1;
                for (int i = 0; i < stacks.getSlots(); i++)
                    result[0] = IdentityBands.choose(result[0], stacks.getStackInSlot(i), priority,
                            entry.getKey() + ":" + String.format(Locale.ROOT, "%05d", i));
            });
        });
        return result[0];
    }
}
