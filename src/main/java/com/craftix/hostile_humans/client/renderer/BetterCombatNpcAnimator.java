package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Native Human-to-Player-Animator bridge for Better Combat attacks. Each Human
 * gets its own animation stack; the mod advances it from client ticks and
 * applies it through the same PlayerModel processor used for player poses.
 * This intentionally does not require Mob Player Animator.
 */
final class BetterCombatNpcAnimator {
    private static final Map<Human, Playback> PLAYBACKS = new WeakHashMap<>();
    private static volatile Api api;
    private static volatile boolean apiLookupAttempted;
    private static volatile boolean tickListenerRegistered;

    private BetterCombatNpcAnimator() {}

    /** Resource reload invalidates players and optional API/resource lookups together. */
    static void resetPlayback() {
        PLAYBACKS.clear();
        api = null;
        apiLookupAttempted = false;
    }

    /**
     * Starts a newly synchronized attack before vanilla model setup.
     */
    static boolean prepare(Human human) {
        if (!isApplicable(human)) {
            PLAYBACKS.remove(human);
            return false;
        }
        registerTickListener();
        Api resolved = resolveApi();
        if (resolved == null) return false;

        Playback playback = PLAYBACKS.computeIfAbsent(human, ignored -> new Playback());
        int sequence = human.getNpcBetterCombatAttackAnimationSequence();
        // Shield-use updates may arrive before attack-cancellation metadata.
        // Consume the old sequence while blocking so lowering the shield cannot
        // replay a stale attack after a gun-to-melee handoff.
        if (human.isUsingItem() && human.getUseItem().getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK) {
            if (playback.sequence != sequence || playback.player != null) {
                playback.sequence = sequence;
                playback.lastTick = human.tickCount;
                playback.player = null;
                playback.attackStack = null;
                playback.layer = null;
                startIdle(playback, resolved, human);
            }
            return false;
        }
        var visual = human.getNpcBetterCombatAttackVisual();
        long now = human.level().getGameTime();
        if (!visual.active(now)) {
            if (playback.sequence != sequence || playback.player != null || playback.stack == null) {
                playback.sequence = sequence;
                clearAttack(playback);
                startIdle(playback, resolved, human);
            }
            return false;
        }
        if (sequence != playback.sequence) {
            playback.sequence = sequence;
            playback.lastTick = human.tickCount;
            int elapsed = visual.elapsed(now);
            // Never replay an expired attack from frame zero on late tracking.
            // Bound catch-up work for extreme custom attack durations.
            if (elapsed <= 200) start(playback, resolved, human, elapsed);
            else {
                clearAttack(playback);
                startIdle(playback, resolved, human);
            }
        }
        return isActive(playback, resolved);
    }

    /** Only an accepted, playable custom attack may suppress the vanilla fallback. */
    static boolean shouldSuppressVanillaSwing(Human human) {
        return human.swinging && hasActiveCustomAnimation(human);
    }

    /** True while this Human's Better Combat attack layer is actively playing. */
    static boolean hasActiveCustomAnimation(Human human) {
        if (!isApplicable(human)) return false;
        Playback playback = PLAYBACKS.get(human);
        Api resolved = api;
        return playback != null && resolved != null && isActive(playback, resolved);
    }

    static void apply(Human human, HumanoidModel<Human> model, float ageInTicks) {
        prepare(human);
        Api resolved = api;
        Playback playback = PLAYBACKS.get(human);
        if (resolved == null || playback == null || playback.stack == null
                || playback.applier == null) return;

        try {
            // Apply the same per-frame interpolation that Player Animator uses
            // during PlayerModel.setupAnim. Stack progression happens on the
            // client tick, never as a side effect of a render pass.
            resolved.setTickDelta.invoke(playback.applier,
                    ageInTicks - (float) Math.floor(ageInTicks));
            applyPart(resolved, playback, "torso", model.body);
            applyPart(resolved, playback, "head", model.head);
            applyPart(resolved, playback, "rightArm", model.rightArm);
            applyPart(resolved, playback, "leftArm", model.leftArm);
            applyPart(resolved, playback, "rightLeg", model.rightLeg);
            applyPart(resolved, playback, "leftLeg", model.leftLeg);
            model.hat.copyFrom(model.head);
            if (model instanceof PlayerModel<?> playerModel) {
                playerModel.jacket.copyFrom(model.body);
                playerModel.rightSleeve.copyFrom(model.rightArm);
                playerModel.leftSleeve.copyFrom(model.leftArm);
                playerModel.rightPants.copyFrom(model.rightLeg);
                playerModel.leftPants.copyFrom(model.leftLeg);
            }
            // Match Better Combat's player path: expose the active processor to
            // Player Animator so torso/limb bending reaches armor render layers.
            PlayerAnimatorModelBridge.bind(model, playback.applier);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not apply Better Combat's NPC attack animation", exception);
            playback.player = null;
            playback.stack = null;
            playback.attackStack = null;
            playback.layer = null;
            playback.applier = null;
        }
    }

    /** Player Animator applies the body channel to the whole renderer as well as its limbs. */
    static void applyBodyTransform(Human human, PoseStack poseStack, float partialTick) {
        prepare(human);
        Playback playback = PLAYBACKS.get(human);
        Api resolved = api;
        if (playback == null || playback.applier == null || resolved == null) return;
        try {
            resolved.setTickDelta.invoke(playback.applier, partialTick);
            Object position = resolved.get3DTransform.invoke(playback.applier, "body",
                    resolved.positionTransform, resolved.zeroVector);
            Object rotation = resolved.get3DTransform.invoke(playback.applier, "body",
                    resolved.rotationTransform, resolved.zeroVector);
            poseStack.translate(component(resolved, position, resolved.vectorX),
                    component(resolved, position, resolved.vectorY) + 0.7D,
                    component(resolved, position, resolved.vectorZ));
            poseStack.mulPose(Axis.ZP.rotation((float) component(resolved, rotation, resolved.vectorZ)));
            poseStack.mulPose(Axis.YP.rotation((float) component(resolved, rotation, resolved.vectorY)));
            poseStack.mulPose(Axis.XP.rotation((float) component(resolved, rotation, resolved.vectorX)));
            poseStack.translate(0.0D, -0.7D, 0.0D);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not apply Better Combat's full-body NPC transform", exception);
        }
    }

    private static double component(Api resolved, Object vector, Method accessor)
            throws ReflectiveOperationException {
        return ((Number) accessor.invoke(vector)).doubleValue();
    }

    /** Player Animator's HeldItemMixin normally only sees IAnimatedPlayer entities. */
    static void applyHeldItemBend(Human human, HumanoidArm arm, PoseStack poseStack) {
        if (!hasAnimationStack(human)) return;
        Playback playback = PLAYBACKS.get(human);
        Api resolved = api;
        if (playback == null || playback.applier == null || resolved == null) return;
        try {
            if (!Boolean.TRUE.equals(resolved.isBendEnabled.invoke(null))) return;
            Object bend = resolved.get3DTransform.invoke(playback.applier,
                    arm == HumanoidArm.LEFT ? "leftArm" : "rightArm",
                    resolved.bendTransform, resolved.zeroVector);
            float axisAngle = -(float) component(resolved, bend, resolved.vectorX);
            float amount = (float) component(resolved, bend, resolved.vectorY);
            if (Math.abs(amount) < 1.0E-5F) return;
            poseStack.translate(0.0F, 0.25F, 0.0F);
            poseStack.mulPose(new Quaternionf().rotateAxis(amount,
                    new Vector3f((float) Math.cos(axisAngle), 0.0F,
                            (float) Math.sin(axisAngle))));
            poseStack.translate(0.0F, -0.25F, 0.0F);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not apply Better Combat's NPC held-item bend", exception);
        }
    }

    static void applyHeldItemTransform(Human human, HumanoidArm arm, PoseStack poseStack) {
        if (!hasAnimationStack(human)) return;
        Playback playback = PLAYBACKS.get(human);
        Api resolved = api;
        if (playback == null || playback.applier == null || resolved == null) return;
        try {
            String part = arm == HumanoidArm.LEFT ? "leftItem" : "rightItem";
            Object position = resolved.get3DTransform.invoke(playback.applier, part,
                    resolved.positionTransform, resolved.zeroVector);
            Object rotation = resolved.get3DTransform.invoke(playback.applier, part,
                    resolved.rotationTransform, resolved.zeroVector);
            poseStack.translate(component(resolved, position, resolved.vectorX) / 16.0D,
                    component(resolved, position, resolved.vectorY) / 16.0D,
                    component(resolved, position, resolved.vectorZ) / 16.0D);
            poseStack.mulPose(Axis.ZP.rotation((float) component(resolved, rotation, resolved.vectorZ)));
            poseStack.mulPose(Axis.YP.rotation((float) component(resolved, rotation, resolved.vectorY)));
            poseStack.mulPose(Axis.XP.rotation((float) component(resolved, rotation, resolved.vectorX)));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not apply Better Combat's NPC held-item animation", exception);
        }
    }

    private static void registerTickListener() {
        if (tickListenerRegistered) return;
        synchronized (BetterCombatNpcAnimator.class) {
            if (tickListenerRegistered) return;
            MinecraftForge.EVENT_BUS.addListener(BetterCombatNpcAnimator::onClientTick);
            tickListenerRegistered = true;
        }
    }

    /**
     * Advance each Human's Player Animator stack in the same clock domain as
     * the entity itself. Rendering can occur more than once per tick or be
     * skipped altogether, so it must never own animation time progression.
     */
    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PLAYBACKS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            PLAYBACKS.clear();
            return;
        }

        Iterator<Map.Entry<Human, Playback>> iterator = PLAYBACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Human, Playback> entry = iterator.next();
            Human human = entry.getKey();
            Playback playback = entry.getValue();
            if (human == null || !human.isAlive() || human.level() != minecraft.level) {
                iterator.remove();
                continue;
            }
            if (playback.stack == null || playback.lastTick == Integer.MIN_VALUE) continue;

            int elapsedTicks = human.tickCount - playback.lastTick;
            if (elapsedTicks <= 0) continue;
            try {
                advancePlayback(playback, human, Math.min(elapsedTicks, 20));
                playback.lastTick = human.tickCount;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                warnOnce("Could not advance Better Combat's NPC animation on the client tick", exception);
                iterator.remove();
            }
        }
    }

    /** Tick-driven transmission gears must advance even while the NPC is off screen. */
    private static void advancePlayback(Playback playback, Human human, int ticks)
            throws ReflectiveOperationException {
        Api resolved = playback.api;
        for (int i = 0; i < ticks; i++) {
            updateWeaponPoses(playback, human);
            resolved.setTickDelta.invoke(playback.applier, 0.0F);
            resolved.stackTick.invoke(playback.stack);
        }
        resolved.setTickDelta.invoke(playback.applier, 0.0F);
    }

    /** The reference player/mob integration keeps weapon poses beneath the attack layer. */
    private static void updateWeaponPoses(Playback playback, Human human)
            throws ReflectiveOperationException {
        Api resolved = playback.api;
        if (playback.mainItemPose == null) return;
        boolean leftHanded = human.getMainArm() == HumanoidArm.LEFT;
        boolean busy = human.swinging || human.isSwimming() || human.isUsingItem()
                || CrossbowItem.isCharged(human.getMainHandItem());
        Object mainAttributes = resolved.getWeaponAttributes.invoke(null, human.getMainHandItem());
        Object offAttributes = resolved.getWeaponAttributes.invoke(null, human.getOffhandItem());
        Object mainPose = busy ? null : weaponPose(resolved, mainAttributes, false);
        Object offPose = busy ? null : weaponPose(resolved, offAttributes, true);
        boolean twoHanded = mainAttributes != null
                && Boolean.TRUE.equals(resolved.isTwoHanded.invoke(mainAttributes));
        boolean walking = human.getDeltaMovement().horizontalDistance() > 0.03D;
        boolean bodyPose = !busy && (twoHanded || (!walking && !human.isCrouching()));
        resolved.setPose.invoke(playback.mainItemPose, mainPose, leftHanded);
        resolved.setPose.invoke(playback.offItemPose, offPose, leftHanded);
        resolved.setPose.invoke(playback.mainBodyPose, bodyPose ? mainPose : null, leftHanded);
        resolved.setPose.invoke(playback.offBodyPose, bodyPose ? offPose : null, leftHanded);
    }

    private static Object weaponPose(Api resolved, Object attributes, boolean offHand)
            throws ReflectiveOperationException {
        if (attributes == null) return null;
        Object name = (offHand ? resolved.offHandPoseName : resolved.mainHandPoseName).invoke(attributes);
        return name instanceof String poseName ? findAnimation(resolved.animations, poseName) : null;
    }

    private static boolean isApplicable(Human human) {
        return human != null && ModList.get().isLoaded("bettercombat")
                && human.isNpcBetterCombatMeleeEnabled()
                && com.craftix.hostile_humans.HumanUtil.isMeleeWeapon(human.getMainHandItem())
                && !club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem());
    }

    private static boolean hasAnimationStack(Human human) {
        if (!isApplicable(human)) return false;
        Playback playback = PLAYBACKS.get(human);
        return playback != null && playback.stack != null && playback.applier != null;
    }

    private static void clearAttack(Playback playback) {
        playback.player = null;
        playback.attackStack = null;
        playback.layer = null;
    }

    private static void startIdle(Playback playback, Api resolved, Human human) {
        try {
            createPoseStack(playback, resolved, human);
            playback.applier = resolved.applierConstructor.newInstance(playback.stack);
            playback.lastTick = human.tickCount;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not start Better Combat's NPC idle weapon pose", exception);
            playback.stack = null;
            playback.applier = null;
        }
    }

    private static void createPoseStack(Playback playback, Api resolved, Human human)
            throws ReflectiveOperationException {
        playback.stack = resolved.stackConstructor.newInstance();
        playback.api = resolved;
        playback.mainBodyPose = resolved.poseConstructor.newInstance(null, true, true);
        playback.mainItemPose = resolved.poseConstructor.newInstance(null, false, true);
        playback.offBodyPose = resolved.poseConstructor.newInstance(null, true, false);
        playback.offItemPose = resolved.poseConstructor.newInstance(null, false, true);
        resolved.stackAddLayer.invoke(playback.stack, 1, resolved.poseBase.get(playback.offItemPose));
        resolved.stackAddLayer.invoke(playback.stack, 2, resolved.poseBase.get(playback.offBodyPose));
        resolved.stackAddLayer.invoke(playback.stack, 3, resolved.poseBase.get(playback.mainItemPose));
        resolved.stackAddLayer.invoke(playback.stack, 4, resolved.poseBase.get(playback.mainBodyPose));
        updateWeaponPoses(playback, human);
    }

    private static void start(Playback playback, Api resolved, Human human, int elapsedTicks) {
        playback.player = null;
        playback.stack = null;
        playback.attackStack = null;
        playback.mainBodyPose = null;
        playback.mainItemPose = null;
        playback.offBodyPose = null;
        playback.offItemPose = null;
        playback.layer = null;
        playback.applier = null;
        String animationName = human.getNpcBetterCombatAttackAnimation();
        if (animationName == null || animationName.isBlank()) {
            startIdle(playback, resolved, human);
            return;
        }
        try {
            Object animation = findAnimation(resolved.animations, animationName);
            if (animation == null) {
                animation = findAnimation(resolved.animations,
                        "two_handed_slash_horizontal_right");
            }
            if (animation == null) return;

            Object builder = resolved.mutableCopy.invoke(animation);
            resolved.fullyEnablePart.invoke(resolved.torsoField.get(builder), true);
            Object head = resolved.headField.get(builder);
            resolved.setEnabled.invoke(resolved.headPitchField.get(head), false);
            // Match Better Combat's player activity filter: keep the attack
            // animation active while swimming/fall-flying, but disable the
            // leg channels while swimming or mounted.
            if (human.isPassenger() || human.getPose() == Pose.SWIMMING) {
                resolved.configurePart.invoke(null, resolved.rightLegField.get(builder), false, false);
                resolved.configurePart.invoke(null, resolved.leftLegField.get(builder), false, false);
            }
            Object preparedAnimation = resolved.build.invoke(builder);

            playback.player = resolved.playerConstructor.newInstance(preparedAnimation, 0);
            Object adjustment = resolved.adjustmentConstructor.newInstance(
                    attackAdjustment(resolved, human));
            playback.attackStack = resolved.attackStackConstructor.newInstance(adjustment);
            playback.layer = resolved.attackStackBaseField.get(playback.attackStack);
            createPoseStack(playback, resolved, human);

            float length = Math.max(1.0F, human.getNpcBetterCombatAttackAnimationDuration());
            float upswing = Math.max(0.05F, Math.min(0.95F,
                    human.getNpcBetterCombatAttackAnimationUpswing()));
            float animationEnd = Math.max(1.0F,
                    ((Number) resolved.endTickField.get(preparedAnimation)).floatValue());
            float fullSpeed = animationEnd / length;
            float upswingMultiplier = getUpswingMultiplier(resolved);
            float initialSpeed = fullSpeed / upswingMultiplier;
            // Keep this formula byte-for-byte equivalent in meaning to
            // Better Combat's player playAttackAnimation implementation.
            // The old NPC path interpolated between unrelated upswing values,
            // which made the second half of many attacks play at the wrong
            // speed and visibly diverge from the player's animation.
            float recoverySpeed = calculateRecoverySpeed(fullSpeed, upswing, upswingMultiplier);

            List<Object> gears = new ArrayList<>(2);
            gears.add(resolved.gearConstructor.newInstance(length * upswing, recoverySpeed));
            gears.add(resolved.gearConstructor.newInstance(length, fullSpeed));
            Object speedModifier = resolved.attackStackSpeedField.get(playback.attackStack);
            resolved.setSpeed.invoke(speedModifier, initialSpeed, gears);

            Object mirrorModifier = resolved.attackStackMirrorField.get(playback.attackStack);
            resolved.setMirrorEnabled.invoke(mirrorModifier, human.getMainArm() == HumanoidArm.LEFT);

            int beginTick = Math.max(1, resolved.beginTickField.getInt(preparedAnimation));
            Object fade = resolved.standardFadeIn.invoke(null, beginTick, resolved.inOutSine);
            resolved.replaceAnimationWithFade.invoke(playback.layer, fade, playback.player);
            // Better Combat puts its attack layer on the Player Animator stack
            // at priority 2000. Use that same stack contract for NPC playback.
            resolved.stackAddLayer.invoke(playback.stack, 2000, playback.layer);
            playback.applier = resolved.applierConstructor.newInstance(playback.stack);
            advancePlayback(playback, human, elapsedTicks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not start Better Combat's configured NPC attack animation", exception);
            playback.player = null;
            playback.stack = null;
            playback.attackStack = null;
            playback.mainBodyPose = null;
            playback.mainItemPose = null;
            playback.offBodyPose = null;
            playback.offItemPose = null;
            playback.layer = null;
            playback.applier = null;
        }
    }

    private static Function<String, Optional<Object>> attackAdjustment(Api resolved, Human human) {
        WeakReference<Human> humanReference = new WeakReference<>(human);
        return partName -> {
            Human currentHuman = humanReference.get();
            if (currentHuman == null) return Optional.empty();
            float pitch = (float) Math.toRadians(currentHuman.getXRot());
            float rotationX;
            switch (partName) {
                case "body" -> rotationX = -pitch * 0.75F;
                case "rightArm", "leftArm" -> rotationX = pitch * 0.25F;
                case "rightLeg", "leftLeg" -> rotationX = -pitch * 0.75F;
                default -> { return Optional.empty(); }
            }
            try {
                Object rotation = resolved.vectorConstructor.newInstance(rotationX, 0.0F, 0.0F);
                Object offset = resolved.vectorConstructor.newInstance(0.0F, 0.0F, 0.0F);
                return Optional.of(resolved.partModifierConstructor.newInstance(rotation, offset));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                warnOnce("Could not apply Better Combat's player pitch adjustment to the NPC animation", exception);
                return Optional.empty();
            }
        };
    }

    private static float getUpswingMultiplier(Api resolved) {
        try {
            Object config = resolved.configField.get(null);
            Object value = resolved.getUpswingMultiplier.invoke(config);
            if (value instanceof Number number && Float.isFinite(number.floatValue())) {
                return Math.max(0.1F, number.floatValue());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Better Combat's animation timing setting was unavailable; using its default timing", exception);
        }
        return 1.0F;
    }

    /** Mirrors Better Combat's player attack-animation recovery-speed formula. */
    static float calculateRecoverySpeed(float fullSpeed, float upswing, float upswingMultiplier) {
        float blend = Math.max(upswingMultiplier - 0.5F, 0.0F) / 0.5F;
        float slowEndpoint = 1.0F - upswing;
        float fastEndpoint = upswing / (1.0F - upswing);
        return fullSpeed * (slowEndpoint + blend * (fastEndpoint - slowEndpoint));
    }

    private static Object findAnimation(Map<String, Object> animations, String name) {
        Object animation = animations.get(name);
        if (animation == null) animation = animations.get("bettercombat:" + name);
        return animation;
    }

    private static void applyPart(Api resolved, Playback playback, String name, ModelPart part)
            throws ReflectiveOperationException {
        resolved.updatePart.invoke(playback.applier, name, part);
    }

    private static boolean isActive(Playback playback, Api resolved) {
        if (playback.layer == null) return false;
        try {
            return (Boolean) resolved.layerIsActive.invoke(playback.layer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Better Combat's NPC animation state could not be read", exception);
            playback.player = null;
            playback.layer = null;
            playback.applier = null;
            return false;
        }
    }

    private static Api resolveApi() {
        if (!apiLookupAttempted) synchronized (BetterCombatNpcAnimator.class) {
            if (!apiLookupAttempted) {
                try {
                    api = ClientAnimationCompatibility.resolveBetterCombat();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce("Better Combat or Player Animator does not expose its expected client animation API", exception);
                }
                apiLookupAttempted = true;
            }
        }
        return api;
    }

    private static void warnOnce(String message, Throwable exception) {
        ClientAnimationCompatibility.warnOnce("BetterCombatNpcAnimator", message, exception);
    }

    record Api(Map<String, Object> animations, Constructor<?> playerConstructor,
                       Constructor<?> attackStackConstructor, Field attackStackBaseField,
                       Field attackStackSpeedField, Field attackStackMirrorField,
                       Constructor<?> stackConstructor,
                       Constructor<?> applierConstructor,
                       Method mutableCopy, Method build, Method fullyEnablePart, Method setEnabled,
                       Method configurePart, Field torsoField, Field headField,
                       Field rightLegField, Field leftLegField, Field headPitchField,
                       Field endTickField, Field beginTickField, Method setMirrorEnabled,
                       Method setSpeed,
                       Constructor<?> gearConstructor, Method standardFadeIn, Object inOutSine,
                       Method replaceAnimationWithFade, Method stackTick, Method stackAddLayer,
                       Constructor<?> adjustmentConstructor, Constructor<?> partModifierConstructor,
                       Constructor<?> vectorConstructor, Method layerIsActive,
                       Method setTickDelta, Method updatePart, Field configField,
                       Method getUpswingMultiplier, Constructor<?> poseConstructor,
                       Field poseBase, Method setPose, Method getWeaponAttributes,
                       Method mainHandPoseName, Method offHandPoseName, Method isTwoHanded,
                       Method get3DTransform, Object positionTransform, Object rotationTransform,
                       Object bendTransform, Object zeroVector,
                       Method vectorX, Method vectorY, Method vectorZ,
                       Method isBendEnabled) {}

    private static final class Playback {
        private int sequence = Integer.MIN_VALUE;
        private int lastTick = Integer.MIN_VALUE;
        private Api api;
        private Object player;
        private Object stack;
        private Object attackStack;
        private Object mainBodyPose;
        private Object mainItemPose;
        private Object offBodyPose;
        private Object offItemPose;
        private Object layer;
        private Object applier;

        private Playback() {}
    }
}
