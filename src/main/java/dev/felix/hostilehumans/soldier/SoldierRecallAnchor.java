package dev.felix.hostilehumans.soldier;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Item-owned feet position. No entity identity, ownership transfer or chunk loading in this codec. */
record SoldierRecallAnchor(ResourceLocation dimension, BlockPos position) {
    private static final String KEY = "HostileHumansUnifiedRecallAnchor";
    SoldierRecallAnchor {
        java.util.Objects.requireNonNull(dimension);
        position = java.util.Objects.requireNonNull(position).immutable();
        if (Math.abs((long) position.getX()) > 29_999_984 || Math.abs((long) position.getZ()) > 29_999_984)
            throw new IllegalArgumentException("Recall position exceeds world limits");
    }
    CompoundTag write() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("dimension", dimension.toString());
        tag.putInt("x", position.getX()); tag.putInt("y", position.getY()); tag.putInt("z", position.getZ());
        return tag;
    }
    static Optional<SoldierRecallAnchor> read(CompoundTag tag) {
        if (tag.getInt("version") != 1 || !tag.contains("dimension", Tag.TAG_STRING)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT)) return Optional.empty();
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return Optional.empty();
        try { return Optional.of(new SoldierRecallAnchor(dimension, new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")))); }
        catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }
    static Optional<SoldierRecallAnchor> of(ItemStack stack) {
        return stack.getItem() instanceof SoldierRecallCharmItem && stack.hasTag()
                ? read(stack.getTag().getCompound(KEY)) : Optional.empty();
    }
    void bind(ItemStack stack) { stack.getOrCreateTag().put(KEY, write()); }
}
