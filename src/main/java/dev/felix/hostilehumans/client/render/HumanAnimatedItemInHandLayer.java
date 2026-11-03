package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
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

/** Vanilla held-item placement plus Better Combat's animated item channels for Humans. */
final class HumanAnimatedItemInHandLayer extends ItemInHandLayer<Human, PlayerModel<Human>> {
    private final ItemInHandRenderer itemRenderer;
    private final boolean spectator;

    HumanAnimatedItemInHandLayer(RenderLayerParent<Human, PlayerModel<Human>> parent,
                                ItemInHandRenderer itemRenderer, boolean spectator) {
        super(parent, itemRenderer);
        this.itemRenderer = itemRenderer;
        this.spectator = spectator;
    }

    @Override
    protected void renderArmWithItem(LivingEntity entity, ItemStack item, ItemDisplayContext displayContext,
                                     HumanoidArm arm, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight) {
        if (item.isEmpty()) return;
        Human human = (Human) entity;
        if (getParentModel() instanceof HumanPlayerModel model && !model.fullDetail()) {
            super.renderArmWithItem(entity,item,displayContext,arm,poseStack,buffer,packedLight);
            return;
        }
        // Hide only the physical offhand item, never the supporting arm or shared world model.
        if (spectator && arm != human.getMainArm() && !HumanSpectatorPresentation.showOffhandItem(human)) return;
        poseStack.pushPose();
        try {
            if (arm != human.getMainArm() && GunSupport.isGunItem(human.getMainHandItem())
                    && SpartanEquipmentCompat.isShield(item)
                    && !HumanGunPresentation.activeShield(human)) {
                BackShieldPose.Profile profile = BackShieldPose.resolve(human, item);
                if (!profile.offhand()) {
                    renderBackShield(human, item, profile, arm, poseStack, buffer, packedLight);
                    return;
                }
            }
            HumanoidArm visualArm = HumanGunPresentation.itemArm(human, arm);
            ItemDisplayContext visualContext = HumanGunPresentation.context(visualArm);
            getParentModel().translateToHand(visualArm, poseStack);
            BetterCombatNpcAnimator.applyHeldItemBend(human, visualArm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.translate((visualArm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F,
                    0.125F, -0.625F);
            BetterCombatNpcAnimator.applyHeldItemTransform(human, visualArm, poseStack);
            if (!ScgunsNpcAnimator.render(human, item, visualContext, poseStack, buffer, packedLight)
                    && !JegNpcAnimator.render(human, item, visualContext, poseStack, buffer, packedLight)) {
                if (GunSupport.isTaczGunItem(item) && arm == human.getMainArm()) {
                    var previous = TaczNpcEffects.begin(human, item, buffer);
                    try {
                        // Arm animation, hand attachment and TaCZ draw context all use
                        // the authored right-hand convention, including left-handed NPCs.
                        itemRenderer.renderItem(human, item, visualContext,
                                visualArm == HumanoidArm.LEFT, poseStack, buffer, packedLight);
                    } finally { TaczNpcEffects.end(previous); }
                } else {
                    itemRenderer.renderItem(human, item, visualContext,
                            visualArm == HumanoidArm.LEFT, poseStack, buffer, packedLight);
                }
            }
        } finally {
            poseStack.popPose();
        }
    }

    /** Visual placement only: shield custody, protection and durability remain server-owned. */
    private void renderBackShield(Human human, ItemStack shield, BackShieldPose.Profile profile,
                                  HumanoidArm arm, PoseStack poses,
                                  MultiBufferSource buffer, int light) {
        getParentModel().body.translateAndRotate(poses);
        boolean elytra = human.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElytraItem;
        poses.translate(0, .30, elytra ? .55 : .38);
        poses.mulPose(Axis.ZP.rotationDegrees(human.getMainArm() == HumanoidArm.RIGHT ? 25 : -25));
        ItemDisplayContext context = profile.apply(poses, arm);
        itemRenderer.renderItem(human, shield, context,
                profile.thirdPerson() && arm == HumanoidArm.LEFT, poses, buffer, light);
    }
}
