package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** All shield visuals consume the server's real use hand and the equipment actually received by the client. */
public final class HumanShieldPresentation {
    private HumanShieldPresentation() {}

    static InteractionHand activeHand(Human human) {
        return activeHand(human.getNpcShieldHand(), human.isAlive(), human.getMainHandItem(), human.getOffhandItem());
    }

    static InteractionHand activeHand(int reportedHand, boolean alive, ItemStack main, ItemStack offhand) {
        if (!alive) return null;
        InteractionHand hand = switch (reportedHand) {
            case 1 -> InteractionHand.MAIN_HAND;
            case 2 -> InteractionHand.OFF_HAND;
            default -> null;
        };
        return hand != null && SpartanEquipmentCompat.isShield(hand == InteractionHand.MAIN_HAND ? main : offhand) ? hand : null;
    }

    static boolean active(Human human) { return activeHand(human) != null; }

    /** Native model predicates receive the same authoritative state as the arm; other entities keep their getter. */
    public static ItemPropertyFunction blockingProperty(ItemPropertyFunction original, ResourceLocation property,
                                                        ItemStack stack, LivingEntity entity) {
        if (original == null || !property.getPath().equals("blocking") || !(entity instanceof Human human)
                || !SpartanEquipmentCompat.isShield(stack)) return original;
        InteractionHand hand = activeHand(human);
        float blocking = hand != null && human.getItemInHand(hand) == stack ? 1 : 0;
        return (renderStack, level, renderEntity, seed) -> blocking;
    }

    /** Final blocking arm wins over weapon/idle channels, including local use-state packet-order gaps. */
    static void apply(Human human, PlayerModel<Human> model) {
        InteractionHand hand = activeHand(human);
        if (hand == null) return;
        HumanoidArm side = hand == InteractionHand.MAIN_HAND ? human.getMainArm() : human.getMainArm().getOpposite();
        applyArm(model, side, ShieldBlockPose.resolve(human, hand, side));
        PlayerAnimatorModelBridge.bind(model, null);
    }

    static void applyArm(PlayerModel<?> model, HumanoidArm side) {
        applyArm(model, side, ShieldBlockPose.vanilla(side));
    }
    static void applyArm(PlayerModel<?> model, HumanoidArm side, ShieldBlockPose.ArmAngles angles) {
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // Absolute arm angles avoid layering the block twice; retain crouch/shoulder pivots.
        arm.xRot = angles.pitch();
        arm.yRot = angles.yaw();
        arm.zRot = 0;
        if (side == HumanoidArm.RIGHT) model.rightSleeve.copyFrom(arm);
        else model.leftSleeve.copyFrom(arm);
    }
}
