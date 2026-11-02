package dev.felix.hostilehumans.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/**
 * Unknown formats are returned as unknown; the SavedData owner retains the original compound
 * unchanged.
 */
final class PassageCodec {
    private PassageCodec() {}

    static Optional<PassagePermit> read(CompoundTag tag) {
        if (tag.isEmpty()) return Optional.of(PassagePermit.empty());
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1)
            return Optional.empty();
        if (!tag.contains("expires", Tag.TAG_LONG)
                || !tag.contains("ban", Tag.TAG_LONG)
                || !tag.contains("deposit", Tag.TAG_LONG)
                || !tag.contains("initiated", Tag.TAG_BYTE)
                || !tag.contains("refund_pending", Tag.TAG_BYTE)) return Optional.empty();
        try {
            return Optional.of(
                    new PassagePermit(
                            tag.getLong("expires"),
                            tag.getLong("ban"),
                            tag.getLong("deposit"),
                            tag.getBoolean("initiated"),
                            tag.getBoolean("refund_pending")));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    static CompoundTag write(PassagePermit state) {
        var tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putLong("expires", state.expiresAt());
        tag.putLong("ban", state.banUntil());
        tag.putLong("deposit", state.depositCopper());
        tag.putBoolean("initiated", state.initiated());
        tag.putBoolean("refund_pending", state.refundPending());
        return tag;
    }
}
