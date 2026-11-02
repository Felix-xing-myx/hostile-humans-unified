package dev.felix.hostilehumans.client.render.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.felix.hostilehumans.client.render.HumanCameraView;
import dev.felix.hostilehumans.client.render.HumanSpectatorPresentation;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Optional dependency boundary. This class has no TaCZ types in its descriptors. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class TaczSpectatorFirstPerson {
    private static boolean used;
    private TaczSpectatorFirstPerson() {}

    /** Also used by the optional native FOV hook; no TaCZ class is resolved without the mod gate. */
    public static boolean ownsCameraFov() {
        var client = Minecraft.getInstance();
        return ModList.get().isLoaded("tacz") && client.getCameraEntity() instanceof Human human
                && HumanCameraView.attached(human) && human.isAlive() && !human.isRemoved()
                && !human.isSleeping() && !human.isInvisible() && HumanSpectatorPresentation.allowsNativeGunPose(human)
                && client.options.getCameraType().isFirstPerson() && TaczNativeOptics.isGun(human.getMainHandItem());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void cameraFov(ViewportEvent.ComputeFov event) {
        if (!ownsCameraFov() || !(event.getCamera().getEntity() instanceof Human human)) return;
        try { used = true; TaczNativeOptics.fov(human, event); }
        catch (RuntimeException | LinkageError failure) {
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce(
                    "TaCZ spectator optics", "Native optic FOV unavailable; retaining camera FOV", failure);
        }
    }

    /**
     * Call before the old spectator frame transform, from AFTER_ENTITIES. The incoming stack is
     * camera-relative with world camera rotation; the backend owns its camera-local projection frame.
     * Returns true only after drawing the complete native gun and both NPC arms. False requests fallback.
     */
    public static boolean tryRender(Human human, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        if (!ModList.get().isLoaded("tacz") || !HumanCameraView.attached(human)
                || !human.isAlive() || human.isRemoved() || human.isInvisible() || human.isSleeping()
                || !HumanSpectatorPresentation.allowsNativeGunPose(human)
                || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return false;
        used = true;
        return TaczNativePresentation.render(human, partialTick, poses, buffers, light);
    }

    /** Same native zoom ratio as a TaCZ player, applied only to the controlled NPC view. */
    public static double lookScale(Human human) {
        if (!ownsCameraFov() || Minecraft.getInstance().getCameraEntity() != human) return 1;
        try {
            used = true;
            return TaczNativeOptics.lookScale(human, Minecraft.getInstance().getFrameTime());
        } catch (RuntimeException | LinkageError failure) {
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce(
                    "TaCZ monitor sensitivity", "Native zoom sensitivity unavailable; retaining mouse response", failure);
            return 1;
        }
    }

    public static void activate() {
        if(ModList.get().isLoaded("tacz"))TaczMonitorRecoil.install();
    }
    public static void updateRecoil(Human human) {
        if(ModList.get().isLoaded("tacz"))TaczMonitorRecoil.update(human);
    }
    public static void clear() {
        if(ModList.get().isLoaded("tacz"))TaczMonitorRecoil.clear();
        if (used) { TaczNativePresentation.clear(); TaczNativeOptics.clearAim(); }
        used = false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !used) return;
        var camera = Minecraft.getInstance().getCameraEntity();
        if (!(camera instanceof Human human) || !HumanCameraView.attached(human)
                || !human.isAlive() || human.isRemoved() || !ownsCameraFov()) clear();
    }

    @Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        private Reload() {}
        @SubscribeEvent
        public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) manager -> {
                clear();
                if(ModList.get().isLoaded("tacz"))dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.clear();
            });
        }
    }
}
