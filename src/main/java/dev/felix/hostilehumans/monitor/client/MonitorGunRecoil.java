package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.MonitorRecoilSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * One control-angle facade; optional adapters own native metadata, not the real player's handlers.
 */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class MonitorGunRecoil {
    private static final ArrayDeque<MonitorRecoilSnapshot> pending = new ArrayDeque<>();
    private static final NativeNpcRecoilTrack track = new NativeNpcRecoilTrack();
    private static final Random random = new Random();
    private static Human subject;
    private static UUID token;
    private static long generation, lastSequence, previousNanos;
    private static double seconds, ads;
    private static ResourceLocation weapon;

    private MonitorGunRecoil() {}

    public static void accept(MonitorRecoilSnapshot packet) {
        if (!HumanMonitorClient.active()) return;
        if (pending.size() == 32) pending.removeFirst();
        pending.addLast(packet);
    }

    private static void clearTrack() {
        track.clear();
        subject = null;
        token = null;
        weapon = null;
        generation = lastSequence = previousNanos = 0;
        seconds = ads = 0;
    }

    private static void clear() {
        clearTrack();
        pending.clear();
    }

    private static void apply(Human h, double now) {
        var delta = track.sample(now);
        HumanMonitorClient.recoil(h, delta.yaw(), delta.pitch());
    }

    public static void update(Human human, UUID lease, long leaseGeneration) {
        var client = Minecraft.getInstance();
        if (!HumanMonitorClient.takingOver(human) || client.getCameraEntity() != human) {
            clear();
            return;
        }
        // TaCZ retains its own authoritative CLIENT Fire event and native spline API.
        dev.felix.hostilehumans.client.render.tacz.TaczSpectatorFirstPerson.updateRecoil(human);
        var held = human.getMainHandItem();
        if (!(GunSupport.isScgunsGunItem(held) || GunSupport.isJegGunItem(held))) {
            clear();
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
        if (subject != human
                || !lease.equals(token)
                || generation != leaseGeneration
                || !id.equals(weapon)) {
            clearTrack();
            subject = human;
            token = lease;
            generation = leaseGeneration;
            weapon = id;
        }
        long nanos = System.nanoTime();
        double elapsed =
                previousNanos == 0 || client.isPaused()
                        ? 0
                        : Math.max(0, (nanos - previousNanos) / 1e9);
        previousNanos = nanos;
        seconds += elapsed;
        try {
            double speed =
                    GunSupport.isScgunsGunItem(held)
                            ? dev.felix.hostilehumans.compat.scguns.ScgunsMonitorRecoilAdapter
                                    .aimSpeed(held)
                            : dev.felix.hostilehumans.compat.jeg.JegMonitorRecoilAdapter.aimSpeed(
                                    held);
            boolean aiming =
                    client.screen == null
                            && client.isWindowActive()
                            && !client.isPaused()
                            && MonitorGunInput.aim(held);
            ads = NativeNpcRecoilTrack.aim(ads, elapsed, speed, aiming);
            apply(human, seconds);
            while (!pending.isEmpty()) {
                var p = pending.removeFirst();
                if (!p.token().equals(token)
                        || p.generation() != generation
                        || !p.target().equals(human.getUUID())
                        || p.entityId() != human.getId()
                        || !p.weapon().equals(weapon)
                        || p.sequence() <= lastSequence
                        || !(p.backend().equals("scguns")
                                ? GunSupport.isScgunsGunItem(held)
                                : GunSupport.isJegGunItem(held))) continue;
                lastSequence = p.sequence();
                track.shot(
                        seconds,
                        NativeNpcRecoilTrack.strength(p.amplitude(), p.adsReduction(), ads),
                        random.nextBoolean());
            }
        } catch (RuntimeException | LinkageError error) {
            clear();
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce(
                    "NPC monitor recoil",
                    "Native recoil unavailable for this controlled gun",
                    error);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var camera = Minecraft.getInstance().getCameraEntity();
        if (!(camera instanceof Human h)
                || !HumanMonitorClient.takingOver(h)
                || !h.isAlive()
                || h.isRemoved()) clear();
    }
}
