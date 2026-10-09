package com.craftix.hostile_humans.client.renderer;

import java.lang.reflect.Method;
import net.minecraft.client.model.HumanoidModel;
import net.minecraftforge.fml.ModList;

/** Connects NPC animation playback to Player Animator's model/armor bend pipeline. */
final class PlayerAnimatorModelBridge {
    private static volatile Access access;
    private static volatile boolean lookupAttempted;

    private PlayerAnimatorModelBridge() {}

    static void bind(HumanoidModel<?> model, Object animationProcessor) {
        if (!ModList.get().isLoaded("playeranimator")) return;
        Access resolved = resolve();
        if (resolved == null) return;
        try {
            Object supplier = resolved.getSupplier.invoke(model);
            if (supplier != null) resolved.setSupplier.invoke(supplier, animationProcessor);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce("Could not connect the NPC animation to Player Animator's model pipeline", exception);
        }
    }

    private static Access resolve() {
        if (!lookupAttempted) synchronized (PlayerAnimatorModelBridge.class) {
            if (!lookupAttempted) {
                try {
                    access = ClientAnimationCompatibility.resolveModel();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce("Player Animator's model deformation API is unavailable", exception);
                }
                lookupAttempted = true;
            }
        }
        return access;
    }

    private static void warnOnce(String message, Throwable exception) {
        ClientAnimationCompatibility.warnOnce("PlayerAnimatorModelBridge", message, exception);
    }

    record Access(Method getSupplier, Method setSupplier) {}
}
