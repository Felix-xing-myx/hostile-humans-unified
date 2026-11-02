package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.CombatWeaponPolicy;
import dev.felix.hostilehumans.navigation.MovementSpeedController;

import net.minecraft.world.item.ItemStack;

/** One movement restriction for the currently executing ranged weapon, never a firing speed boost. */
public final class RangedStrafeSpeed {
    private static final java.util.UUID LEGACY = java.util.UUID.fromString("80e8306b-15a5-49de-9771-b4dd99bc0862");
    private RangedStrafeSpeed() { }
    public static void setBow(Human human, boolean active) { set(human, active); }
    public static void setCrossbow(Human human, boolean active) { set(human, active); }
    public static void setTrident(Human human, boolean active) { set(human, active); }
    public static void clear(Human human) { set(human, false); }
    private static void set(Human human, boolean active) {
        var speed = human.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(LEGACY);
        ItemStack weapon = human.getMainHandItem();
        if (!CombatWeaponPolicy.isBowOrCrossbow(weapon) && !(weapon.getItem() instanceof net.minecraft.world.item.TridentItem)) weapon = human.getOffhandItem();
        MovementSpeedController.limit(human, MovementSpeedController.Limit.RANGED,
                active ? RangedCombatProfile.of(human, weapon).strafeSpeed() : 1, active);
    }
}
