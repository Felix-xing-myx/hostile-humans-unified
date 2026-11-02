package dev.felix.hostilehumans.damage;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.HumanFinisherSelection;
import dev.felix.hostilehumans.behavior.combat.defense.RecentDamageTracker;
import dev.felix.hostilehumans.faction.StandingEffects;

import net.minecraft.world.damagesource.DamageSource;

/** Single input for actual health lost inside a damage receipt. Shield/absorption-only hits never enter. */
public final class HumanDamageEvents {
    private HumanDamageEvents() {}
    public static void healthLost(Human human, DamageSource source, double amount) {
        if (human.level().isClientSide || !Double.isFinite(amount) || amount <= 0) return;
        RecentDamageTracker.record(human, (float) amount, source.getEntity());
        HumanFinisherSelection.damaged(human);
        StandingEffects.damage(human, source, (float) amount);
    }
}
