package dev.felix.hostilehumans.faction.visual;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.TierThreeHuman;
import dev.felix.hostilehumans.faction.HumanFactions;

import dev.felix.hostilehumans.entity.HumanTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;

/** Shared arm/leg geometry attached directly to the animated parent limbs, outside vanilla armor. */
public final class FactionVisualLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation HIRED = new ResourceLocation(HumanRuntime.MOD_ID, "textures/entity/faction_band/hired.png");
    private final boolean player, slim;
    public FactionVisualLayer(RenderLayerParent<T, M> parent, boolean player, boolean slim) {
        super(parent); this.player = player; this.slim = slim;
    }
    @Override public void render(PoseStack poses, MultiBufferSource buffers, int light, T entity, float limbSwing,
                                 float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (entity.isInvisible() || entity.isSpectator()) return;
        String faction; int grade; boolean hired = false;
        if (player) {
            if (!FactionVisualClient.playersVisible()) return;
            var selected = FactionVisualClient.player(entity.getUUID());
            if (selected == null || selected.grade() == 0) return;
            faction = selected.faction(); grade = selected.grade();
        } else if (entity instanceof Human human) {
            if (!FactionVisualClient.humansVisible() || human.getTier() == HumanTier.ROAMER) return;
            faction = HumanFactions.id(human);
            grade = HumanFactions.isBoss(human) ? 4 : TierThreeHuman.isTierThree(human) ? 3 : human.getTier() == HumanTier.LEVEL2 ? 2 : 1;
            hired = human.hasOwner();
        } else return;
        var definition = FactionVisualClient.definition(faction);
        if (definition != null && definition.style().equals("none")) return;
        int color = FactionVisualClient.color(faction);
        var textures = FactionBandAppearance.textures(faction, grade);
        boolean chest = armored(entity, EquipmentSlot.CHEST);
        boolean legs = armored(entity, EquipmentSlot.LEGS);
        boolean boots = armored(entity, EquipmentSlot.FEET);
        draw(poses, buffers, light, textures.cloth(), color, 0, chest, legs, boots);
        draw(poses, buffers, light, textures.trim(), 0xFFFFFF, .015F, chest, legs, boots);
        if (hired) drawHired(poses, buffers, light, chest, legs, boots);
    }
    private static boolean armored(LivingEntity entity, EquipmentSlot slot) {
        return entity.getItemBySlot(slot).getItem() instanceof net.minecraft.world.item.ArmorItem armor
                && armor.getEquipmentSlot() == slot;
    }
    /** A small tab below the hem retains employment identity without painting over rank stripes. */
    private void drawHired(PoseStack poses, MultiBufferSource buffers, int light, boolean chest, boolean legs, boolean boots) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(HIRED));
        hiredTab(poses, vertices, getParentModel().rightArm, chest ? 1.0F : .5F, 5.08F, -(chest ? 3.18F : 2.43F), light);
        hiredTab(poses, vertices, getParentModel().leftArm, chest ? -1.7F : -1.2F, 5.08F, -(chest ? 3.18F : 2.43F), light);
        hiredTab(poses, vertices, getParentModel().leftLeg, boots ? 1.8F : 1.3F, 9.08F, -(FactionBandFit.leg(legs, boots, 0).depth() + .06F), light);
    }
    private static void hiredTab(PoseStack poses, VertexConsumer vertices, ModelPart limb, float x, float y, float z, int light) {
        poses.pushPose();
        try {
            limb.translateAndRotate(poses); poses.scale(1/16F, 1/16F, 1/16F);
            face(poses,vertices,x,y,z,x+.7F,y,z,x+.7F,y+.7F,z,x,y+.7F,z,
                    0,0,-1,0xFFFFFF,light,.75F,1,5/16F,11/16F);
        } finally { poses.popPose(); }
    }
    private void draw(PoseStack poses, MultiBufferSource buffers, int light, ResourceLocation texture, int color, float offset, boolean chest, boolean legs, boolean boots) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        var arm = FactionBandFit.arm(slim, chest, offset);
        var leg = FactionBandFit.leg(legs, boots, offset);
        ring(poses, vertices, getParentModel().rightArm, arm.outer(), arm.inner(), -arm.depth(), arm.depth(), 2, 5, color, light);
        ring(poses, vertices, getParentModel().leftArm, -arm.inner(), -arm.outer(), -arm.depth(), arm.depth(), 2, 5, color, light);
        ring(poses, vertices, getParentModel().leftLeg, -leg.depth(), leg.depth(), -leg.depth(), leg.depth(), 6, 9, color, light);
    }
    private static void ring(PoseStack poses, VertexConsumer out, ModelPart limb, float x0, float x1,
                             float z0, float z1, float y0, float y1, int color, int light) {
        poses.pushPose();
        try {
            limb.translateAndRotate(poses); poses.scale(1/16F, 1/16F, 1/16F);
            face(poses,out,x0,y0,z0,x1,y0,z0,x1,y1,z0,x0,y1,z0,0,0,-1,color,light,0,.25F,0,1);
            face(poses,out,x1,y0,z1,x0,y0,z1,x0,y1,z1,x1,y1,z1,0,0,1,color,light,.25F,.5F,0,1);
            face(poses,out,x0,y0,z1,x0,y0,z0,x0,y1,z0,x0,y1,z1,-1,0,0,color,light,.5F,.75F,0,1);
            face(poses,out,x1,y0,z0,x1,y0,z1,x1,y1,z1,x1,y1,z0,1,0,0,color,light,.75F,1,0,1);
            // Narrow hem surfaces give the cloth real thickness without a solid cap across the limb.
            float t=.08F;
            for (int edge=0; edge<2; edge++) {
                float y=edge==0?y0:y1, ny=edge==0?-1:1;
                face(poses,out,x0,y,z0,x1,y,z0,x1,y,z0+t,x0,y,z0+t,0,ny,0,color,light,0,.25F,0,1/16F);
                face(poses,out,x0,y,z1-t,x1,y,z1-t,x1,y,z1,x0,y,z1,0,ny,0,color,light,0,.25F,0,1/16F);
                face(poses,out,x0,y,z0+t,x0+t,y,z0+t,x0+t,y,z1-t,x0,y,z1-t,0,ny,0,color,light,0,.25F,0,1/16F);
                face(poses,out,x1-t,y,z0+t,x1,y,z0+t,x1,y,z1-t,x1-t,y,z1-t,0,ny,0,color,light,0,.25F,0,1/16F);
            }
        } finally { poses.popPose(); }
    }
    private static void face(PoseStack poses, VertexConsumer out, float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,int color,int light,
                             float u0,float u1,float v0,float v1) {
        vertex(poses,out,ax,ay,az,u0,v0,nx,ny,nz,color,light);
        vertex(poses,out,bx,by,bz,u1,v0,nx,ny,nz,color,light);
        vertex(poses,out,cx,cy,cz,u1,v1,nx,ny,nz,color,light);
        vertex(poses,out,dx,dy,dz,u0,v1,nx,ny,nz,color,light);
    }
    private static void vertex(PoseStack poses, VertexConsumer out,float x,float y,float z,float u,float v,
                               float nx,float ny,float nz,int color,int light) {
        out.vertex(poses.last().pose(),x,y,z).color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(poses.last().normal(),nx,ny,nz).endVertex();
    }
}
