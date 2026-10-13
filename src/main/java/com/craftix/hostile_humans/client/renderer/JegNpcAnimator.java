package com.craftix.hostile_humans.client.renderer;

import club.someoneice.humangunner.GunSupport;
import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Optional client boundary. Rendering owns no server weapon/ammo state. */
@OnlyIn(Dist.CLIENT)
public final class JegNpcAnimator {
    private static final ThreadLocal<Human> DRAWING = new ThreadLocal<>();
    private static boolean warned;
    private JegNpcAnimator() {}
    public static Human drawing() { return DRAWING.get(); }
    static float bodyOffset(Human human) {
        return GunSupport.isJegGunItem(human.getMainHandItem()) && !human.isUsingItem()
                ? JegNativePose.bodyOffset(human) : 0;
    }
    static void apply(Human human, PlayerModel<Human> model) {
        if (!GunSupport.isJegGunItem(human.getMainHandItem()) || human.isUsingItem() || !human.isAlive()
                || human.isVisuallySwimming() || human.isSleeping() || human.isFallFlying()) return;
        JegNativePose.apply(human, model);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }
    static boolean render(Human human, ItemStack stack, ItemDisplayContext context,
                          PoseStack poses, MultiBufferSource buffers, int light) {
        if (!GunSupport.isJegGunItem(stack)) return false;
        PoseStack isolated = new PoseStack();
        isolated.last().pose().set(poses.last().pose());
        isolated.last().normal().set(poses.last().normal());
        Human previous = DRAWING.get();
        DRAWING.set(human);
        try { return JegNativePose.render(human, stack, context, isolated, buffers, light); }
        catch (RuntimeException | LinkageError error) {
            if (!warned) {
                warned = true;
                com.mojang.logging.LogUtils.getLogger().warn("Cannot draw native JEG NPC gun; using item fallback", error);
            }
            return false;
        } finally { if (previous == null) DRAWING.remove(); else DRAWING.set(previous); }
    }
}
