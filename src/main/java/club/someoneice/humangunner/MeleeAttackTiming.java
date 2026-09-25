package club.someoneice.humangunner;

import com.craftix.hostile_humans.Config;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.RandomSource;

/** One cooldown policy shared by every human close-combat goal. */
public final class MeleeAttackTiming {
    private MeleeAttackTiming() {}

    public static int nextCooldown(Human human) {
        UnifiedConfig.Tier tier = TierAttributes.of(human);
        return human.getRandom().nextInt(tier.meleeCooldownMin(), tier.meleeCooldownMax() + 1);
    }

    /** Fallback for a non-Human base entity retained for compatibility. */
    public static int nextCooldown(RandomSource random) {
        int configuredMin = Math.min(Config.meleeAttackCooldownMin.get(), Config.meleeAttackCooldownMax.get());
        int configuredMax = Math.max(Config.meleeAttackCooldownMin.get(), Config.meleeAttackCooldownMax.get());
        int minimum = Math.max(1, configuredMin);
        int maximum = Math.max(minimum, configuredMax);
        return random.nextInt(minimum, maximum + 1);
    }
}
