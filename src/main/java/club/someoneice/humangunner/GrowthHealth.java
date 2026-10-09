package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Owns only our base-health value; Minecraft and other mods retain their attribute modifiers. */
final class GrowthHealth {
    private GrowthHealth() { }

    static void apply(Human human) {
        CompoundTag state = HumanGrowth.state(human);
        AttributeInstance maximum = human.getAttribute(Attributes.MAX_HEALTH);
        if (maximum == null) return;
        boolean first = !state.contains("owns_health");
        double live = maximum.getBaseValue();
        double previous = state.getDouble("owned_health");
        double original = TierThreeHuman.isTierThree(human) ? 50 : 60;
        double oldRoll = human.getPersistentData().getDouble(HumanGunner.MOD_ID + ":rolled_max_health");
        var data = human.getPersistentData();
        boolean previousVanilla = first && data.getBoolean(HumanGunner.MOD_ID + ":vanilla_health_active");
        if (previousVanilla) {
            state.putBoolean("vanilla", true);
            double saved = data.getDouble(HumanGunner.MOD_ID + ":pre_vanilla_health_base");
            if (!data.getBoolean(HumanGunner.MOD_ID + ":pre_vanilla_tier_health")
                    && Double.isFinite(saved) && saved > 0) state.putDouble("external_health", saved);
            data.remove(HumanGunner.MOD_ID + ":vanilla_health_active");
            data.remove(HumanGunner.MOD_ID + ":pre_vanilla_health_base");
            data.remove(HumanGunner.MOD_ID + ":pre_vanilla_tier_health");
        }
        boolean ours = first ? (MaxHealthCompatibilityPolicy.initialOwnership(live, oldRoll, original)
                || previousVanilla && !state.contains("external_health") && close(live, 20))
                : MaxHealthCompatibilityPolicy.retainsOwnership(state.getBoolean("owns_health"), live, previous, original);
        double wanted = UnifiedConfig.get().vanillaMode() ? 20 : HumanGrowth.fullHealth(human);
        // Switching vanilla mode must not lose an external base supplied before the switch.
        if (UnifiedConfig.get().vanillaMode()) {
            if (!state.getBoolean("vanilla") && !ours) state.putDouble("external_health", live);
            boolean ownsOriginal = first ? ours : state.getBoolean("owns_health");
            state.putBoolean("owns_health", ownsOriginal);
            state.putBoolean("vanilla", true);
            change(human, maximum, wanted, first && state.getBoolean("new_health"));
            state.remove("new_health");
            if (ownsOriginal) state.putDouble("owned_health", wanted);
            return;
        } else if (state.getBoolean("vanilla")) {
            state.remove("vanilla");
            if (state.contains("external_health")) {
                if (close(live, 20)) change(human, maximum, state.getDouble("external_health"), false);
                state.remove("external_health");
                state.putBoolean("owns_health", false);
                return;
            }
        }
        if (ours) {
            boolean fill = first && state.getBoolean("new_health") && human.isAlive();
            change(human, maximum, wanted, fill);
            state.putDouble("owned_health", wanted);
        }
        state.remove("new_health");
        state.putBoolean("owns_health", ours);
        // The old roll is consumed once, never used as an additional random multiplier.
        human.getPersistentData().remove(HumanGunner.MOD_ID + ":rolled_max_health");
        human.getPersistentData().remove(HumanGunner.MOD_ID + ":tier_health_ranges_v2");
    }

    private static boolean close(double a, double b) { return Math.abs(a - b) < 0.001; }

    private static void change(Human human, AttributeInstance maximum, double wanted, boolean fill) {
        float current = human.getHealth();
        if (!close(maximum.getBaseValue(), wanted)) maximum.setBaseValue(wanted);
        if (current <= 0) return;
        float next = fill ? human.getMaxHealth() : VanillaModePolicy.clampedHealth(current, human.getMaxHealth());
        if (Math.abs(next - current) > 0.00001F) human.setHealth(next);
    }
}
