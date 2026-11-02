package dev.felix.hostilehumans.compat.tacz;

import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.ReloadKey;
import com.tacz.guns.client.input.ShootKey;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

/** Read authored player bindings without invoking any real-player gun operator. */
public final class TaczMonitorInput {
    private TaczMonitorInput() {}
    public static boolean attackDown() { return ShootKey.SHOOT_KEY.isDown(); }
    public static boolean aimDown() { return AimKey.AIM_KEY.isDown(); }
    public static boolean reloadDown() { return ReloadKey.RELOAD_KEY.isDown(); }
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(TaczMonitorInput::key);
        MinecraftForge.EVENT_BUS.addListener(TaczMonitorInput::mouse);
    }
    private static boolean accepts() {
        return Minecraft.getInstance().getCameraEntity() instanceof Human human && HumanMonitorClient.takingOver(human)
                && GunSupport.isTaczGunItem(human.getMainHandItem());
    }
    private static void key(InputEvent.Key event) {
        if (!accepts() || event.getAction() != GLFW.GLFW_PRESS) return;
        HumanMonitorClient.queueWeaponInput(ShootKey.SHOOT_KEY.matches(event.getKey(), event.getScanCode()),
                AimKey.AIM_KEY.matches(event.getKey(), event.getScanCode()), ReloadKey.RELOAD_KEY.matches(event.getKey(), event.getScanCode()));
    }
    private static void mouse(InputEvent.MouseButton.Post event) {
        if (!accepts() || event.getAction() != GLFW.GLFW_PRESS) return;
        HumanMonitorClient.queueWeaponInput(ShootKey.SHOOT_KEY.matchesMouse(event.getButton()),
                AimKey.AIM_KEY.matchesMouse(event.getButton()), ReloadKey.RELOAD_KEY.matchesMouse(event.getButton()));
    }
}
