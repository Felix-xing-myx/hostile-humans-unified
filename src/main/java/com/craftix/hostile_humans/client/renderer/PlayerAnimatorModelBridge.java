package com.craftix.hostile_humans.client.renderer;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.minecraft.client.model.HumanoidModel;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** Connects NPC animation playback to Player Animator's model/armor bend pipeline. */
final class PlayerAnimatorModelBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Access access;
    private static volatile boolean lookupAttempted;
    private static volatile boolean warned;

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
                    ClassLoader loader = PlayerAnimatorModelBridge.class.getClassLoader();
                    Class<?> mutableModel = Class.forName(
                            "dev.kosmx.playerAnim.impl.IMutableModel", false, loader);
                    Class<?> supplierType = Class.forName(
                            "dev.kosmx.playerAnim.core.util.SetableSupplier", false, loader);
                    Method getSupplier = mutableModel.getMethod("getEmoteSupplier");
                    Method setSupplier = supplierType.getMethod("set", Object.class);
                    access = new Access(getSupplier, setSupplier);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                    warnOnce("Player Animator's model deformation API is unavailable", exception);
                }
                lookupAttempted = true;
            }
        }
        return access;
    }

    private static void warnOnce(String message, Throwable exception) {
        if (!warned) synchronized (PlayerAnimatorModelBridge.class) {
            if (!warned) {
                LOGGER.warn(message, exception);
                warned = true;
            }
        }
    }

    private record Access(Method getSupplier, Method setSupplier) {}
}
