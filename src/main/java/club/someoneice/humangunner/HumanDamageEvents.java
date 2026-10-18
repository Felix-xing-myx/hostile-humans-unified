package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;

/** Single input for actual health lost inside a damage receipt. Shield/absorption-only hits never enter. */
final class HumanDamageEvents {
    private HumanDamageEvents() {}
    static void healthLost(Human human, DamageSource source, double amount) {
        if (human.level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        RecentDamageTracker.record(human, (float) amount, source.getEntity());
        StandingEffects.damage(human, source, (float) amount);
    }
}
