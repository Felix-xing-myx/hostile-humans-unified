package club.someoneice.humangunner.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Spartan Weaponry selects structure-spawned mob equipment with Level.random.
 * C2ME legitimately generates and places structures on worker threads, while
 * that random source is owned by the server thread. Accessing it there aborts
 * the chunk future and can leave loading and saving waiting indefinitely.
 *
 * Keep Spartan Weaponry's original path on the server thread. Only its unsafe
 * asynchronous call is replaced with the same uniform item selection using a
 * fresh random source owned exclusively by the current invocation.
 */
@Pseudo
@Mixin(targets = "com.oblivioussp.spartanweaponry.util.ItemRandomizer", remap = false)
public abstract class SpartanWeaponryItemRandomizerMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true, require = 0)
    private static void humanGunner$useWorkerLocalRandom(
            Level level,
            List<Item> items,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || serverLevel.getServer().isSameThread()
                || items.isEmpty()) {
            return;
        }

        RandomSource workerRandom = RandomSource.create();
        cir.setReturnValue(new ItemStack(items.get(workerRandom.nextInt(items.size()))));
    }
}
