package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import top.ribs.scguns.client.handler.GunRenderingHandler;
import top.ribs.scguns.client.handler.DualWieldShotTracker;
import top.ribs.scguns.common.GripType;
import top.ribs.scguns.item.GunItem;

/** Optional implementation loaded only for native Scorched Guns items on the physical client. */
@OnlyIn(Dist.CLIENT)
final class ScgunsNativePose {
    private static final java.util.Map<Human, Float> LAST_FLASH = new java.util.WeakHashMap<>();
    private ScgunsNativePose() { }

    static void forget(Human human) {
        if (LAST_FLASH.remove(human) != null) DualWieldShotTracker.get().clearShotCount(human.getId());
    }

    static void apply(Human human, PlayerModel<Human> model, float limbSwing, float limbSwingAmount) {
        ItemStack stack = human.getMainHandItem();
        GunItem item = (GunItem) stack.getItem();
        GripType grip = item.getModifiedGun(stack).getGeneral().getGripType(stack);
        boolean right = human.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = right ? model.rightArm : model.leftArm;
        ModelPart support = right ? model.leftArm : model.rightArm;
        float side = right ? 1 : -1;
        // Native player pose callbacks require Player and read local input. Adapt grip metadata
        // to NPC model parts instead, using this entity's head aim and dominant hand.
        if (grip.equals(GripType.DUAL_WIELD)) {
            aim(main, model.head, 0);
            aim(support, model.head, 0);
        } else if (grip.equals(GripType.ONE_HANDED) || grip.equals(GripType.ONE_HANDED_2)) {
            aim(main, model.head, -.05F * side);
            // Undo the native HumanoidModel Mixin's generic two-handed pose for the free arm.
            support.xRot = Mth.cos(limbSwing * .6662F + (right ? 0 : (float) Math.PI)) * limbSwingAmount;
            support.yRot = support.zRot = 0;
        } else if (grip.equals(GripType.BAZOOKA)) {
            aim(main, model.head, -.15F * side);
            aim(support, model.head, .45F * side);
            main.y = support.y = 1.0F;
        } else if (grip.equals(GripType.MINI_GUN) || grip.equals(GripType.MINI_GUN_2)
                || grip.equals(GripType.MINI_GUN_3) || grip.equals(GripType.MINI_GUN_4)
                || grip.equals(GripType.MINI_GUN_5)) {
            aim(main, model.head, -.15F * side);
            aim(support, model.head, .55F * side);
            main.xRot += .45F;
            support.xRot += .35F;
        } else {
            // Two-handed rifle/shotgun/SMG and future unknown grips retain a safe hold.
            AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm, model.head, right);
        }
    }

    private static void aim(ModelPart arm, ModelPart head, float yaw) {
        arm.xRot = -(float) Math.PI / 2 + head.xRot;
        arm.yRot = head.yRot + yaw;
        arm.zRot = 0;
    }

    static void render(Human human, ItemStack stack, ItemDisplayContext context,
                       PoseStack poses, MultiBufferSource buffers, int light) {
        GunRenderingHandler renderer = GunRenderingHandler.get();
        // The native NPC shot API sends flash packets but not the player's dual-shot count.
        // Each received flash installs a new boxed random token; compare identity so repeated
        // render passes neither advance the barrel nor lose equal-valued consecutive tokens.
        int id = human.getId();
        Float token = GunRenderingHandler.entityIdToRandomValue.get(id);
        GripType grip = ((GunItem) stack.getItem()).getModifiedGun(stack).getGeneral().getGripType(stack);
        if (grip.equals(GripType.DUAL_WIELD) && GunRenderingHandler.entityIdForMuzzleFlash.contains(id) && token != null
                && LAST_FLASH.get(human) != token) {
            LAST_FLASH.put(human, token);
            DualWieldShotTracker tracker = DualWieldShotTracker.get();
            tracker.incrementShotCount(id);
            // Native drawMuzzleFlash reads this tracker for the alternate barrel offset.
        }
        renderer.applyWeaponScale(stack, poses);
        // The native item-stack renderer substitutes Minecraft.player. Pass the actual NPC
        // to the public LivingEntity renderer so models/attachments use the right entity.
        // Native dual-wield models already contain both guns: render the stack exactly once.
        renderer.renderWeapon(human, stack, context, poses, buffers, light,
                Minecraft.getInstance().getFrameTime());
    }
}
