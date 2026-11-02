package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Wallet is item-owned. Unknown schema/currency/bounds retain their raw NBT and cannot be spent.
 */
public final class WalletCodec {
    public static final String KEY = "hostile_humans_unified_wallet";

    public record Data(UUID id, int revision, WalletBalance balance) {}

    private WalletCodec() {}

    public static Optional<Data> read(ItemStack stack) {
        if (!(stack.getItem() instanceof WalletItem)) return Optional.empty();
        if (!stack.hasTag() || !stack.getTag().contains(KEY))
            return Optional.of(new Data(EconomyView.NONE, 0, WalletBalance.empty()));
        return WalletPayloadCodec.read(stack.getTag().getCompound(KEY))
                .map(value -> new Data(value.id(), value.revision(), value.balance()));
    }

    public static void write(ItemStack stack, Data value) {
        if (read(stack).isEmpty())
            throw new IllegalArgumentException("unknown wallet data retained");
        var tag = stack.hasTag() ? stack.getTag().getCompound(KEY) : new CompoundTag();
        stack.getOrCreateTag()
                .put(
                        KEY,
                        WalletPayloadCodec.write(
                                tag,
                                new WalletPayloadCodec.Data(
                                        value.id(), value.revision(), value.balance())));
    }

    public static void balance(ItemStack stack, WalletBalance balance) {
        var old =
                read(stack)
                        .orElseThrow(() -> new IllegalArgumentException("wallet schema unknown"));
        write(stack, new Data(old.id(), Math.addExact(old.revision(), 1), balance));
    }
}
