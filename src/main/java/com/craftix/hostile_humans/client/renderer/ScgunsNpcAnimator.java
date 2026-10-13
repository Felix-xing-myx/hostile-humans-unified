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
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Client-only optional boundary; never fabricates a Player or mutates equipment. */
@OnlyIn(Dist.CLIENT)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class ScgunsNpcAnimator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warned;
    private static final java.util.Map<Human, Boolean> ATTACHED = new java.util.WeakHashMap<>();

    private ScgunsNpcAnimator() { }

    public static void beginRender(Human human) { ATTACHED.remove(human); }

    /** Only suppress the estimated post-render flash after the attached native draw succeeded. */
    public static boolean consumeAttachedRender(Human human) {
        return Boolean.TRUE.equals(ATTACHED.remove(human));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onEntityLeave(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Human human) {
            ATTACHED.remove(human);
            if (net.minecraftforge.fml.ModList.get().isLoaded("scguns")) ScgunsNativePose.forget(human);
        }
    }

    static void apply(Human human, PlayerModel<Human> model, float limbSwing, float limbSwingAmount) {
        if (!GunSupport.isScgunsGunItem(human.getMainHandItem()) || !human.isAlive()
                || human.isVisuallySwimming() || human.isSleeping() || human.isFallFlying()) return;
        // Healing/drinking owns its hand animation. Parallel NPC eating does not use this flag.
        if (human.isUsingItem()) return;
        try {
            ScgunsNativePose.apply(human, model, limbSwing, limbSwingAmount);
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            net.minecraft.client.model.AnimationUtils.animateCrossbowHold(model.rightArm, model.leftArm,
                    model.head, HumanGunPresentation.mainArm(human) == net.minecraft.world.entity.HumanoidArm.RIGHT);
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    static boolean render(Human human, ItemStack item, ItemDisplayContext context,
                          PoseStack poses, MultiBufferSource buffers, int light) {
        if (!GunSupport.isScgunsGunItem(item)) return false;
        // Isolate even unbalanced native push/pop operations if an optional model throws.
        PoseStack nativePoses = new PoseStack();
        nativePoses.last().pose().set(poses.last().pose());
        nativePoses.last().normal().set(poses.last().normal());
        try {
            ScgunsNativePose.render(human, item, context, nativePoses, buffers, light);
            if (item == human.getMainHandItem()) ATTACHED.put(human, true);
            return true;
        } catch (RuntimeException | LinkageError error) {
            warn(error);
            return false;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            LOGGER.warn("Cannot apply native Scorched Guns NPC presentation; using vanilla fallback", error);
        }
    }
}
