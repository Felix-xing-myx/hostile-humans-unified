package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.item.VanillaPotionStacks;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ItemStack's Forge size hook delegates to this getter; apply equally on client and server.
 * The common source set has no generated refmap, so use the verified 1.20.1 SRG selector. */
@Mixin(Item.class)
public abstract class VanillaPotionStackSizeMixin {
    @Inject(method = "m_41459_()I", at = @At("RETURN"), cancellable = true,
            remap = false, require = 1, allow = 1)
    private void hostileHumansUnified$potionStackSize(CallbackInfoReturnable<Integer> result) {
        if (VanillaPotionStacks.applies((Item) (Object) this)) result.setReturnValue(VanillaPotionStacks.MAX_SIZE);
    }
}
