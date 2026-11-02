package dev.felix.hostilehumans.client.render.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.RecoilModifier;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * Native recoil math with observation-owned state; never touches LocalPlayer or native global
 * recoil.
 */
final class TaczMonitorRecoil {
    private static final NativeRecoilTrack TRACK = new NativeRecoilTrack();
    private static NativeFrameClock clock = new NativeFrameClock();
    private static Human subject;
    private static ResourceLocation gunId;
    private static boolean listening;

    private TaczMonitorRecoil() {}

    static void install() {
        if (listening) return;
        // Remote native discharge packets publish CLIENT GunFireEvent, once per burst cycle, like
        // native player recoil.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TaczMonitorRecoil::shot);
        listening = true;
    }

    static void clear() {
        subject = null;
        gunId = null;
        TRACK.clear();
        clock = new NativeFrameClock();
    }

    private static double now() {
        return clock.sample(System.nanoTime(), Minecraft.getInstance().isPaused());
    }

    private static void shot(GunFireEvent event) {
        if (!event.getLogicalSide().isClient()
                || !(event.getShooter() instanceof Human human)
                || !HumanMonitorClient.takingOver(human)
                || Minecraft.getInstance().getCameraEntity() != human) return;
        var held = human.getMainHandItem();
        var gun = IGun.getIGunOrNull(held);
        var fired = IGun.getIGunOrNull(event.getGunItemStack());
        if (gun == null
                || fired == null
                || !gun.getGunId(held).equals(fired.getGunId(event.getGunItemStack()))) return;
        try {
            if (subject != human || !gun.getGunId(held).equals(gunId)) clear();
            // Finish the previous continuous-time sample before replacing its native shot curves.
            update(human);
            var index = TimelessAPI.getClientGunIndex(gun.getGunId(held)).orElse(null);
            if (index == null
                    || index.getGunData() == null
                    || index.getGunData().getRecoil() == null) return;
            var data = index.getGunData();
            var cache = new AttachmentCacheProperty();
            cache.eval(held, data);
            ParameterizedCachePair<Float, Float> recoil = cache.getCache(RecoilModifier.ID);
            if (recoil == null) return;
            float aim = IGunOperator.fromLivingEntity(human).getSynAimingProgress();
            float zoom = Math.max(1, gun.getAimingZoom(held));
            float factor = 1 - aim + aim / (float) Math.min(Math.sqrt(zoom), 1.5);
            if (!human.isInWater() && human.getPose() == Pose.SWIMMING)
                factor *= data.getCrawlRecoilMultiplier();
            var pitch =
                    NativeRecoilCurve.create(
                            data.getRecoil(),
                            "genPitchSplineFunction",
                            (float) recoil.left().eval(factor));
            var yaw =
                    NativeRecoilCurve.create(
                            data.getRecoil(),
                            "genYawSplineFunction",
                            (float) recoil.right().eval(factor));
            subject = human;
            gunId = gun.getGunId(held);
            TRACK.shot(now(), pitch, yaw);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            clear();
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce(
                    "TaCZ monitor recoil",
                    "Native recoil unavailable for this observed gun",
                    failure);
        }
    }

    static void update(Human human) {
        if (subject == null) return;
        var gun = IGun.getIGunOrNull(human.getMainHandItem());
        if (subject != human
                || !HumanMonitorClient.takingOver(human)
                || gun == null
                || !gun.getGunId(human.getMainHandItem()).equals(gunId)) {
            clear();
            return;
        }
        try {
            var delta = TRACK.sample(now());
            HumanMonitorClient.recoil(human, delta.yaw(), delta.pitch());
        } catch (RuntimeException | LinkageError failure) {
            clear();
        }
    }
}
