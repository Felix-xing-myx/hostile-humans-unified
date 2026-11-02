package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.entity.Human;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The held monitor stores a server-authored UUID and last location; opening always rechecks live ownership. */
public final class MonitorTargetBinding {
    private static final String KEY = "hostile_humans_unified:monitor_binding";
    private MonitorTargetBinding() { }
    public static void bind(ItemStack item, Human human) {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putUUID("target", human.getUUID());
        tag.putString("dimension", human.level().dimension().location().toString());
        tag.putLong("position", human.blockPosition().asLong());
        item.getOrCreateTag().put(KEY, tag);
    }
    public static void open(ServerPlayer player, ItemStack item) {
        var tag = item.getTag();
        if (tag == null || !tag.contains(KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) { HumanMonitorService.unavailable(player); return; }
        var binding = tag.getCompound(KEY);
        if (!binding.hasUUID("target") || !player.level().dimension().location().toString().equals(binding.getString("dimension"))) {
            HumanMonitorService.unavailable(player); return;
        }
        var target = binding.getUUID("target");
        var entity = player.serverLevel().getEntity(target);
        if (entity instanceof Human human) {
            if (MonitorAccessPolicy.allows(player, human)) { bind(item, human); HumanMonitorService.open(player, human); }
            else HumanMonitorService.unavailable(player);
            return;
        }
        // Survival bootstrap positions come only from the authoritative owned-soldier record.
        if (!player.isCreative()) { HumanMonitorService.openOwned(player, target); return; }
        var position = BlockPos.of(binding.getLong("position"));
        if (!player.serverLevel().getWorldBorder().isWithinBounds(position)) { HumanMonitorService.unavailable(player); return; }
        HumanMonitorService.resolve(player, target, position);
    }
}
