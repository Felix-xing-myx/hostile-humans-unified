package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.InventoryTotemProtection;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply Human recovery only after vanilla/Forge has accepted and consumed a held totem. */
@Mixin(LivingEntity.class)
public abstract class HumanTotemRecoveryMixin {
    @Inject(method = "m_21262_(Lnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("RETURN"), remap = false, require = 1)
    private void hostileHumansUnified$finishTotemRecovery(DamageSource source,
                                                         CallbackInfoReturnable<Boolean> result) {
        if (Boolean.TRUE.equals(result.getReturnValue()) && (Object) this instanceof Human human)
            InventoryTotemProtection.applyRecovery(human);
    }
}
