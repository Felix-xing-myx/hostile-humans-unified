package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

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
            getParentModel().translateToHand(arm, poseStack);
            BetterCombatNpcAnimator.applyHeldItemBend(human, arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F,
                    0.125F, -0.625F);
            BetterCombatNpcAnimator.applyHeldItemTransform(human, arm, poseStack);
            itemRenderer.renderItem(human, item, displayContext,
                    arm == HumanoidArm.LEFT, poseStack, buffer, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
