package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
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

/** Shared arm/leg geometry attached directly to the animated parent limbs, outside vanilla armor. */
public final class FactionVisualLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation[] GRADES = new ResourceLocation[4];
    private static final ResourceLocation HIRED = new ResourceLocation(HumanGunner.MOD_ID, "textures/entity/faction_band/hired.png");
    static { for (int i = 0; i < 4; i++) GRADES[i] = new ResourceLocation(HumanGunner.MOD_ID, "textures/entity/faction_band/grade_" + (i + 1) + ".png"); }
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
        draw(poses, buffers, light, GRADES[Math.max(1, Math.min(4, grade)) - 1], color, 0);
        if (hired) draw(poses, buffers, light, HIRED, 0xFFFFFF, .02F);
    }
    private void draw(PoseStack poses, MultiBufferSource buffers, int light, ResourceLocation texture, int color, float offset) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        ring(poses, vertices, getParentModel().rightArm, slim ? -3.12F - offset : -4.12F - offset,
                2.12F + offset, -3.12F - offset, 3.12F + offset, 2, 5, color, light);
        ring(poses, vertices, getParentModel().leftArm, -2.12F - offset,
                slim ? 3.12F + offset : 4.12F + offset, -3.12F - offset, 3.12F + offset, 2, 5, color, light);
        ring(poses, vertices, getParentModel().leftLeg, -3.12F - offset, 3.12F + offset,
                -3.12F - offset, 3.12F + offset, 6, 9, color, light);
    }
    private static void ring(PoseStack poses, VertexConsumer out, ModelPart limb, float x0, float x1,
                             float z0, float z1, float y0, float y1, int color, int light) {
        poses.pushPose(); limb.translateAndRotate(poses); poses.scale(1/16F, 1/16F, 1/16F);
        face(poses, out, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, 0,0,-1,color,light);
        face(poses, out, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, 0,0,1,color,light);
        face(poses, out, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, -1,0,0,color,light);
        face(poses, out, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, 1,0,0,color,light);
        poses.popPose();
    }
    private static void face(PoseStack poses, VertexConsumer out, float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz,int color,int light) {
        vertex(poses,out,ax,ay,az,0,.25F,nx,ny,nz,color,light);
        vertex(poses,out,bx,by,bz,1,.25F,nx,ny,nz,color,light);
        vertex(poses,out,cx,cy,cz,1,.75F,nx,ny,nz,color,light);
        vertex(poses,out,dx,dy,dz,0,.75F,nx,ny,nz,color,light);
    }
    private static void vertex(PoseStack poses, VertexConsumer out,float x,float y,float z,float u,float v,
                               float nx,float ny,float nz,int color,int light) {
        out.vertex(poses.last().pose(),x,y,z).color((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(poses.last().normal(),nx,ny,nz).endVertex();
    }
}
