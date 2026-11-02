package dev.felix.hostilehumans.economy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Deterministic actual slot handles. No serialized capability copy and no synthetic wallet
 * balances.
 */
public final class WalletInventory {
    public record Slot(
            String key,
            boolean mainInventory,
            Supplier<ItemStack> read,
            Consumer<ItemStack> write) {}

    private WalletInventory() {}

    public static List<Slot> slots(ServerPlayer player) {
        var slots = new ArrayList<Slot>();
        if (ModList.get().isLoaded("curios")) CuriosWalletAccess.collect(player, slots);
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            int index = i;
            slots.add(
                    new Slot(
                            "inventory:" + i,
                            i < inventory.items.size(),
                            () -> inventory.getItem(index),
                            stack -> inventory.setItem(index, stack)));
        }
        // A third-party alias to exactly the same physical stack must not count its value twice.
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        slots.removeIf(slot -> !slot.read().get().isEmpty() && !seen.add(slot.read().get()));
        if (slots.size() > 105) throw new IllegalStateException("too many economic slot handles");
        return List.copyOf(slots);
    }

    public static void initializeEmptyWallets(ServerPlayer player) {
        for (var slot : slots(player)) {
            var stack = slot.read().get();
            var value = WalletCodec.read(stack);
            if (value.isPresent() && value.get().id().equals(EconomyView.NONE)) {
                var updated = stack.copy();
                WalletCodec.write(
                        updated, new WalletCodec.Data(UUID.randomUUID(), 0, value.get().balance()));
                slot.write().accept(updated);
            }
        }
        player.getInventory().setChanged();
    }
}
