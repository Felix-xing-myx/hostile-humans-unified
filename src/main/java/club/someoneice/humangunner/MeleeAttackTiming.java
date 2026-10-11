package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.util.RandomSource;

/** One cooldown policy shared by every human close-combat goal. */
public final class MeleeAttackTiming {
    private static final java.util.Map<Human, Integer> READY_AT = new java.util.WeakHashMap<>();
    private MeleeAttackTiming() {}

    public static int nextCooldown(Human human) {
        int cooldown;
        if (BetterCombatMeleeCombat.hasWeaponProfile(human)) {
            cooldown = BetterCombatMeleeCombat.cooldownTicks(human);
        } else {
            UnifiedConfig.Tier tier = TierAttributes.of(human);
            cooldown = human.getRandom().nextInt(tier.meleeCooldownMin(), tier.meleeCooldownMax() + 1);
        }
        READY_AT.put(human, human.tickCount + cooldown);
        return cooldown;
    }

    /** Forecast only; never opens an attack window or changes the owning goal's timer. */
    static int remainingCooldown(Human human) {
        return Math.max(0, READY_AT.getOrDefault(human, human.tickCount) - human.tickCount);
    }

    /** Fallback for a non-Human base entity retained for compatibility. */
    public static int nextCooldown(RandomSource random) {
        int configuredMin = UnifiedConfig.get().tier("roamer").meleeCooldownMin();
        int configuredMax = UnifiedConfig.get().tier("roamer").meleeCooldownMax();
        int minimum = Math.max(1, configuredMin);
        int maximum = Math.max(minimum, configuredMax);
        return random.nextInt(minimum, maximum + 1);
    }
}
