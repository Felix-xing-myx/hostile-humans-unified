package dev.felix.hostilehumans.compat.jeg;

import ttv.migami.jeg.client.KeyBinds;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;

/** Read authored player bindings without invoking any real-player gun operator. */
public final class JegMonitorInput {
    private JegMonitorInput() {}
    public static boolean attackDown() { return KeyBinds.getShootMapping().isDown(); }
    public static boolean aimDown() { return KeyBinds.getAimMapping().isDown(); }
    public static boolean reloadDown() { return KeyBinds.KEY_RELOAD.isDown(); }
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(JegMonitorInput::captureKey);
        MinecraftForge.EVENT_BUS.addListener(JegMonitorInput::captureMouse);
    }
    private static boolean accepts() {
        return Minecraft.getInstance().getCameraEntity() instanceof Human human && HumanMonitorClient.takingOver(human)
                && GunSupport.isJegGunItem(human.getMainHandItem());
    }
    public static void captureKey(InputEvent.Key event) {
        if (!accepts() || event.getAction() != GLFW.GLFW_PRESS) return;
        HumanMonitorClient.queueWeaponInput(KeyBinds.getShootMapping().matches(event.getKey(), event.getScanCode()),
                KeyBinds.getAimMapping().matches(event.getKey(), event.getScanCode()), KeyBinds.KEY_RELOAD.matches(event.getKey(), event.getScanCode()));
    }
    public static void captureMouse(InputEvent.MouseButton event) {
        if (!accepts() || event.getAction() != GLFW.GLFW_PRESS) return;
        HumanMonitorClient.queueWeaponInput(KeyBinds.getShootMapping().matchesMouse(event.getButton()),
                KeyBinds.getAimMapping().matchesMouse(event.getButton()), KeyBinds.KEY_RELOAD.matchesMouse(event.getButton()));
    }
    public static void captureInteraction() {
        if (accepts()) HumanMonitorClient.queueWeaponInput(attackDown(), aimDown(), reloadDown());
    }
}
