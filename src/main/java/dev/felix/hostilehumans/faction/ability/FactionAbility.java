package dev.felix.hostilehumans.faction.ability;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.world.damagesource.DamageSource;

/** Passive capabilities consume event facts; they never acquire AI action or movement ownership. */
interface FactionAbility {
    record IncomingAttack(Human human, DamageSource source, float amount) {}
    /** True consumes this incoming attack. No health, durability or pressure is fabricated. */
    boolean evade(IncomingAttack attack);
    /** Read-only server diagnostics, never rolls or changes an ability. */
    default String describe(Human human) { return "能力等待事件"; }
}
