package dev.felix.hostilehumans.economy;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class WalletPayloadCodecTest {
    private static int checks;

    private static void require(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var data =
                new WalletPayloadCodec.Data(
                        UUID.randomUUID(),
                        7,
                        WalletBalance.empty().with(Currency.ASHEN, 12345).autoPickup(false));
        var raw = WalletPayloadCodec.write(new CompoundTag(), data);
        require(WalletPayloadCodec.read(raw).orElseThrow().equals(data), "actual NBT roundtrip");
        raw.putString("future_note", "preserve");
        var updated =
                WalletPayloadCodec.write(
                        raw,
                        new WalletPayloadCodec.Data(
                                data.id(), 8, data.balance().with(Currency.AZURE, 99)));
        require(
                updated.getString("future_note").equals("preserve") && raw.getInt("revision") == 7,
                "extensions and old revision preserved");
        for (int kind = 0; kind < 6; kind++) {
            var bad = raw.copy();
            switch (kind) {
                case 0 -> bad.putInt("schema", 2);
                case 1 -> bad.putString("balances", "malformed");
                case 2 -> bad.getCompound("balances").putLong(Currency.ASHEN.faction(), 5);
                case 3 -> bad.getCompound("balances").putLong("unknown:currency", 5);
                case 4 -> bad.getCompound("balances").putLong(Currency.ASHEN.key(), -1);
                case 5 -> bad.putUUID("id", new UUID(0, 0));
            }
            var before = bad.copy();
            require(
                    WalletPayloadCodec.read(bad).isEmpty(),
                    "unknown/malformed payload is not spendable " + kind);
            try {
                WalletPayloadCodec.write(bad, data);
                throw new AssertionError("bad payload overwritten");
            } catch (IllegalArgumentException expected) {
            }
            require(before.equals(bad), "raw bad payload retained " + kind);
        }
        System.out.println(
                "WalletPayloadCodecTest: "
                        + checks
                        + " actual NBT preservation/identity/unknown schema checks passed");
    }
}
