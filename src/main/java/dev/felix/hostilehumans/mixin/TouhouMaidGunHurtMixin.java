package dev.felix.hostilehumans.mixin;

import net.minecraftforge.event.level.ExplosionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The built-in TaCZ compatibility removes every maid from a kinetic-bullet
 * explosion before damage is calculated. Let the normal damage path run so
 * wild maids take full damage and Human Gunner's MaidHurtEvent listener can
 * apply the configured 15% remainder to tamed maids exactly once.
 */
@Pseudo
@Mixin(
        targets = "com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.event.GunHurtMaidEvent",
        remap = false
)
public abstract class TouhouMaidGunHurtMixin {
    @Inject(method = dev.felix.hostilehumans.compat.OptionalMixinPlugin.MAID_EXPLOSION,
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void hhu$keepMaidsInTaczExplosion(ExplosionEvent.Detonate event, CallbackInfo ci) {
        if (dev.felix.hostilehumans.behavior.combat.gun.GunSupport.get().enabled()) ci.cancel();
    }
}
