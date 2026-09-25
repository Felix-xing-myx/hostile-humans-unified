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
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
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
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        this.addLayer((RenderLayer)new HumanoidArmorLayer((RenderLayerParent)this, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
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
    }

    @NotNull
    public ResourceLocation getTextureLocation(Human entity) {
        return entity.getResourceLocation();
    }

    public void render(Human human, float pEntityYaw, float pPartialTicks, @NotNull PoseStack pMatrixStack, @NotNull MultiBufferSource pBuffer, int pPackedLight) {
        ItemStack stack2;
        ((PlayerModel)this.model).leftArmPose = HumanoidModel.ArmPose.EMPTY;
        ((PlayerModel)this.model).rightArmPose = HumanoidModel.ArmPose.EMPTY;
        ItemStack stack = human.getMainHandItem();
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof CrossbowItem) {
                if (human.isChargingCrossbow()) {
                    this.setHandPose(human, HumanoidModel.ArmPose.CROSSBOW_CHARGE);
                } else {
                    this.setHandPose(human, HumanoidModel.ArmPose.CROSSBOW_HOLD);
                }
            } else if (stack.getItem() instanceof BowItem && human.isAggressive()) {
                this.setHandPose(human, HumanoidModel.ArmPose.BOW_AND_ARROW);
            } else if (stack.getItem() instanceof TridentItem && human.isUsingItem() && human.getUseItemRemainingTicks() > 10) {
                this.setHandPose(human, HumanoidModel.ArmPose.THROW_SPEAR);
            } else {
                this.setHandPose(human, HumanoidModel.ArmPose.ITEM);
            }
        }
        if (!(stack2 = human.getOffhandItem()).isEmpty()) {
            if (stack2.getItem().canPerformAction(human.getOffhandItem(), ToolActions.SHIELD_BLOCK)) {
                if (human.isBlocking()) {
                    this.setOffHandPose(human, HumanoidModel.ArmPose.BLOCK);
                }
            } else {
                this.setOffHandPose(human, HumanoidModel.ArmPose.ITEM);
            }
        }
        if (club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
            setHandPose(human, HumanoidModel.ArmPose.BOW_AND_ARROW);
        }
        super.render(human, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    private void setHandPose(Human entity, HumanoidModel.ArmPose pose) {
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            ((PlayerModel)this.model).rightArmPose = pose;
        } else {
            ((PlayerModel)this.model).leftArmPose = pose;
        }
    }

    private void setOffHandPose(Human entity, HumanoidModel.ArmPose pose) {
        if (entity.getMainArm() != HumanoidArm.RIGHT) {
            ((PlayerModel)this.model).rightArmPose = pose;
        } else {
            ((PlayerModel)this.model).leftArmPose = pose;
        }
    }
}

