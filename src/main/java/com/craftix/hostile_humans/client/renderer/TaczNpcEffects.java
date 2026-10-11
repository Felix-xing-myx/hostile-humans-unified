package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.SlotModel;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import it.unimi.dsi.fastutil.Pair;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Client-only TaCZ effects, scoped to one actual Human held-item render.
 * Uses upstream shoot notifications, authored flash assets and the upstream muzzle node.
 * Never sets TaCZ's global player flash timestamp/matrices or creates damage/explosions.
 */
public final class TaczNpcEffects {
    private static final Map<Human, Shot> SHOTS = new WeakHashMap<>();
    private static final SlotModel FLASH = new SlotModel(true);
    private static Context context;

    private TaczNpcEffects() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TaczNpcEffects::onShoot);
    }

    private static void onShoot(GunShootEvent event) {
        // This CLIENT event originates in TaCZ's accepted server shot packet.
        if (!event.getLogicalSide().isClient() || !(event.getShooter() instanceof Human human)) return;
        String id = gunId(event.getGunItemStack());
        if (id.isEmpty() || !id.equals(gunId(human.getMainHandItem()))) return;
        SHOTS.put(human, new Shot(id, human.tickCount, human.level().getGameTime(),
                System.nanoTime(), human.getRandom().nextFloat() * 360.0F));
    }

    static String gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null || gun.getGunId(stack) == null ? "" : gun.getGunId(stack).toString();
    }

    static int lastShotTick(Human human) {
        Shot shot = SHOTS.get(human);
        return shot != null && shot.gunId.equals(gunId(human.getMainHandItem()))
                && human.level().getGameTime() >= shot.gameTime
                && human.level().getGameTime() - shot.gameTime <= 2
                ? shot.tick : Integer.MIN_VALUE / 2;
    }

    static void reset() { SHOTS.clear(); context = null; }

    /** Caller must restore the returned scope in finally, including nested renders. */
    static Context begin(Human human, ItemStack stack, MultiBufferSource buffer) {
        Context previous = context;
        context = new Context(human, stack, buffer);
        return previous;
    }

    static void end(Context previous) {
        Context finished = context;
        context = previous;
        // Switching buffer types inside a native model delegate can flush the
        // gun's still-active VertexConsumer. Draw only after the entire item.
        if (finished != null) try {
            for (Flash pending : finished.flashes) drawFlash(finished, pending);
        } catch (RuntimeException | LinkageError error) {
            ClientAnimationCompatibility.warnOnce("TaczNpcEffects", "Could not draw NPC muzzle flash", error);
        }
    }

    /** Called at TaCZ's muzzle-node functional renderer, after all model transforms. */
    public static void renderMuzzle(PoseStack poses, ItemDisplayContext displayContext) {
        try { captureMuzzle(poses, displayContext); }
        catch (RuntimeException | LinkageError error) {
            ClientAnimationCompatibility.warnOnce("TaczNpcEffects", "Could not read TaCZ muzzle effects", error);
        }
    }

    private static void captureMuzzle(PoseStack poses, ItemDisplayContext displayContext) {
        Context current = context;
        if (current == null || OculusCompat.isRenderShadow()
                || (displayContext != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    && displayContext != ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) return;
        Human human = current.human;
        if (!human.isAlive() || current.stack != human.getMainHandItem()) return;
        Shot shot = SHOTS.get(human);
        if (shot == null || !shot.gunId.equals(gunId(current.stack))) return;
        long elapsed = System.nanoTime() - shot.nanoTime;
        if (elapsed < 0 || elapsed > 75_000_000L
                || human.level().getGameTime() < shot.gameTime
                || human.level().getGameTime() - shot.gameTime > 2) return;
        var display = TimelessAPI.getGunDisplay(current.stack).orElse(null);
        if (display == null || display.getMuzzleFlash() == null || hidesFlash(current.stack)) return;
        var flash = display.getMuzzleFlash();
        if (current.flashes.size() >= 8) return;
        PoseStack captured = new PoseStack();
        captured.last().pose().set(poses.last().pose());
        captured.last().normal().set(poses.last().normal());
        current.flashes.add(new Flash(captured, flash.getTexture(), 0.5F * flash.getScale(), shot.rotation));
    }

    private static void drawFlash(Context current, Flash flash) {
        PoseStack poses = flash.poses;
        var consumer = current.buffer.getBuffer(RenderType.entityTranslucent(flash.texture));
        poses.pushPose();
        try {
            poses.scale(flash.scale, flash.scale, flash.scale);
            poses.mulPose(Axis.ZP.rotationDegrees(flash.rotation));
            poses.translate(0, -1, 0);
            FLASH.renderToBuffer(poses, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            poses.mulPose(Axis.YP.rotationDegrees(90));
            FLASH.renderToBuffer(poses, consumer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        } finally { poses.popPose(); }
    }

    private static boolean hidesFlash(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return false;
        ItemStack muzzle = gun.getAttachment(stack, AttachmentType.MUZZLE);
        if (muzzle.isEmpty()) muzzle = gun.getBuiltinAttachment(stack, AttachmentType.MUZZLE);
        IAttachment attachment = IAttachment.getIAttachmentOrNull(muzzle);
        if (attachment == null) return false;
        var index = TimelessAPI.getCommonAttachmentIndex(attachment.getAttachmentId(muzzle)).orElse(null);
        if (index == null) return false;
        var property = index.getData().getModifier().get(SilenceModifier.ID);
        return property != null && property.getValue() instanceof Pair<?, ?> pair
                && Boolean.TRUE.equals(pair.right());
    }

    static final class Context {
        final Human human;
        final ItemStack stack;
        final MultiBufferSource buffer;
        final List<Flash> flashes = new ArrayList<>();
        Context(Human human, ItemStack stack, MultiBufferSource buffer) {
            this.human = human; this.stack = stack; this.buffer = buffer;
        }
    }
    private record Flash(PoseStack poses, net.minecraft.resources.ResourceLocation texture,
                         float scale, float rotation) {}
    private record Shot(String gunId, int tick, long gameTime, long nanoTime, float rotation) {}
}
