package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client presentation offset; never changes entity eye height, collision or firing origins. */
public final class HumanSpectatorCamera {
    private static final double HEIGHT_OFFSET = 0.12;
    private static final double SURFACE_MARGIN = 0.02;

    private HumanSpectatorCamera() {}

    public static Vec3 position(Camera camera, BlockGetter world, Entity entity, boolean detached) {
        return position(camera, world, entity, detached, 1);
    }
    public static Vec3 position(Camera camera, BlockGetter world, Entity entity, boolean detached, float partialTick) {
        Vec3 start = camera.getPosition();
        var client = Minecraft.getInstance();
        if (detached || client.player == null
                || !(entity instanceof Human human) || !HumanCameraView.attached(human) || !human.isAlive() || human.isRemoved()
                || human.isSleeping() || human.level() != client.level) return start;
        Vec3 predicted = dev.felix.hostilehumans.monitor.client.HumanMonitorClient.predictedEye(human, partialTick);
        if (predicted != null) start = predicted;
        Vec3 end = start.add(0, HEIGHT_OFFSET, 0);
        var hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, human));
        if (hit.getType() == HitResult.Type.MISS) return end;
        double available = Math.max(0, Math.min(HEIGHT_OFFSET, hit.getLocation().y - start.y - SURFACE_MARGIN));
        return start.add(0, available, 0);
    }
}
