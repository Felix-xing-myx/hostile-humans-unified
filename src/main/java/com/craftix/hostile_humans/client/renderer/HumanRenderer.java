package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.client.renderer.HumanBackpackLayer;
import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@OnlyIn(value=Dist.CLIENT)
public class HumanRenderer
extends HumanoidMobRenderer<Human, PlayerModel<Human>> {
    public HumanRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanPlayerModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        // HumanoidMobRenderer installs the vanilla held-item layer. Replace
        // only this Human renderer's instance so item keyframes follow the
        // same Better Combat attack as the body, without a global Mixin.
        this.layers.removeIf(layer -> layer instanceof ItemInHandLayer);
        this.addLayer(new HumanAnimatedItemInHandLayer(this, context.getItemInHandRenderer()));
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        this.addLayer((RenderLayer)new ArrowLayer(context, (LivingEntityRenderer)this));
        this.addLayer((RenderLayer)new CustomHeadLayer((RenderLayerParent)this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer((RenderLayer)new ElytraLayer((RenderLayerParent)this, context.getModelSet()));
        this.addLayer((RenderLayer)new BeeStingerLayer((LivingEntityRenderer)this));
        if (ModList.get().isLoaded("travelersbackpack") && ModList.get().isLoaded("curios")) {
            this.addLayer(new HumanBackpackLayer((RenderLayerParent<Human, PlayerModel<Human>>)this));
        }
    }

    @Nullable
    protected RenderType getRenderType(Human p_115322_, boolean p_115323_, boolean p_115324_, boolean p_115325_) {
        return RenderType.entityTranslucent((ResourceLocation)p_115322_.getResourceLocation());
    }

    public Vec3 getRenderOffset(Human p_117785_, float p_117786_) {
        return p_117785_.isCrouching() ? new Vec3(0.0, -0.125, 0.0) : super.getRenderOffset(p_117785_, p_117786_);
    }

    protected void scale(Human p_117798_, PoseStack p_117799_, float p_117800_) {
        p_117799_.scale(0.9375f, 0.9375f, 0.9375f);
    }

    protected void renderNameTag(Human human, Component p_117809_, PoseStack p_117810_, MultiBufferSource p_117811_, int p_117812_) {
        if (human.hasCustomName() && !human.getCustomName().getString().isEmpty()) {
            super.renderNameTag(human, p_117809_, p_117810_, p_117811_, p_117812_);
        }
    }

    protected void setupRotations(Human p_117802_, PoseStack p_117803_, float p_117804_, float p_117805_, float p_117806_) {
        float stanceYaw = ((HumanPlayerModel)this.model).updateStance(p_117802_, p_117806_);
        float f = p_117802_.getSwimAmount(p_117806_);
        if (p_117802_.isFallFlying()) {
            super.setupRotations(p_117802_, p_117803_, p_117804_, p_117805_, p_117806_);
            float f1 = (float)p_117802_.getFallFlyingTicks() + p_117806_;
            float f2 = Mth.clamp((float)(f1 * f1 / 100.0f), (float)0.0f, (float)1.0f);
            if (!p_117802_.isAutoSpinAttack()) {
                p_117803_.mulPose(Axis.XP.rotationDegrees(f2 * (-90.0f - p_117802_.getXRot())));
            }
            Vec3 vec3 = p_117802_.getViewVector(p_117806_);
            Vec3 vec31 = p_117802_.getDeltaMovement();
            double d0 = vec31.horizontalDistanceSqr();
            double d1 = vec3.horizontalDistanceSqr();
            if (d0 > 0.0 && d1 > 0.0) {
                double d2 = (vec31.x * vec3.x + vec31.z * vec3.z) / Math.sqrt(d0 * d1);
                double d3 = vec31.x * vec3.z - vec31.z * vec3.x;
                p_117803_.mulPose(Axis.YP.rotation((float)(Math.signum(d3) * Math.acos(d2))));
            }
        } else if (f > 0.0f) {
            super.setupRotations(p_117802_, p_117803_, p_117804_, p_117805_, p_117806_);
            float f3 = p_117802_.isInWater() ? -90.0f - p_117802_.getXRot() : -90.0f;
            float f4 = Mth.lerp((float)f, (float)0.0f, (float)f3);
            p_117803_.mulPose(Axis.XP.rotationDegrees(f4));
            if (p_117802_.isVisuallySwimming()) {
                p_117803_.translate(0.0, -1.0, (double)0.3f);
            }
        } else {
            super.setupRotations(p_117802_, p_117803_, p_117804_, p_117805_, p_117806_);
        }
        // Turn the model as one player-like unit: shoulders, torso, legs,
        // armor and held items share the same smoothly interpolated yaw.
        p_117803_.mulPose(Axis.YP.rotationDegrees(-stanceYaw));
        BetterCombatNpcAnimator.applyBodyTransform(p_117802_, p_117803_, p_117806_);
    }

    @NotNull
    public ResourceLocation getTextureLocation(Human entity) {
        return entity.getResourceLocation();
    }

    public void render(Human human, float pEntityYaw, float pPartialTicks, @NotNull PoseStack pMatrixStack, @NotNull MultiBufferSource pBuffer, int pPackedLight) {
        PlayerModel<Human> playerModel = this.model;
        playerModel.crouching = human.isCrouching();
        HumanoidModel.ArmPose mainPose = getArmPose(human, InteractionHand.MAIN_HAND);
        HumanoidModel.ArmPose offPose = getArmPose(human, InteractionHand.OFF_HAND);
        if (mainPose.isTwoHanded() && offPose != HumanoidModel.ArmPose.BLOCK) {
            offPose = human.getOffhandItem().isEmpty()
                    ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        }
        if (human.getMainArm() == HumanoidArm.RIGHT) {
            playerModel.rightArmPose = mainPose;
            playerModel.leftArmPose = offPose;
        } else {
            playerModel.leftArmPose = mainPose;
            playerModel.rightArmPose = offPose;
        }
        super.render(human, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    private static HumanoidModel.ArmPose getArmPose(Human human, InteractionHand hand) {
        ItemStack stack = human.getItemInHand(hand);
        if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
        if (human.isUsingItem() && human.getUsedItemHand() == hand
                && human.getUseItemRemainingTicks() > 0) {
            UseAnim use = stack.getUseAnimation();
            if (use == UseAnim.BLOCK || stack.getItem().canPerformAction(stack, ToolActions.SHIELD_BLOCK)) {
                return HumanoidModel.ArmPose.BLOCK;
            }
            if (use == UseAnim.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (use == UseAnim.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            if (use == UseAnim.SPEAR) return HumanoidModel.ArmPose.THROW_SPEAR;
            if (use == UseAnim.SPYGLASS) return HumanoidModel.ArmPose.SPYGLASS;
            if (use == UseAnim.TOOT_HORN) return HumanoidModel.ArmPose.TOOT_HORN;
            if (use == UseAnim.BRUSH) return HumanoidModel.ArmPose.BRUSH;
        }
        if (hand == InteractionHand.MAIN_HAND
                && club.someoneice.humangunner.GunSupport.get().isGun(stack)) {
            // TaCZ's legacy two-handed biped pose is disabled for Humans. The
            // NPC Player Animator adapter owns its motion when assets exist;
            // use the neutral item pose rather than reintroducing the old pose
            // as a fallback when a gun has no third-person animation asset.
            return HumanoidModel.ArmPose.ITEM;
        }
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)
                && !human.swinging) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

}

