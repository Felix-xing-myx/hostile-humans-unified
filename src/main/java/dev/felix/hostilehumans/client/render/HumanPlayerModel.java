package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.RangedWeaponCustody;
import dev.felix.hostilehumans.entity.HumanUtil;
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
    private final ModelPart[] restParts;
    private final boolean armsOnly;
    private final SpectatorMeleePresentation spectatorMelee;
    private float renderStanceYawDegrees;

    private static final class StanceState {
        private float yawDegrees;
        private double lastAge;

        private StanceState(double age) {
            lastAge = age;
        }
    }

    HumanPlayerModel(ModelPart root) {
        this(root, false);
    }

    HumanPlayerModel(ModelPart root, boolean armsOnly) {
        super(root, false);
        this.restParts = root.getAllParts().toArray(ModelPart[]::new);
        this.armsOnly = armsOnly;
        this.spectatorMelee = armsOnly ? new SpectatorMeleePresentation() : null;
    }

    float updateStance(Human human, float partialTick, float bodyYaw) {
        renderStanceYawDegrees = 0.0F;
        if (!human.isAlive() || HumanShieldPresentation.active(human) || !isRangedStance(human)
                || human.isVisuallySwimming() || human.isFallFlying()) {
            // In particular, a ranged-to-melee weapon swap must never retain
            // the old sidestep pose while melee animation takes over.
            stances.remove(human);
            return 0.0F;
        }

        float jegBodyOffset = JegNpcAnimator.bodyOffset(human);
        if (jegBodyOffset != 0) {
            stances.remove(human);
            // Render-only native machine-gun shoulder stance. Head and item share this frame.
            renderStanceYawDegrees = Mth.wrapDegrees(Mth.rotLerp(partialTick,
                    human.yHeadRotO, human.yHeadRot) - bodyYaw) + jegBodyOffset;
            return renderStanceYawDegrees;
        }
        if (GunSupport.isScgunsGunItem(human.getMainHandItem()) && !human.isUsingItem()) {
            // Native dual guns form one rigid item model. Turn the rendered body toward
            // the aim instead of twisting that pair ninety degrees around one wrist.
            // setupAnim subtracts this same offset; entity yaw/AI remain untouched.
            stances.remove(human);
            float relativeAim = Mth.wrapDegrees(Mth.rotLerp(partialTick,
                    human.yHeadRotO, human.yHeadRot) - bodyYaw);
            float nativeShoulder = ScgunsNpcAnimator.bodyOffset(human, partialTick);
            renderStanceYawDegrees = nativeShoulder != 0 ? relativeAim + nativeShoulder
                    : relativeAim - Mth.clamp(relativeAim, -25.0F, 25.0F);
            return renderStanceYawDegrees;
        }

        if (GunSupport.get().isGun(human.getMainHandItem())
                && TaczNpcAnimator.isAimingOrFiring(human)) {
            // The server-side gunner goal already turns the entity toward its
            // ballistic target while aiming. Do not add the visual sidestep
            // turn on top of that yaw: it makes the gun point off-target.
            // When TaCZ aim is released, normal movement-based body turning
            // resumes smoothly from zero.
            stances.remove(human);
            return 0.0F;
        }

        // The camera already follows the observed head. Additional cosmetic sidestep
        // shoulder yaw makes first-person weapons sweep across the view; native gun
        // shoulder offsets above are still needed for their authored hand attachment.
        if (armsOnly) {
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
            float movementBodyYaw = Mth.rotLerp(partialTick, human.yBodyRotO, human.yBodyRot);
            float moveYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float relativeMoveYaw = Mth.wrapDegrees(moveYaw - movementBodyYaw);
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
        // Model instances are reused across entities; do not let the previous
        // Human's Player Animator processor leak into this render pass.
        PlayerAnimatorModelBridge.bind(this, null);
        // Keyframes modify pivots as well as rotations. Vanilla setupAnim does
        // not restore every pivot/axis, so a reused model needs its rest pose.
        for (ModelPart part : restParts) part.resetPose();
        boolean customMeleeAnimation = BetterCombatNpcAnimator.prepare(human)
                || BetterCombatNpcAnimator.shouldSuppressVanillaSwing(human);
        float vanillaAttackTime = this.attackTime;
        if (customMeleeAnimation || HumanShieldPresentation.active(human))
            this.attackTime = 0.0F;
        try {
            super.setupAnim(human, limbSwing, limbSwingAmount, ageInTicks, aimYaw, headPitch);
        } finally {
            this.attackTime = vanillaAttackTime;
        }
        // Prefer optional resource keyframes; native hold/aim and synchronized
        // procedural actions remain available without Player Animator.
        TaczNpcAnimator.apply(human, this, ageInTicks);
        ScgunsNpcAnimator.apply(human, this, limbSwing, limbSwingAmount);
        JegNpcAnimator.apply(human, this);
        BetterCombatNpcAnimator.apply(human, this, ageInTicks);
        // Cosmetic only. Two-handed weapons and attack animations always retain their arms.
        if (human.isNpcOffhandEating() && !human.isUsingItem() && !human.swinging
                && !customMeleeAnimation && !isRangedStance(human)) {
            ModelPart foodArm = human.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                    ? this.leftArm : this.rightArm;
            foodArm.xRot = -1.35F + 0.08F * Mth.sin(ageInTicks * 1.3F);
            foodArm.yRot = human.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 0.4F : -0.4F;
            if (foodArm == this.leftArm) this.leftSleeve.copyFrom(foodArm);
            else this.rightSleeve.copyFrom(foodArm);
        }
        if (armsOnly) spectatorMelee.apply(human, this, ageInTicks);
        // Keep the separate skin overlay on the same animated head pose.
        HumanShieldPresentation.apply(human, this);
        this.hat.copyFrom(this.head);
        if (armsOnly) {
            // This is a separate model instance. Never hide parts on the world renderer.
            this.setAllVisible(false);
            this.leftArm.visible = this.rightArm.visible = true;
            this.leftSleeve.visible = this.rightSleeve.visible = true;
        }
    }

    private static boolean isRangedStance(Human human) {
        ItemStack mainHand = human.getMainHandItem();
        if (HumanUtil.isMeleeWeapon(mainHand)) return false;
        if (RangedWeaponCustody.isBowOrCrossbow(mainHand)
                || mainHand.getItem() instanceof TridentItem
                || dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().isGun(mainHand)) return true;
        ItemStack offhand = human.getOffhandItem();
        return mainHand.isEmpty() && (RangedWeaponCustody.isBowOrCrossbow(offhand)
                || offhand.getItem() instanceof TridentItem);
    }
}
