package dev.felix.hostilehumans.economy;

import net.minecraft.server.level.ServerPlayer;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** Optional linkage stays in this class; cosmetics never enter economic holdings. */
public final class CuriosWalletAccess {
    private CuriosWalletAccess() {}

    static void collect(ServerPlayer player, List<WalletInventory.Slot> slots) {
        CuriosApi.getCuriosInventory(player)
                .ifPresent(
                        inventory ->
                                inventory.getCurios().entrySet().stream()
                                        .sorted(
                                                java.util.Comparator
                                                        .<java.util.Map.Entry<
                                                                        String,
                                                                        top.theillusivec4.curios.api
                                                                                .type.inventory
                                                                                .ICurioStacksHandler>>
                                                                comparingInt(
                                                                        e ->
                                                                                e.getKey()
                                                                                                .equals(
                                                                                                        "wallet")
                                                                                        ? 0
                                                                                        : 1)
                                                        .thenComparing(java.util.Map.Entry::getKey))
                                        .forEach(
                                                entry -> {
                                                    var stacks = entry.getValue().getStacks();
                                                    for (int i = 0; i < stacks.getSlots(); i++) {
                                                        int index = i;
                                                        if (stacks.getStackInSlot(i).getItem()
                                                                instanceof WalletItem)
                                                            slots.add(
                                                                    new WalletInventory.Slot(
                                                                            "curios:"
                                                                                    + entry.getKey()
                                                                                    + ":"
                                                                                    + i,
                                                                            false,
                                                                            () ->
                                                                                    stacks
                                                                                            .getStackInSlot(
                                                                                                    index),
                                                                            stack ->
                                                                                    stacks
                                                                                            .setStackInSlot(
                                                                                                    index,
                                                                                                    stack)));
                                                    }
                                                }));
    }
}
