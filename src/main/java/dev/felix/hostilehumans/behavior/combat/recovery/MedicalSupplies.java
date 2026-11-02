package dev.felix.hostilehumans.behavior.combat.recovery;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.firstaid.FirstAidApiBridge;
import dev.felix.hostilehumans.compat.firstaid.FirstAidCompat;
import dev.felix.hostilehumans.compat.scguns.ScgunsMedical;
import dev.felix.hostilehumans.compat.scguns.ScgunsSettings;
import dev.felix.hostilehumans.config.UnifiedConfig;
import net.minecraft.world.item.ItemStack;
/** Shared recovery boundary; selection/custody never depends on a particular optional mod. */
public final class MedicalSupplies {
    private MedicalSupplies() {}
    public static boolean isSupply(ItemStack stack) { return FirstAidCompat.isSupply(stack) || ScgunsMedical.isSupply(stack); }
    public static boolean isMedicine(ItemStack stack) { return FirstAidCompat.isMedicine(stack) || ScgunsMedical.isSupply(stack); }
    static boolean isMorphine(ItemStack stack) { return FirstAidCompat.isMorphine(stack); }
    static boolean canUse(Human human, ItemStack stack) {
        if (human.getHealth() / Math.max(1F, human.getMaxHealth()) >= .25D) return false;
        if (isMorphine(stack) && (human.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)
                || human.getTarget() == null && !RecoverySupplies.recentActualDamage(human))) return false;
        boolean available = FirstAidCompat.isSupply(stack) ? FirstAidCompat.canUse(human, stack) : ScgunsMedical.canUse(human, stack);
        if (!available) return false;
        if (isMorphine(stack)) return true;
        double netMissing = human.getMaxHealth() - human.getHealth() - RecoverySupplies.pendingHealingWithin(human, 100);
        if (FirstAidCompat.isMedicine(stack)) return netMissing > 0 && FirstAidApiBridge.schedule(stack) != null;
        var profile = ScgunsMedical.profile(stack);
        if (profile == null) return false;
        if (netMissing > 0 && (profile.healing() > 0 || profile.effects().stream().anyMatch(ScgunsSettings.Effect::isRegeneration))) return true;
        return profile.effects().stream().anyMatch(effect -> {
            var type = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(effect.id());
            var active = type == null ? null : human.getEffect(type);
            return type != null && type.isBeneficial() && !effect.isRegeneration()
                    && (active == null || effect.amplifier() > active.getAmplifier());
        });
    }

    static double directHealing(ItemStack stack) {
        var profile = ScgunsMedical.profile(stack);
        return profile == null ? 0 : Math.max(0, profile.healing());
    }
    /** NaN is an explicit unknown backend estimate; selection then uses duration/quality/slot. */
    static double expectedHealing(Human human, ItemStack stack, int ticks) {
        double missing = Math.max(0, human.getMaxHealth() - human.getHealth() - RecoverySupplies.pendingHealingWithin(human, ticks));
        ticks = Math.max(0, ticks - Math.max(0, useDuration(stack)));
        if (FirstAidCompat.isMedicine(stack)) {
            var schedule = FirstAidApiBridge.schedule(stack);
            if (schedule == null) return Double.NaN;
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            var settings = UnifiedConfig.get().firstAid();
            double total = id.getPath().equals("plaster") ? settings.plasterHealing() : settings.bandageHealing();
            double fraction = Math.min(schedule.pulses(), ticks / Math.max(1, schedule.intervalTicks())) / (double) schedule.pulses();
            return Math.min(missing, Math.max(0, total * fraction));
        }
        var profile = ScgunsMedical.profile(stack);
        if (profile == null) return 0;
        double amount = ticks > 0 ? Math.max(0, profile.healing()) : 0;
        for (var effect : profile.effects()) if (effect.isRegeneration())
            amount += Math.min(ticks, effect.durationTicks()) / 20D * (effect.amplifier() >= 1 ? 2 : 1);
        return Math.min(missing, amount);
    }
    public static double inventoryValue(ItemStack stack) { return FirstAidCompat.isSupply(stack) ? FirstAidCompat.inventoryValue(stack) : ScgunsMedical.inventoryValue(stack); }
    static int useDuration(ItemStack stack) { return FirstAidCompat.isSupply(stack) ? FirstAidCompat.useDuration(stack) : ScgunsMedical.useDuration(stack); }
    static float pendingHealing(Human human) { return FirstAidCompat.pendingHealing(human); }
    static void tick(Human human) { FirstAidCompat.tick(human); ScgunsMedical.tick(human); }
}
