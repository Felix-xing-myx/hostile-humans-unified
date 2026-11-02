package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Arm presentation only: no fake player, action input or server state. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class HumanSpectatorView {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static WeakReference<ClientLevel> failedLevel = new WeakReference<>(null);

    private HumanSpectatorView() {}

    private static final class Buffers {
        // Items combine base geometry and glint consumers at the same time. A single
        // immediate fallback aliases both consumers and crashes with Duplicate delegates.
        // Use vanilla's complete fixed-channel layout, isolated from the level's batches.
        // Allocate once, never per frame: the builders own native memory.
        static final MultiBufferSource.BufferSource SOURCE =
                new RenderBuffers().bufferSource();
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.level == failedLevel.get()
                || client.player == null
                || !client.options.getCameraType().isFirstPerson() || event.getCamera().isDetached()
                || !(client.getCameraEntity() instanceof Human human) || !HumanCameraView.attached(human)
                || event.getCamera().getEntity() != human || human.level() != client.level
                || !human.isAlive() || human.isRemoved() || human.isSleeping() || human.isInvisible()) return;
        if (!(client.getEntityRenderDispatcher().getRenderer(human) instanceof HumanRenderer renderer)) return;

        // This is a camera-relative presentation origin, not the Human's physical feet.
        // It avoids a boss's head/shoulders crossing the near plane when its collision
        // height is intentionally smaller than its world model. World depth remains intact.
        // Item renderers can fail after pushing their own transforms. Keep those pushes
        // off the world's stack, so a failed optional pass cannot corrupt later rendering.
        PoseStack poses = new PoseStack();
        poses.last().pose().set(event.getPoseStack().last().pose());
        poses.last().normal().set(event.getPoseStack().last().normal());
        try {
            MultiBufferSource.BufferSource buffers = Buffers.SOURCE;
            Throwable renderFailure = null;
            try {
                int light=client.getEntityRenderDispatcher().getPackedLightCoords(human,event.getPartialTick());
                if(!HumanSpectatorGunView.tryRender(human,event.getPartialTick(),poses,buffers,light)) {
                    HumanSpectatorPresentation.frame(poses,event.getCamera().getXRot(),event.getCamera().getYRot());
                    renderer.renderSpectatorArms(human,event.getPartialTick(),poses,buffers,light);
                }
            } catch (RuntimeException | LinkageError failure) {
                renderFailure = failure;
                throw failure;
            } finally {
                try { buffers.endBatch(); }
                catch (RuntimeException | LinkageError flushFailure) {
                    if (renderFailure == null) throw flushFailure;
                    renderFailure.addSuppressed(flushFailure);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            // Optional presentation must not keep crashing the client on one bad item model.
            // No per-frame retry/log storm; a new client world can attempt the pass again.
            failedLevel = new WeakReference<>(client.level);
            LOGGER.error("Human spectator arms disabled for this client level after a render failure; "
                    + "vanilla spectator camera remains active", failure);
        }
    }
}
