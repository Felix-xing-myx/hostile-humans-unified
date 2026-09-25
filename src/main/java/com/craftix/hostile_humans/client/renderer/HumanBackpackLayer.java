package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public class HumanBackpackLayer
extends RenderLayer<Human, PlayerModel<Human>> {
    private static final String BACK_SLOT = "back";

    public HumanBackpackLayer(RenderLayerParent<Human, PlayerModel<Human>> renderer) {
        super(renderer);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Human human, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (human.isInvisible()) {
            return;
        }
        CuriosApi.getCuriosInventory((LivingEntity)human).ifPresent(curiosInventory -> curiosInventory.getStacksHandler(BACK_SLOT).ifPresent(backSlot -> {
            ItemStack backpack = backSlot.getStacks().getStackInSlot(0);
            if (!backpack.isEmpty()) {
                this.renderBackItem(poseStack, buffer, packedLight, human, backpack);
            }
        }));
    }

    private void renderBackItem(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Human human, ItemStack backpack) {
        poseStack.pushPose();
        ((PlayerModel)this.getParentModel()).body.translateAndRotate(poseStack);
        poseStack.translate(0.0f, 0.2f, 0.29f);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
        poseStack.scale(0.9f, 0.9f, 0.9f);
        Minecraft.getInstance().getItemRenderer().renderStatic((LivingEntity)human, backpack, ItemDisplayContext.FIXED, false, poseStack, buffer, human.level(), packedLight, OverlayTexture.NO_OVERLAY, human.getId());
        poseStack.popPose();
    }
}

