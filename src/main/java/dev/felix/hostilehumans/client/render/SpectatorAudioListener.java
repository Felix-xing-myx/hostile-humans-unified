package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Listener-position boundary for integrations which attenuate before the vanilla sound engine. */
public final class SpectatorAudioListener {
    private SpectatorAudioListener() {}

    public static double distanceSquared(SoundInstance source, double original) {
        return distanceSquared(new Vec3(source.getX(), source.getY(), source.getZ()), original);
    }

    public static double distanceSquared(Entity source, double original) {
        return distanceSquared(source.getEyePosition(0), original);
    }

    private static double distanceSquared(Vec3 source, double original) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null
                || !(client.getCameraEntity() instanceof Human observed) || !HumanCameraView.attached(observed)
                || observed.level() != client.level || !observed.isAlive() || observed.isRemoved()) return original;
        var camera = client.gameRenderer.getMainCamera();
        // A sound packet may arrive before the first render after switching cameras.
        Vec3 listener = camera.getEntity() == observed ? camera.getPosition() : observed.getEyePosition();
        double distance = listener.distanceToSqr(source);
        return Double.isFinite(distance) && distance >= 0 ? distance : original;
    }
}
