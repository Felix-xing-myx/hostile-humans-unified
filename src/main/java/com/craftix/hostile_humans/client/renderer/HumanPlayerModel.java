package com.craftix.hostile_humans.client.renderer;

import club.someoneice.humangunner.GunSupport;
import club.someoneice.humangunner.RangedWeaponCustody;
import com.craftix.hostile_humans.HumanUtil;
import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/** Smooth the whole ranged stance while leaving combat movement untouched. */
final class HumanPlayerModel extends PlayerModel<Human> {
    private static final float MAX_STANCE_TURN_DEGREES = 45.0F;
    private static final float STANCE_RESPONSE_TICKS = 2.5F;
    private static final double MIN_STEP_DISTANCE_SQR = 0.0004D;
    private static final double MAX_STEP_DISTANCE_SQR = 0.49D;
    private final Map<Human, StanceState> stances = new WeakHashMap<>();
    private float renderStanceYawDegrees;

    private static final class StanceState {
        private float yawDegrees;
        private double lastAge;

        private StanceState(double age) {
            lastAge = age;
        }
    }

    HumanPlayerModel(ModelPart root) {
        super(root, false);
    }

    float updateStance(Human human, float partialTick) {
        renderStanceYawDegrees = 0.0F;
        if (!human.isAlive() || !isRangedStance(human)
                || human.isVisuallySwimming() || human.isFallFlying()) {
            // In particular, a ranged-to-melee weapon swap must never retain
            // the old sidestep pose while melee animation takes over.
            stances.remove(human);
            return 0.0F;
        }

        double age = human.tickCount + partialTick;
        StanceState state = stances.computeIfAbsent(human, ignored -> new StanceState(age));
        double elapsed = age - state.lastAge;
        if (elapsed < 0.0D || elapsed > 10.0D) {
            state.yawDegrees = 0.0F;
            elapsed = 0.0D;
        }
        state.lastAge = age;

        float targetYaw = 0.0F;
        double dx = human.getX() - human.xOld;
        double dz = human.getZ() - human.zOld;
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr >= MIN_STEP_DISTANCE_SQR && distanceSqr <= MAX_STEP_DISTANCE_SQR) {
            float bodyYaw = Mth.rotLerp(partialTick, human.yBodyRotO, human.yBodyRot);
            float moveYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float relativeMoveYaw = Mth.wrapDegrees(moveYaw - bodyYaw);
            float stepWeight = Mth.clamp((float) Math.sqrt(distanceSqr) * 8.0F, 0.0F, 1.0F);
            // Side steps turn the stance; forward and backward steps do not.
            targetYaw = MAX_STANCE_TURN_DEGREES
                    * Mth.sin((float) Math.toRadians(relativeMoveYaw)) * stepWeight;
        }
        float blend = 1.0F - (float) Math.exp(-elapsed / STANCE_RESPONSE_TICKS);
        state.yawDegrees = Mth.lerp(blend, state.yawDegrees, targetYaw);
        renderStanceYawDegrees = Mth.clamp(state.yawDegrees,
                -MAX_STANCE_TURN_DEGREES, MAX_STANCE_TURN_DEGREES);
        return renderStanceYawDegrees;
    }

    @Override
    public void setupAnim(Human human, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // The renderer turns the complete model, including shoulders and
        // armor. Counter that turn only in the head/weapon aim animation.
        float aimYaw = Mth.clamp(netHeadYaw - renderStanceYawDegrees, -85.0F, 85.0F);
        super.setupAnim(human, limbSwing, limbSwingAmount, ageInTicks, aimYaw, headPitch);
    }

    private static boolean isRangedStance(Human human) {
        ItemStack mainHand = human.getMainHandItem();
        if (HumanUtil.isMeleeWeapon(mainHand)) return false;
        if (RangedWeaponCustody.isBowOrCrossbow(mainHand)
                || mainHand.getItem() instanceof TridentItem
                || GunSupport.get().isGun(mainHand)) return true;
        ItemStack offhand = human.getOffhandItem();
        return mainHand.isEmpty() && (RangedWeaponCustody.isBowOrCrossbow(offhand)
                || offhand.getItem() instanceof TridentItem);
    }
}
