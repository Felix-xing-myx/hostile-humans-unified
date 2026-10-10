package club.someoneice.humangunner;

import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import net.minecraft.world.item.ItemStack;

/** Loaded only when First Aid is present. Uses its public healing API, not player body internals. */
final class FirstAidApiBridge {
    private FirstAidApiBridge() {
    }

    static int applyTicks(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemHealing healing)) return -1;
        // First Aid exposes GUI application time in milliseconds, not game ticks.
        return Math.max(1, (int) Math.ceil(healing.getApplyTime(stack) / 50.0D));
    }

    static FirstAidCompat.HealingSchedule schedule(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemHealing healing)) return null;
        AbstractPartHealer healer = healing.createNewHealer(stack.copy());
        if (healer == null) return null;
        int pulses = healer.maxHeal.getAsInt();
        int interval = healer.ticksPerHeal.getAsInt();
        if (pulses <= 0 || interval <= 0 || (long) pulses * interval > Integer.MAX_VALUE) return null;
        return new FirstAidCompat.HealingSchedule(pulses, interval);
    }
}
