package dev.felix.hostilehumans.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.monitor.client.HumanMonitorClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** One bounded observation track, shared by world bob and optional weapon presentation. */
@Mod.EventBusSubscriber(modid = "hostile_humans_unified", value = Dist.CLIENT)
public final class HumanSpectatorMotion {
    private static Human subject;
    private static SpectatorMotionTrack track = new SpectatorMotionTrack();
    private HumanSpectatorMotion() {}

    public static SpectatorMotionTrack.Frame frame(Human human, float partialTick) {
        if (subject != human) { subject = human; track = new SpectatorMotionTrack(); }
        var point = eyePosition(human, partialTick);
        return track.sample((double) human.level().getGameTime() + partialTick, point.x, point.z,
                onGround(human, partialTick) && !human.isPassenger() && !human.isInWaterOrBubble() && !human.isFallFlying());
    }

    public static net.minecraft.world.phys.Vec3 eyePosition(Human human, float partialTick) {
        var predicted = HumanMonitorClient.predictedEye(human, partialTick);
        return predicted != null ? predicted : human.getEyePosition(partialTick);
    }

    public static boolean onGround(Human human, float partialTick) {
        Boolean predicted = HumanMonitorClient.predictedOnGround(human, partialTick);
        return predicted != null ? predicted : human.onGround();
    }

    /** Same transform as vanilla GameRenderer.bobView, using the observed Human's own gait. */
    public static void bob(PoseStack poses, float partialTick) {
        var client = Minecraft.getInstance();
        if (!client.options.bobView().get() || !client.options.getCameraType().isFirstPerson()
                || client.gameRenderer.getMainCamera().isDetached()
                || !(client.getCameraEntity() instanceof Human human) || !HumanCameraView.attached(human)
                || !human.isAlive() || human.isRemoved() || human.isSleeping()) return;
        var motion = frame(human, partialTick);
        double phase = -motion.distance() * Math.PI, amount = motion.bob();
        poses.translate(Math.sin(phase) * amount * 0.5, -Math.abs(Math.cos(phase) * amount), 0);
        poses.mulPose(Axis.ZP.rotationDegrees((float) (Math.sin(phase) * amount * 3)));
        poses.mulPose(Axis.XP.rotationDegrees((float) (Math.abs(Math.cos(phase - 0.2) * amount) * 5)));
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && subject != null
                && (!subject.isAlive() || subject.isRemoved() || !HumanCameraView.attached(subject))) {
            subject = null; track = new SpectatorMotionTrack();
        }
    }
}
