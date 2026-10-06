package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Optional resource keyframes with native TaCZ model animation as a fallback. */
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

    /** Invalidate playback after a resource reload, including missing-asset caches. */
    static void resetPlayback() {
        STATES.clear();
    }

    static boolean isAimingOrFiring(Human human) {
        if (!canAnimate(human)) return false;
        try {
            return TaczNativeAnimation.sample(human).aim() > 0.0F || recentlyFired(human);
        } catch (RuntimeException | LinkageError exception) {
            warnOnce("Could not read TaCZ synchronized action state", exception);
            return false;
        }
    }

    /** EMF asks before setupAnim: read live synchronized state, never cached aim/reload flags. */
    static boolean hasActiveCustomAnimation(Human human) {
        if (!canAnimate(human)) return false;
        try {
            return TaczNativeAnimation.sample(human).actionActive() || recentlyFired(human);
        } catch (RuntimeException | LinkageError exception) {
            warnOnce("Could not read TaCZ synchronized action state", exception);
            return false;
        }
    }

    private static boolean recentlyFired(Human human) {
        State state = STATES.get(human);
        return state != null && !discontinuous(state, human)
                && human.tickCount >= state.firedTick && human.tickCount < state.firedTick + 6;
    }

    private static boolean canAnimate(Human human) {
        return human != null && ModList.get().isLoaded("tacz") && human.isAlive()
                && !human.isSleeping() && !human.isVisuallySwimming() && !human.isFallFlying()
                && club.someoneice.humangunner.GunSupport.get().isGun(human.getMainHandItem());
    }

    private static boolean discontinuous(State state, Human human) {
        double dx = human.getX() - state.x;
        double dy = human.getY() - state.y;
        double dz = human.getZ() - state.z;
        return TaczAnimationPolicy.needsReset(state.lastTick, human.tickCount,
                state.lastGameTime, human.level().getGameTime(), dx * dx + dy * dy + dz * dz);
    }

    static void apply(Human human, HumanoidModel<Human> model, float ageInTicks) {
        PlayerAnimatorModelBridge.bind(model, null);
        if (!canAnimate(human)) {
            STATES.remove(human);
            return;
        }
        State state = STATES.get(human);
        if (state == null || discontinuous(state, human)
                || state.model == null || state.model.get() != model) {
            state = new State();
            state.lastTick = human.tickCount;
            state.model = new WeakReference<>(model);
            STATES.put(human, state);
        }
        state.lastGameTime = human.level().getGameTime();
        state.x = human.getX();
        state.y = human.getY();
        state.z = human.getZ();
        try {
            TaczNativeAnimation.Sample sample = TaczNativeAnimation.sample(human);
            boolean fired = TaczAnimationPolicy.cooldownStarted(state.lastShootCooldown, sample.shootCooldown());
            state.lastShootCooldown = sample.shootCooldown();
            if (fired) state.firedTick = human.tickCount;
            boolean reloadStarted = sample.reloading() && !state.wasReloading;
            if (reloadStarted) state.reloadStart = human.tickCount;
            state.wasReloading = sample.reloading();
            if (!applyKeyframes(state, human, model, ageInTicks, sample, fired, reloadStarted)) {
                TaczNativeAnimation.apply(human, model, sample, ageInTicks, state.firedTick, state.reloadStart);
                copyOverlays(model);
            }
        } catch (RuntimeException | LinkageError exception) {
            warnOnce("TaCZ native animation could not be applied to a Human", exception);
            STATES.remove(human);
            PlayerAnimatorModelBridge.bind(model, null);
        } finally {
            state.lastTick = human.tickCount;
        }
    }

    private static boolean applyKeyframes(State state, Human human, HumanoidModel<Human> model,
                                         float age, TaczNativeAnimation.Sample sample,
                                         boolean fired, boolean reloadStarted) {
        if (!ModList.get().isLoaded("playeranimator")) return false;
        Api resolved = resolveApi();
        if (resolved == null) return false;
        try {
            ItemStack gun = human.getMainHandItem();
            Object display = ((Optional<?>) resolved.getGunDisplay.invoke(null, gun)).orElse(null);
            if (!hasAnimationAsset(resolved, display)) {
                state.stack = null;
                return false;
            }
            Object assetId = resolved.getPlayerAnimator3rd.invoke(display);
            Object assetManager = resolved.getAssetManager.invoke(null);
            // A reload may replace the manager or the asset under the same ID.
            Object sentinel = resolved.getAnimations.invoke(assetManager, assetId, "hold_upper");
            Object asset = sentinel instanceof Optional<?> optional ? optional.orElse(null) : null;
            if (!assetId.equals(state.assetId) || state.stack == null || asset != state.assetIdentity) {
                initialize(state, resolved, assetId, human.tickCount);
                state.assetIdentity = asset;
                reloadStarted = sample.reloading();
            }
            updateLoop(state, resolved, assetManager, assetId, state.lowerLayer,
                    Channel.LOWER, selectLowerAnimation(human));
            updateLoop(state, resolved, assetManager, assetId, state.upperLayer,
                    Channel.UPPER, selectUpperAnimation(human, sample.aim() > 0.0F));
            updateLoop(state, resolved, assetManager, assetId, state.rotationLayer,
                    Channel.ROTATION, "empty");
            if (reloadStarted) {
                // Reload preempts an unfinished fire animation.
                resolved.layerSetAnimation.invoke(state.oneShotLayer, new Object[]{null});
                startOneShot(state, resolved, assetManager, assetId, "reload_upper");
            } else if (fired && !sample.reloading()) {
                startOneShot(state, resolved, assetManager, assetId,
                        sample.aim() > 0.0F ? "aim_fire_upper" : "normal_fire_upper");
            }
            if (!sample.reloading() && state.keyframeReloading) {
                resolved.layerSetAnimation.invoke(state.oneShotLayer, new Object[]{null});
            }
            state.keyframeReloading = sample.reloading();
            tickStack(state, resolved, human.tickCount);
            // Programmatic fallback covers unavailable channels/actions. No stale
            // one-shot may keep EMF paused once the synchronized action has ended.
            boolean action = sample.actionActive() || recentlyFired(human);
            boolean missingAction = sample.reloading() && "reload_upper".equals(state.missingOneShotAnimation)
                    || recentlyFired(human) && (sample.aim() > 0.0F ? "aim_fire_upper" : "normal_fire_upper")
                            .equals(state.missingOneShotAnimation);
            if (!action || missingAction || sample.bolting()
                    || sample.drawCooldown() > 0L || sample.meleeCooldown() > 0L
                    || state.upperAnimation.isEmpty()) return false;
            applyParts(state, resolved, model, age);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("TaCZ Player Animator playback failed; using native model animation", exception);
            state.stack = null;
            PlayerAnimatorModelBridge.bind(model, null);
            return false;
        }
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
        state.keyframeReloading = false;
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
        }
        state.lastTick = tickCount;
    }

    private static void applyParts(State state, Api resolved, HumanoidModel<Human> model,
                                   float ageInTicks) throws ReflectiveOperationException {
        ModelPart[] parts = {model.body, model.head, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        String[] names = {"torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg"};
        PartPose[] poses = new PartPose[parts.length];
        float[][] scales = new float[parts.length][3];
        for (int i = 0; i < parts.length; i++) {
            poses[i] = parts[i].storePose();
            scales[i] = new float[]{parts[i].xScale, parts[i].yScale, parts[i].zScale};
        }
        try {
            resolved.setTickDelta.invoke(state.applier, ageInTicks - (float) Math.floor(ageInTicks));
            for (int i = 0; i < parts.length; i++) applyPart(state, resolved, names[i], parts[i]);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // A failed optional layer must not leave half a keyframe on the
            // vanilla pose used by the native fallback (especially the legs).
            for (int i = 0; i < parts.length; i++) {
                parts[i].loadPose(poses[i]);
                parts[i].xScale = scales[i][0];
                parts[i].yScale = scales[i][1];
                parts[i].zScale = scales[i][2];
            }
            throw exception;
        }
        copyOverlays(model);
        PlayerAnimatorModelBridge.bind(model, state.applier);
    }

    private static void copyOverlays(HumanoidModel<Human> model) {
        model.hat.copyFrom(model.head);
        if (model instanceof PlayerModel<?> playerModel) {
            playerModel.jacket.copyFrom(model.body);
            playerModel.rightSleeve.copyFrom(model.rightArm);
            playerModel.leftSleeve.copyFrom(model.leftArm);
            playerModel.rightPants.copyFrom(model.rightLeg);
            playerModel.leftPants.copyFrom(model.leftLeg);
        }
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
                            getAnimations, stackConstructor,
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
                       Method containsKey, Method getAnimations, Constructor<?> stackConstructor,
                       Method stackAddLayer, Method stackTick, Constructor<?> layerConstructor,
                       Method layerIsActive, Method layerSetAnimation, Method replaceAnimationWithFade,
                       Method standardFadeIn, Object inOutSine, Constructor<?> playerConstructor,
                       Constructor<?> applierConstructor, Method setTickDelta, Method updatePart) {}

    private static final class State {
        private WeakReference<HumanoidModel<Human>> model;
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
        private boolean keyframeReloading;
        private int firedTick = Integer.MIN_VALUE / 2;
        private int reloadStart;
        private long lastGameTime;
        private double x, y, z;
        private Object assetIdentity;
        private long lastShootCooldown = Long.MIN_VALUE;
        private int lastTick;
    }
}
