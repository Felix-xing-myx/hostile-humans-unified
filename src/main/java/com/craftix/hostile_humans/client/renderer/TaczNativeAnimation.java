package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.IThirdPersonAnimation;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;

/** Loaded only behind the TaCZ mod gate; no Player Animator references. */
final class TaczNativeAnimation {
    private TaczNativeAnimation() {}

    static Sample sample(Human human) {
        IGunOperator operator = IGunOperator.fromLivingEntity(human);
        ReloadState reload = operator.getSynReloadState();
        return new Sample(operator.getSynAimingProgress(),
                reload != null && reload.getStateType() != null && reload.getStateType().isReloading(),
                operator.getSynShootCoolDown(), operator.getSynDrawCoolDown(),
                operator.getSynMeleeCoolDown(), operator.getSynIsBolting());
    }

    static void apply(Human human, HumanoidModel<Human> model, Sample sample,
                      float age, int firedTick, int reloadStart) {
        // Verified TaCZ 1.1.8 parameter order: right arm, left arm, body, head.
        // Avoid a lambda whose synthetic method exposes GunDisplayInstance in
        // this class's method descriptors when optional dependencies are absent.
        var display = TimelessAPI.getGunDisplay(human.getMainHandItem()).orElse(null);
        IThirdPersonAnimation animation = ThirdPersonManager.getAnimation(
                display == null ? "default" : display.getThirdPersonAnimation());
        boolean leftHanded = human.getMainArm() == HumanoidArm.LEFT;
        var main = leftHanded ? model.leftArm : model.rightArm;
        var support = leftHanded ? model.rightArm : model.leftArm;
        if (sample.aim > 0.0F) {
            animation.animateGunAim(human, main, support, model.body, model.head, sample.aim);
        } else {
            animation.animateGunHold(human, main, support, model.body, model.head);
        }
        if (leftHanded) {
            main.yRot = -main.yRot + 2.0F * model.head.yRot;
            support.yRot = -support.yRot + 2.0F * model.head.yRot;
        }
        // These are readable procedural gestures, not a resource pack's exact
        // magazine/hand keyframes. Vanilla walking/riding legs remain intact.
        if (sample.reloading) {
            float motion = TaczAnimationPolicy.reloadMotion(age - reloadStart);
            main.xRot += 0.25F;
            support.xRot = -0.8F - motion * 0.6F + model.head.xRot;
            support.yRot = model.head.yRot + (leftHanded ? -0.5F : 0.5F);
            support.zRot = (leftHanded ? -1.0F : 1.0F) * (0.25F + motion * 0.35F);
        } else if (sample.bolting) {
            support.xRot += 0.35F * TaczAnimationPolicy.reloadMotion(age);
        } else if (sample.meleeCooldown > 0L) {
            main.xRot += 0.35F;
            support.xRot += 0.35F;
        } else if (sample.drawCooldown > 0L) {
            main.xRot += 0.45F;
            support.xRot += 0.45F;
        } else {
            float recoil = TaczAnimationPolicy.recoil(age, firedTick) * 0.12F;
            main.xRot -= recoil;
            support.xRot -= recoil;
        }
    }

    record Sample(float aim, boolean reloading, long shootCooldown, long drawCooldown,
                  long meleeCooldown, boolean bolting) {
        boolean actionActive() {
            return aim > 0.0F || reloading || bolting || drawCooldown > 0L || meleeCooldown > 0L;
        }
    }
}
