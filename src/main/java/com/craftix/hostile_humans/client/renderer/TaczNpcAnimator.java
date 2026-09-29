package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Applies TaCZ's Player Animator layers to non-player Human models. */
public final class TaczNpcAnimator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Human, State> STATES = new WeakHashMap<>();
    private static final int LOWER_LAYER_PRIORITY = 93;
    private static final int UPPER_LAYER_PRIORITY = 94;
    private static final int ONE_SHOT_LAYER_PRIORITY = 95;
    private static final int ROTATION_LAYER_PRIORITY = 96;
    private static final int FADE_TICKS = 8;
    private static volatile Api api;
    private static volatile boolean lookupAttempted;
    private static volatile boolean warned;

    private TaczNpcAnimator() {}

    /**
     * TaCZ's aim state is read while the model is being animated, after the
     * renderer has already selected its whole-body sidestep yaw. Remember the
     * last client-side aim sample so an actively aiming gunner can keep the
     * body locked to the behavior-layer target yaw instead of turning the gun
     * away from the target to face its lateral movement.
     */
    static boolean isAimingOrFiring(Human human) {
        State state = STATES.get(human);
        return state != null && (state.aiming || human.tickCount <= state.firingUntilTick);
    }

    /** True only while a TaCZ-specific action should take precedence over CEM animations. */
    static boolean hasActiveCustomAnimation(Human human) {
        if (human == null || !hasDependencies()
                || !club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem())) {
            return false;
        }
        State state = STATES.get(human);
        if (state == null) return false;
        if (state.aiming || state.reloading || human.tickCount <= state.firingUntilTick) return true;
        Api resolved = api;
        if (resolved == null || state.oneShotLayer == null) return false;
        try {
            return (Boolean) resolved.layerIsActive.invoke(state.oneShotLayer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not read TaCZ's active one-shot animation state", exception);
            return false;
        }
    }

    static void apply(Human human, HumanoidModel<Human> model, float ageInTicks) {
        ItemStack gun = human.getMainHandItem();
        if (!hasDependencies() || !club.someoneice.humangunner.GunSupport.get().isGun(gun)
                || human.isVisuallySwimming() || human.isFallFlying()) {
            STATES.remove(human);
            return;
        }

        Api resolved = resolveApi();
        if (resolved == null) return;
        try {
            Object display = ((Optional<?>) resolved.getGunDisplay.invoke(null, gun)).orElse(null);
            if (!hasAnimationAsset(resolved, display)) {
                STATES.remove(human);
                return;
            }
            Object assetId = resolved.getPlayerAnimator3rd.invoke(display);
            Object assetManager = resolved.getAssetManager.invoke(null);

            State state = STATES.computeIfAbsent(human, ignored -> new State());
            if (!assetId.equals(state.assetId) || state.stack == null) {
                initialize(state, resolved, assetId, human.tickCount);
            }

            Object operator = resolved.fromLivingEntity.invoke(null, human);
            float aimProgress = ((Number) resolved.getAimingProgress.invoke(operator)).floatValue();
            boolean aiming = aimProgress > 0.0F;
            state.aiming = aiming;
            Object reloadState = resolved.getReloadState.invoke(operator);
            Object reloadType = reloadState == null ? null : resolved.getReloadType.invoke(reloadState);
            boolean reloading = reloadType != null
                    && (Boolean) resolved.isReloading.invoke(reloadType);
            state.reloading = reloading;
            long shootCooldown = ((Number) resolved.getShootCooldown.invoke(operator)).longValue();
            boolean fired = state.lastShootCooldown != Long.MIN_VALUE
                    && shootCooldown > state.lastShootCooldown + 2L;
            state.lastShootCooldown = shootCooldown;
            if (fired) state.firingUntilTick = human.tickCount + 6;

            updateLoop(state, resolved, assetManager, assetId, state.lowerLayer,
                    Channel.LOWER, selectLowerAnimation(human));
            updateLoop(state, resolved, assetManager, assetId, state.upperLayer,
                    Channel.UPPER, selectUpperAnimation(human, aiming));
            // TaCZ's native path always installs this empty-channel animation;
            // it carries its Player Animator aim/head adjustment metadata.
            updateLoop(state, resolved, assetManager, assetId, state.rotationLayer,
                    Channel.ROTATION, "empty");

            boolean reloadStarted = reloading && !state.wasReloading;
            state.wasReloading = reloading;
            if (reloadStarted) {
                startOneShot(state, resolved, assetManager, assetId, "reload_upper");
            } else if (fired && !reloading) {
                startOneShot(state, resolved, assetManager, assetId,
                        aiming ? "aim_fire_upper" : "normal_fire_upper");
            }

            tickStack(state, resolved, human.tickCount);
            if (!hasActiveCustomAnimation(human)) {
                // Leave the base model available to EMF/CEM packs while the
                // gunner is merely idle. EMF's pause callback takes over only
                // during actual aim, reload, or firing actions.
                PlayerAnimatorModelBridge.bind(model, null);
                return;
            }
            applyParts(state, resolved, model, ageInTicks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("TaCZ Player Animator layers could not be applied to a Human", exception);
            STATES.remove(human);
            PlayerAnimatorModelBridge.bind(model, null);
        }
    }

    private static boolean hasDependencies() {
        return ModList.get().isLoaded("tacz") && ModList.get().isLoaded("playeranimator");
    }

    private static boolean hasAnimationAsset(Api resolved, Object display)
            throws ReflectiveOperationException {
        if (display == null) return false;
        Object assetId = resolved.getPlayerAnimator3rd.invoke(display);
        return assetId != null && (Boolean) resolved.containsKey.invoke(
                resolved.getAssetManager.invoke(null), assetId);
    }

    private static void initialize(State state, Api resolved, Object assetId, int tickCount)
            throws ReflectiveOperationException {
        state.assetId = assetId;
        state.lowerAnimation = "";
        state.upperAnimation = "";
        state.rotationAnimation = "";
        state.missingLowerAnimation = "";
        state.missingUpperAnimation = "";
        state.missingRotationAnimation = "";
        state.missingOneShotAnimation = "";
        state.wasReloading = false;
        state.lastShootCooldown = Long.MIN_VALUE;
        state.lastTick = tickCount;

        state.stack = resolved.stackConstructor.newInstance();
        state.lowerLayer = resolved.layerConstructor.newInstance();
        state.upperLayer = resolved.layerConstructor.newInstance();
        state.oneShotLayer = resolved.layerConstructor.newInstance();
        state.rotationLayer = resolved.layerConstructor.newInstance();
        resolved.stackAddLayer.invoke(state.stack, LOWER_LAYER_PRIORITY, state.lowerLayer);
        resolved.stackAddLayer.invoke(state.stack, UPPER_LAYER_PRIORITY, state.upperLayer);
        resolved.stackAddLayer.invoke(state.stack, ONE_SHOT_LAYER_PRIORITY, state.oneShotLayer);
        resolved.stackAddLayer.invoke(state.stack, ROTATION_LAYER_PRIORITY, state.rotationLayer);
        state.applier = resolved.applierConstructor.newInstance(state.stack);
    }

    private static void updateLoop(State state, Api resolved, Object assetManager, Object assetId,
                                   Object layer, Channel channel, String desiredAnimation)
            throws ReflectiveOperationException {
        String current = getAnimationName(state, channel);
        String missing = getMissingAnimationName(state, channel);
        boolean active = (Boolean) resolved.layerIsActive.invoke(layer);
        if (desiredAnimation.equals(current) && active) return;
        if (desiredAnimation.equals(missing)) return;

        if (replaceAnimation(state, resolved, assetManager, assetId, layer, desiredAnimation)) {
            setAnimationName(state, channel, desiredAnimation);
            setMissingAnimationName(state, channel, "");
        } else {
            resolved.layerSetAnimation.invoke(layer, new Object[]{null});
            setAnimationName(state, channel, "");
            setMissingAnimationName(state, channel, desiredAnimation);
        }
    }

    private static void startOneShot(State state, Api resolved, Object assetManager,
                                     Object assetId, String animationName)
            throws ReflectiveOperationException {
        if ((Boolean) resolved.layerIsActive.invoke(state.oneShotLayer)
                || animationName.equals(state.missingOneShotAnimation)) {
            return;
        }
        if (replaceAnimation(state, resolved, assetManager, assetId,
                state.oneShotLayer, animationName)) {
            state.missingOneShotAnimation = "";
        } else {
            resolved.layerSetAnimation.invoke(state.oneShotLayer, new Object[]{null});
            state.missingOneShotAnimation = animationName;
        }
    }

    private static boolean replaceAnimation(State state, Api resolved, Object assetManager,
                                            Object assetId, Object layer, String animationName)
            throws ReflectiveOperationException {
        Object value = resolved.getAnimations.invoke(assetManager, assetId, animationName);
        Object animation = value instanceof Optional<?> optional ? optional.orElse(null) : null;
        if (animation == null) return false;
        Object player = resolved.playerConstructor.newInstance(animation);
        Object fade = resolved.standardFadeIn.invoke(null, FADE_TICKS, resolved.inOutSine);
        resolved.replaceAnimationWithFade.invoke(layer, fade, player);
        return true;
    }

    private static void tickStack(State state, Api resolved, int tickCount)
            throws ReflectiveOperationException {
        int elapsedTicks = tickCount - state.lastTick;
        if (elapsedTicks > 0) {
            for (int i = 0; i < Math.min(elapsedTicks, 20); i++) {
                resolved.stackTick.invoke(state.stack);
            }
            state.lastTick = tickCount;
        }
    }

    private static void applyParts(State state, Api resolved, HumanoidModel<Human> model,
                                   float ageInTicks) throws ReflectiveOperationException {
        resolved.setTickDelta.invoke(state.applier, ageInTicks - (float) Math.floor(ageInTicks));
        applyPart(state, resolved, "torso", model.body);
        applyPart(state, resolved, "head", model.head);
        applyPart(state, resolved, "rightArm", model.rightArm);
        applyPart(state, resolved, "leftArm", model.leftArm);
        applyPart(state, resolved, "rightLeg", model.rightLeg);
        applyPart(state, resolved, "leftLeg", model.leftLeg);
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> playerModel) {
            playerModel.jacket.copyFrom(model.body);
            playerModel.rightSleeve.copyFrom(model.rightArm);
            playerModel.leftSleeve.copyFrom(model.leftArm);
            playerModel.rightPants.copyFrom(model.rightLeg);
            playerModel.leftPants.copyFrom(model.leftLeg);
        }
        PlayerAnimatorModelBridge.bind(model, state.applier);
    }

    private static void applyPart(State state, Api resolved, String name, ModelPart part)
            throws ReflectiveOperationException {
        resolved.updatePart.invoke(state.applier, name, part);
    }

    private static String selectLowerAnimation(Human human) {
        if (human.isPassenger()) return "ride_lower";
        if (human.isFallFlying()) return "hold_lower";
        if (human.isSprinting()) return human.isCrouching() ? "crouch_walk_lower" : "run_lower";
        if (human.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
            return human.isCrouching() ? "crouch_walk_lower" : "walk_lower";
        }
        return human.isCrouching() ? "crouch_lower" : "hold_lower";
    }

    private static String selectUpperAnimation(Human human, boolean aiming) {
        if (aiming) return "aim_upper";
        if (human.isSprinting()) return human.isCrouching() ? "crouch_walk_upper" : "run_upper";
        if (human.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
            return human.isCrouching() ? "crouch_walk_upper" : "walk_upper";
        }
        return "hold_upper";
    }

    private static String getAnimationName(State state, Channel channel) {
        return switch (channel) {
            case LOWER -> state.lowerAnimation;
            case UPPER -> state.upperAnimation;
            case ROTATION -> state.rotationAnimation;
        };
    }

    private static void setAnimationName(State state, Channel channel, String value) {
        switch (channel) {
            case LOWER -> state.lowerAnimation = value;
            case UPPER -> state.upperAnimation = value;
            case ROTATION -> state.rotationAnimation = value;
        }
    }

    private static String getMissingAnimationName(State state, Channel channel) {
        return switch (channel) {
            case LOWER -> state.missingLowerAnimation;
            case UPPER -> state.missingUpperAnimation;
            case ROTATION -> state.missingRotationAnimation;
        };
    }

    private static void setMissingAnimationName(State state, Channel channel, String value) {
        switch (channel) {
            case LOWER -> state.missingLowerAnimation = value;
            case UPPER -> state.missingUpperAnimation = value;
            case ROTATION -> state.missingRotationAnimation = value;
        }
    }

    private static Api resolveApi() {
        if (!lookupAttempted) synchronized (TaczNpcAnimator.class) {
            if (!lookupAttempted) {
                try {
                    ClassLoader loader = TaczNpcAnimator.class.getClassLoader();
                    Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI", false, loader);
                    Method getGunDisplay = timeless.getMethod("getGunDisplay", ItemStack.class);
                    Class<?> displayType = Class.forName(
                            "com.tacz.guns.client.resource.GunDisplayInstance", false, loader);
                    Method getPlayerAnimator3rd = displayType.getMethod("getPlayerAnimator3rd");
                    Class<?> assetManagerType = Class.forName(
                            "com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager",
                            false, loader);
                    Method getAssetManager = assetManagerType.getMethod("get");
                    Method containsKey = assetManagerType.getMethod("containsKey",
                            Class.forName("net.minecraft.resources.ResourceLocation", false, loader));
                    Method getAnimations = assetManagerType.getDeclaredMethod("getAnimations",
                            Class.forName("net.minecraft.resources.ResourceLocation", false, loader),
                            String.class);
                    getAnimations.setAccessible(true);

                    Class<?> gunOperator = Class.forName("com.tacz.guns.api.entity.IGunOperator", false, loader);
                    Method fromLivingEntity = gunOperator.getMethod("fromLivingEntity",
                            Class.forName("net.minecraft.world.entity.LivingEntity", false, loader));
                    Method getAimingProgress = gunOperator.getMethod("getSynAimingProgress");
                    Method getReloadState = gunOperator.getMethod("getSynReloadState");
                    Method getShootCooldown = gunOperator.getMethod("getSynShootCoolDown");
                    Class<?> reloadStateType = Class.forName(
                            "com.tacz.guns.api.entity.ReloadState", false, loader);
                    Method getReloadType = reloadStateType.getMethod("getStateType");
                    Class<?> reloadEnum = Class.forName(
                            "com.tacz.guns.api.entity.ReloadState$StateType", false, loader);
                    Method isReloading = reloadEnum.getMethod("isReloading");

                    Class<?> animationType = Class.forName(
                            "dev.kosmx.playerAnim.core.data.KeyframeAnimation", false, loader);
                    Class<?> animationInterface = Class.forName(
                            "dev.kosmx.playerAnim.api.layered.IAnimation", false, loader);
                    Class<?> stackType = Class.forName(
                            "dev.kosmx.playerAnim.api.layered.AnimationStack", false, loader);
                    Constructor<?> stackConstructor = stackType.getConstructor();
                    Method stackAddLayer = stackType.getMethod("addAnimLayer", int.class, animationInterface);
                    Method stackTick = stackType.getMethod("tick");
                    Class<?> modifierLayer = Class.forName(
                            "dev.kosmx.playerAnim.api.layered.ModifierLayer", false, loader);
                    Constructor<?> layerConstructor = modifierLayer.getConstructor();
                    Method layerIsActive = modifierLayer.getMethod("isActive");
                    Method layerSetAnimation = modifierLayer.getMethod("setAnimation", animationInterface);
                    Class<?> fadeType = Class.forName(
                            "dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier", false, loader);
                    Class<?> easeType = Class.forName("dev.kosmx.playerAnim.core.util.Ease", false, loader);
                    Object inOutSine = easeType.getField("INOUTSINE").get(null);
                    Method standardFadeIn = fadeType.getMethod("standardFadeIn", int.class, easeType);
                    Method replaceAnimationWithFade = modifierLayer.getMethod(
                            "replaceAnimationWithFade", fadeType, animationInterface);
                    Class<?> playerType = Class.forName(
                            "dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer", false, loader);
                    Constructor<?> playerConstructor = playerType.getConstructor(animationType);

                    Class<?> applierType = Class.forName(
                            "dev.kosmx.playerAnim.impl.animation.AnimationApplier", false, loader);
                    Constructor<?> applierConstructor = applierType.getConstructor(animationInterface);
                    Method setTickDelta = applierType.getMethod("setTickDelta", float.class);
                    Method updatePart = applierType.getMethod("updatePart", String.class, ModelPart.class);
                    api = new Api(getGunDisplay, getPlayerAnimator3rd, getAssetManager, containsKey,
                            getAnimations, fromLivingEntity, getAimingProgress, getReloadState,
                            getShootCooldown, getReloadType, isReloading, stackConstructor,
                            stackAddLayer, stackTick, layerConstructor, layerIsActive,
                            layerSetAnimation, replaceAnimationWithFade, standardFadeIn,
                            inOutSine, playerConstructor, applierConstructor, setTickDelta, updatePart);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce("TaCZ or Player Animator does not expose the expected animation API", exception);
                }
                lookupAttempted = true;
            }
        }
        return api;
    }

    private static void warnOnce(String message, Throwable exception) {
        if (!warned) synchronized (TaczNpcAnimator.class) {
            if (!warned) {
                LOGGER.warn(message, exception);
                warned = true;
            }
        }
    }

    private enum Channel {
        LOWER,
        UPPER,
        ROTATION
    }

    private record Api(Method getGunDisplay, Method getPlayerAnimator3rd, Method getAssetManager,
                       Method containsKey, Method getAnimations, Method fromLivingEntity,
                       Method getAimingProgress, Method getReloadState, Method getShootCooldown,
                       Method getReloadType, Method isReloading, Constructor<?> stackConstructor,
                       Method stackAddLayer, Method stackTick, Constructor<?> layerConstructor,
                       Method layerIsActive, Method layerSetAnimation, Method replaceAnimationWithFade,
                       Method standardFadeIn, Object inOutSine, Constructor<?> playerConstructor,
                       Constructor<?> applierConstructor, Method setTickDelta, Method updatePart) {}

    private static final class State {
        private Object assetId;
        private Object stack;
        private Object lowerLayer;
        private Object upperLayer;
        private Object oneShotLayer;
        private Object rotationLayer;
        private Object applier;
        private String lowerAnimation = "";
        private String upperAnimation = "";
        private String rotationAnimation = "";
        private String missingLowerAnimation = "";
        private String missingUpperAnimation = "";
        private String missingRotationAnimation = "";
        private String missingOneShotAnimation = "";
        private boolean wasReloading;
        private boolean aiming;
        private boolean reloading;
        private int firingUntilTick = Integer.MIN_VALUE;
        private long lastShootCooldown = Long.MIN_VALUE;
        private int lastTick;
    }
}
