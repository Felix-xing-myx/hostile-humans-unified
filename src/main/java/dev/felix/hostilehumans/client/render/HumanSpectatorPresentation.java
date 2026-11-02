package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/** Presentation rules for the isolated spectator pass; never changes inventory or action state. */
public final class HumanSpectatorPresentation {
    private static final double VIRTUAL_EYE_HEIGHT = 1.53;
    private static final float ARM_SCALE = 0.85F;
    private static final double FORWARD = 0.36;
    private static final double SCREEN_DOWN = 0.16;

    private HumanSpectatorPresentation() {}

    /** Idle inventory contents never take ownership from a native two-handed gun presentation.
     * Real use and synchronized shield defense own the arms, including packet-order gaps. */
    public static boolean allowsNativeGunPose(Human human) {
        return allowsNativeGunPose(human.isUsingItem(), HumanShieldPresentation.activeHand(human) != null);
    }

    static boolean allowsNativeGunPose(boolean usingItem, boolean blocking) {
        return !usingItem && !blocking;
    }

    static void frame(PoseStack poses, float pitch, float yaw) {
        // Work in the camera's forward/up frame, so the offset remains down on screen
        // while looking up/down. Scale around a normalized eye, not the entity's feet.
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 up = Vec3.directionFromRotation(pitch - 90, yaw);
        Vec3 offset = forward.scale(FORWARD).subtract(up.scale(SCREEN_DOWN));
        poses.translate(offset.x, offset.y, offset.z);
        poses.scale(ARM_SCALE, ARM_SCALE, ARM_SCALE);
        poses.translate(0, -VIRTUAL_EYE_HEIGHT, 0);
    }

    /** First-person arms follow the local view without writing into the tracked entity outside this draw. */
    static void withControlView(Human human, Runnable draw) {
        if (!dev.felix.hostilehumans.monitor.client.HumanMonitorClient.takingOver(human)) { draw.run(); return; }
        float yaw=human.getYRot(), oldYaw=human.yRotO, pitch=human.getXRot(), oldPitch=human.xRotO;
        float head=human.getYHeadRot(), oldHead=human.yHeadRotO, body=human.yBodyRot, oldBody=human.yBodyRotO;
        try {
            float viewYaw=dev.felix.hostilehumans.monitor.client.HumanMonitorClient.viewYaw();
            float viewPitch=dev.felix.hostilehumans.monitor.client.HumanMonitorClient.viewPitch();
            human.setYRot(viewYaw); human.yRotO=viewYaw; human.setXRot(viewPitch); human.xRotO=viewPitch;
            human.setYHeadRot(viewYaw); human.yHeadRotO=viewYaw; human.yBodyRot=viewYaw; human.yBodyRotO=viewYaw;
            draw.run();
        } finally {
            human.setYRot(yaw); human.yRotO=oldYaw; human.setXRot(pitch); human.xRotO=oldPitch;
            human.setYHeadRot(head); human.yHeadRotO=oldHead; human.yBodyRot=body; human.yBodyRotO=oldBody;
        }
    }

    static boolean showOffhandItem(Human human) {
        boolean ranged = GunSupport.isGunItem(human.getMainHandItem())
                || RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem());
        return showOffhandItem(ranged,
                HumanShieldPresentation.activeHand(human) == InteractionHand.OFF_HAND,
                human.isUsingItem() && human.getUsedItemHand() == InteractionHand.OFF_HAND);
    }

    static boolean showOffhandItem(boolean ranged, boolean blockingOffhand, boolean usingOffhand) {
        return !ranged || blockingOffhand || usingOffhand;
    }
}
