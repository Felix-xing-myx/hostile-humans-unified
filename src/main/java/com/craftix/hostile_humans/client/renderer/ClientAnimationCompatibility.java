package com.craftix.hostile_humans.client.renderer;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Client-only optional-mod contracts. Keep descriptors here so a dependency update
 * can be audited without changing attack timing, model transforms or armor binding.
 * Verified against TaCZ 1.1.8-hotfix, Better Combat 1.9.0 and Player Animator 1.0.2-rc1.
 * Optional mod types deliberately never appear in Java method/field descriptors.
 */
public final class ClientAnimationCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static volatile ReloadSoundAccess reloadSoundAccess;
    private static volatile boolean reloadSoundLookupAttempted;

    private ClientAnimationCompatibility() {}

    public static void warnOnce(String feature, String operation, Throwable exception) {
        if (!WARNED.add(feature + ":" + operation)) return;
        Throwable cause = exception instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : exception;
        LOGGER.warn("[{}] {}", feature, operation, cause);
    }

    static BetterCombatNpcAnimator.Api resolveBetterCombat() throws ReflectiveOperationException {
        ClassLoader loader = ClientAnimationCompatibility.class.getClassLoader();
        Class<?> animationRegistry = Class.forName(
                "net.bettercombat.client.animation.AnimationRegistry", false, loader);
        Field animationField = animationRegistry.getField("animations");
        @SuppressWarnings("unchecked")
        Map<String, Object> animations = (Map<String, Object>) animationField.get(null);

        Class<?> animationType = Class.forName(
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation", false, loader);
        Class<?> builderType = Class.forName(
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation$AnimationBuilder", false, loader);
        Class<?> stateCollectionType = Class.forName(
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection", false, loader);
        Class<?> stateType = Class.forName(
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection$State", false, loader);
        Method mutableCopy = animationType.getMethod("mutableCopy");
        Method build = builderType.getMethod("build");
        Method fullyEnablePart = stateCollectionType.getMethod("fullyEnablePart", boolean.class);
        Method setEnabled = stateType.getMethod("setEnabled", boolean.class);
        Class<?> stateHelper = Class.forName(
                "net.bettercombat.client.animation.StateCollectionHelper", false, loader);
        Method configurePart = stateHelper.getMethod("configure", stateCollectionType,
                boolean.class, boolean.class);

        Class<?> animationPlayer = Class.forName(
                "net.bettercombat.client.animation.CustomAnimationPlayer", false, loader);
        Constructor<?> playerConstructor = animationPlayer.getConstructor(animationType, int.class);
        Class<?> animationInterface = Class.forName(
                "dev.kosmx.playerAnim.api.layered.IAnimation", false, loader);
        Class<?> modifierLayer = Class.forName(
                "dev.kosmx.playerAnim.api.layered.ModifierLayer", false, loader);
        Class<?> mirrorModifier = Class.forName(
                "dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier", false, loader);
        Method setMirrorEnabled = mirrorModifier.getMethod("setEnabled", boolean.class);

        Class<?> adjustmentModifier = Class.forName(
                "dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier", false, loader);

        Class<?> speedModifier = Class.forName(
                "net.bettercombat.client.animation.modifier.TransmissionSpeedModifier", false, loader);
        Method setSpeed = speedModifier.getMethod("set", float.class, List.class);
        Class<?> gearType = Class.forName(
                "net.bettercombat.client.animation.modifier.TransmissionSpeedModifier$Gear", false, loader);
        Constructor<?> gearConstructor = gearType.getConstructor(float.class, float.class);

        Class<?> attackStackType = Class.forName(
                "net.bettercombat.client.animation.AttackAnimationSubStack", false, loader);
        Constructor<?> attackStackConstructor = attackStackType.getConstructor(adjustmentModifier);
        Field attackStackBase = attackStackType.getField("base");
        Field attackStackSpeed = attackStackType.getField("speed");
        Field attackStackMirror = attackStackType.getField("mirror");
        Class<?> poseStackType = Class.forName(
                "net.bettercombat.client.animation.PoseSubStack", false, loader);
        Class<?> abstractModifier = Class.forName(
                "dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier", false, loader);
        Constructor<?> poseConstructor = poseStackType.getConstructor(
                abstractModifier, boolean.class, boolean.class);
        Field poseBase = poseStackType.getField("base");
        Method setPose = poseStackType.getMethod("setPose", animationType, boolean.class);
        Class<?> weaponRegistry = Class.forName(
                "net.bettercombat.logic.WeaponRegistry", false, loader);
        Method getWeaponAttributes = weaponRegistry.getMethod("getAttributes", ItemStack.class);
        Class<?> weaponAttributes = Class.forName(
                "net.bettercombat.api.WeaponAttributes", false, loader);
        Method mainHandPoseName = weaponAttributes.getMethod("pose");
        Method offHandPoseName = weaponAttributes.getMethod("offHandPose");
        Method isTwoHanded = weaponAttributes.getMethod("isTwoHanded");

        Class<?> fadeType = Class.forName(
                "dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier", false, loader);
        Class<?> easeType = Class.forName("dev.kosmx.playerAnim.core.util.Ease", false, loader);
        Object inOutSine = easeType.getField("INOUTSINE").get(null);
        Method standardFadeIn = fadeType.getMethod("standardFadeIn", int.class, easeType);
        Method replaceAnimationWithFade = modifierLayer.getMethod(
                "replaceAnimationWithFade", fadeType, animationInterface);
        Method layerIsActive = modifierLayer.getMethod("isActive");

        Class<?> animationStack = Class.forName(
                "dev.kosmx.playerAnim.api.layered.AnimationStack", false, loader);
        Constructor<?> stackConstructor = animationStack.getConstructor();
        Method stackTick = animationStack.getMethod("tick");
        Method stackAddLayer = animationStack.getMethod(
                "addAnimLayer", int.class, animationInterface);
        Class<?> partModifier = Class.forName(
                "dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier$PartModifier",
                false, loader);
        Class<?> vector3f = Class.forName(
                "dev.kosmx.playerAnim.core.util.Vec3f", false, loader);
        Constructor<?> adjustmentConstructor = adjustmentModifier.getConstructor(Function.class);
        Constructor<?> partModifierConstructor = partModifier.getConstructor(vector3f, vector3f);
        Constructor<?> vectorConstructor = vector3f.getConstructor(float.class, float.class, float.class);

        Class<?> applierType = Class.forName(
                "dev.kosmx.playerAnim.impl.animation.AnimationApplier", false, loader);
        Constructor<?> applierConstructor = applierType.getConstructor(animationInterface);
        Method setTickDelta = applierType.getMethod("setTickDelta", float.class);
        Method updatePart = applierType.getMethod("updatePart", String.class, ModelPart.class);
        Class<?> transformType = Class.forName(
                "dev.kosmx.playerAnim.api.TransformType", false, loader);
        Method get3DTransform = applierType.getMethod("get3DTransform", String.class,
                transformType, vector3f);
        Object positionTransform = transformType.getField("POSITION").get(null);
        Object rotationTransform = transformType.getField("ROTATION").get(null);
        Object bendTransform = transformType.getField("BEND").get(null);
        Object zeroVector = vector3f.getField("ZERO").get(null);
        Method vectorX = vector3f.getMethod("getX");
        Method vectorY = vector3f.getMethod("getY");
        Method vectorZ = vector3f.getMethod("getZ");
        Class<?> playerAnimatorHelper = Class.forName(
                "dev.kosmx.playerAnim.impl.Helper", false, loader);
        Method isBendEnabled = playerAnimatorHelper.getMethod("isBendEnabled");
        Class<?> betterCombat = Class.forName("net.bettercombat.BetterCombat", false, loader);
        Field configField = betterCombat.getField("config");
        Class<?> configType = Class.forName("net.bettercombat.config.ServerConfig", false, loader);
        Method getUpswingMultiplier = configType.getMethod("getUpswingMultiplier");

        return new BetterCombatNpcAnimator.Api(animations, playerConstructor, attackStackConstructor,
                attackStackBase, attackStackSpeed, attackStackMirror, stackConstructor,
                applierConstructor,
                mutableCopy, build, fullyEnablePart, setEnabled, configurePart,
                builderType.getField("torso"), builderType.getField("head"),
                builderType.getField("rightLeg"), builderType.getField("leftLeg"),
                Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection", false, loader)
                        .getField("pitch"),
                animationType.getField("endTick"), animationType.getField("beginTick"),
                setMirrorEnabled, setSpeed,
                gearConstructor, standardFadeIn, inOutSine, replaceAnimationWithFade,
                stackTick, stackAddLayer, adjustmentConstructor, partModifierConstructor,
                vectorConstructor, layerIsActive, setTickDelta, updatePart, configField,
                getUpswingMultiplier, poseConstructor, poseBase, setPose,
                getWeaponAttributes, mainHandPoseName, offHandPoseName, isTwoHanded,
                get3DTransform, positionTransform, rotationTransform, bendTransform,
                zeroVector,
                vectorX, vectorY, vectorZ, isBendEnabled);
    }

    static TaczNpcAnimator.Api resolveTacz() throws ReflectiveOperationException {
        ClassLoader loader = ClientAnimationCompatibility.class.getClassLoader();
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
        // TaCZ's public AnimationManager methods require AbstractClientPlayer;
        // they cannot play an independent Human NPC stack. This package-private
        // asset lookup is the sole keyframe contract requiring accessibility.
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
        return new TaczNpcAnimator.Api(getGunDisplay, getPlayerAnimator3rd, getAssetManager, containsKey,
                getAnimations, stackConstructor,
                stackAddLayer, stackTick, layerConstructor, layerIsActive,
                layerSetAnimation, replaceAnimationWithFade, standardFadeIn,
                inOutSine, playerConstructor, applierConstructor, setTickDelta, updatePart);
    }

    static TaczNpcAnimator.ReloadApi resolveTaczReload() throws ReflectiveOperationException {
        ClassLoader loader = ClientAnimationCompatibility.class.getClassLoader();
        Class<?> animation = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation", false, loader);
        Class<?> player = Class.forName("dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer", false, loader);
        Class<?> speed = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier", false, loader);
        Class<?> modifier = Class.forName("dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier", false, loader);
        Class<?> layer = Class.forName("dev.kosmx.playerAnim.api.layered.ModifierLayer", false, loader);
        return new TaczNpcAnimator.ReloadApi(player.getConstructor(animation, int.class),
                animation.getMethod("getLength"), animation.getMethod("isInfinite"), player.getMethod("getStopTick"),
                player.getMethod("getCurrentTick"), speed.getConstructor(float.class),
                speed.getField("speed"), layer.getMethod("addModifierLast", modifier));
    }

    static PlayerAnimatorModelBridge.Access resolveModel() throws ReflectiveOperationException {
        ClassLoader loader = ClientAnimationCompatibility.class.getClassLoader();
        Class<?> mutableModel = Class.forName(
                "dev.kosmx.playerAnim.impl.IMutableModel", false, loader);
        Class<?> supplierType = Class.forName(
                "dev.kosmx.playerAnim.core.util.SetableSupplier", false, loader);
        Method getSupplier = mutableModel.getMethod("getEmoteSupplier");
        Method setSupplier = supplierType.getMethod("set", Object.class);
        return new PlayerAnimatorModelBridge.Access(getSupplier, setSupplier);
    }

    /**
     * TaCZ 1.1.8 exposes no GunDisplayInstance getter for its authored animation ID.
     * ClientAssetsManager has public display lookup, but the resolved default display
     * ID is private too; guessing an ID would lose custom/default gun-pack behavior.
     * Keep this single private contract until an equivalent public API exists.
     */
    public static ResourceLocation reloadAnimationLocation(Object displayInstance) {
        ReloadSoundAccess resolved = resolveReloadSound();
        if (resolved == null) return null;
        try {
            Object display = resolved.display.get(displayInstance);
            return display == null ? null
                    : (ResourceLocation) resolved.animationLocation.invoke(display);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("TaCZ reload sounds", "Cannot read authored reload cues; configured one-shot sounds remain available", exception);
            return null;
        }
    }

    private static ReloadSoundAccess resolveReloadSound() {
        if (!reloadSoundLookupAttempted) synchronized (ClientAnimationCompatibility.class) {
            if (!reloadSoundLookupAttempted) {
                try {
                    ClassLoader loader = ClientAnimationCompatibility.class.getClassLoader();
                    Class<?> instance = Class.forName("com.tacz.guns.client.resource.GunDisplayInstance", false, loader);
                    Class<?> display = Class.forName("com.tacz.guns.client.resource.pojo.display.gun.GunDisplay", false, loader);
                    Field field = instance.getDeclaredField("display");
                    if (field.getType() != display) throw new NoSuchFieldException("GunDisplayInstance.display type changed");
                    field.setAccessible(true);
                    reloadSoundAccess = new ReloadSoundAccess(field, display.getMethod("getAnimationLocation"));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce("TaCZ reload sounds", "Reload cue API is unavailable; configured one-shot sounds remain available", exception);
                }
                reloadSoundLookupAttempted = true;
            }
        }
        return reloadSoundAccess;
    }

    private record ReloadSoundAccess(Field display, Method animationLocation) {}
}
