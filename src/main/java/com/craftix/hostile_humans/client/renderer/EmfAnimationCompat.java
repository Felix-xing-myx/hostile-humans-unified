package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/**
 * Temporarily gives the mod's TaCZ/Better Combat animations priority over
 * EMF resource-pack animations, without making EMF a required dependency.
 */
final class EmfAnimationCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean registrationAttempted;

    private EmfAnimationCompat() {}

    static void registerPauseCondition() {
        if (!ModList.get().isLoaded("entity_model_features")) return;
        synchronized (EmfAnimationCompat.class) {
            if (registrationAttempted) return;
            registrationAttempted = true;
            try {
                ClassLoader loader = EmfAnimationCompat.class.getClassLoader();
                Class<?> api = Class.forName(
                        "traben.entity_model_features.EMFAnimationApi", false, loader);
                Method register = api.getMethod("registerPauseCondition", Function.class);
                Function<Object, Boolean> shouldPause = EmfAnimationCompat::shouldPausePackAnimation;
                Object result = register.invoke(null, shouldPause);
                if (Boolean.TRUE.equals(result)) {
                    LOGGER.info("Registered EMF animation priority for active Human TaCZ/Better Combat animations");
                } else {
                    LOGGER.warn("EMF did not accept the Human animation-priority condition");
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                LOGGER.warn("Could not register optional EMF animation-priority compatibility; resource-pack animations may overlap Human combat animations",
                        exception);
            }
        }
    }

    private static Boolean shouldPausePackAnimation(Object emfEntity) {
        // EMF adds its interface to Entity instances at runtime. Use Object as
        // the bridge type so this class has no compile-time EMF dependency.
        if (!(emfEntity instanceof Human human)) return false;
        return TaczNpcAnimator.hasActiveCustomAnimation(human)
                || BetterCombatNpcAnimator.hasActiveCustomAnimation(human);
    }
}
