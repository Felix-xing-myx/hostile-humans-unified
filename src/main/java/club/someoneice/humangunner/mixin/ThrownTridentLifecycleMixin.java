package club.someoneice.humangunner.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bounds the lifetime of Drowned-style Human Gunner trident ammunition. */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentLifecycleMixin {
    @Unique
    private static final String HUMAN_AMMUNITION = "humangunner:drowned_style_trident";
    @Unique
    private static final int MAX_LIFETIME_TICKS = 100;

    // There is intentionally no refmap in this compatibility project. Bind
    // the stable Forge 1.20.1 SRG tick name explicitly.
    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
    private void humanGunner$expireDrownedStyleAmmunition(CallbackInfo ci) {
        ThrownTrident trident = (ThrownTrident) (Object) this;
        if (!trident.getPersistentData().getBoolean(HUMAN_AMMUNITION)) {
            return;
        }
        // Reassert this every tick so another pickup compatibility hook cannot
        // turn the temporary projectile back into a collectible weapon.
        trident.pickup = AbstractArrow.Pickup.DISALLOWED;
        if (!trident.level().isClientSide && trident.tickCount >= MAX_LIFETIME_TICKS) {
            trident.discard();
        }
    }
}
