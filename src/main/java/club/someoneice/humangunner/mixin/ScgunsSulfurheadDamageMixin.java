package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Restore the normal damage pipeline for Humans; never impersonate their owner. */
@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.monster.SulfurheadEntity", remap = false)
public abstract class ScgunsSulfurheadDamageMixin extends Monster {
    protected ScgunsSulfurheadDamageMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = club.someoneice.humangunner.compat.OptionalMixinPlugin.SCGUNS_SULFURHEAD_DAMAGE,
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void hostileHumansUnified$allowHumanDamage(DamageSource source, float amount,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!level().isClientSide && source.getEntity() instanceof Human) {
            // The inspected override only filters the attacker, then calls Monster.hurt.
            // Keep Forge events, invulnerability, armor, effects, attribution and death intact.
            cir.setReturnValue(super.hurt(source, amount));
        }
    }
}
