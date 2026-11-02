package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.Minecraft;

/** Shared presentation permission: native spectator attachment or a server-authorized creative monitor. */
public final class HumanCameraView {
    private HumanCameraView() {}
    public static boolean attached(Human human) {
        var client = Minecraft.getInstance();
        return client.player != null && client.level == human.level() && client.getCameraEntity() == human
                && (client.player.isSpectator() || HumanMonitorClient.viewing(human));
    }
}
