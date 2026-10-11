package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.GunSupport;
import club.someoneice.humangunner.SpartanEquipmentCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Vanilla held-item placement plus Better Combat's animated item channels for Humans. */
final class HumanAnimatedItemInHandLayer extends ItemInHandLayer<Human, PlayerModel<Human>> {
    private final ItemInHandRenderer itemRenderer;

    HumanAnimatedItemInHandLayer(RenderLayerParent<Human, PlayerModel<Human>> parent,
                                ItemInHandRenderer itemRenderer) {
        super(parent, itemRenderer);
        this.itemRenderer = itemRenderer;
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack item, ItemDisplayContext displayContext,
                                     HumanoidArm arm, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight) {
        if (item.isEmpty()) return;
        Human human = (Human) entity;
        poseStack.pushPose();
        try {
            if (arm != human.getMainArm() && GunSupport.isGunItem(human.getMainHandItem())
                    && SpartanEquipmentCompat.isShield(item)) {
                renderBackShield(human, item, poseStack, buffer, packedLight);
                return;
            }
            getParentModel().translateToHand(arm, poseStack);
            BetterCombatNpcAnimator.applyHeldItemBend(human, arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F,
                    0.125F, -0.625F);
            BetterCombatNpcAnimator.applyHeldItemTransform(human, arm, poseStack);
            if (!ScgunsNpcAnimator.render(human, item, displayContext, poseStack, buffer, packedLight))
                itemRenderer.renderItem(human, item, displayContext,
                        arm == HumanoidArm.LEFT, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }

    /** Visual placement only: shield custody, protection and durability remain server-owned. */
    private void renderBackShield(Human human, ItemStack shield, PoseStack poses,
                                  MultiBufferSource buffer, int light) {
        getParentModel().body.translateAndRotate(poses);
        boolean elytra = human.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElytraItem;
        poses.translate(0, .30, elytra ? .55 : .38);
        poses.mulPose(Axis.ZP.rotationDegrees(human.getMainArm() == HumanoidArm.RIGHT ? 25 : -25));
        // Item models use Y-up; the humanoid layer uses Y-down. Turn the shield upright,
        // with its face toward the rear, before rendering in this torso-local frame.
        poses.mulPose(Axis.XP.rotationDegrees(180));
        if (shield.is(Items.SHIELD)) {
            // Vanilla's special renderer is centered at zero, unlike baked item models.
            // Cancel ItemRenderer's (-.5, -.5, -.5) origin shift. Its native Y/Z
            // inversion already cancels our upright turn above; do not invert it again.
            // Keep the native renderer for banner patterns and enchantment foil.
            poses.translate(.5, .5, .5);
        }
        // NONE avoids item-frame and hand transforms pushing different mod shields sideways.
        itemRenderer.renderItem(human, shield, ItemDisplayContext.NONE, false, poses, buffer, light);
    }
}
