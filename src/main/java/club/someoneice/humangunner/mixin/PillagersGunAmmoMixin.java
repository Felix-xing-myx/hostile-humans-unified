package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanGunner;
import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pillagers Gun normally treats every pair with the same entity type as allies.
 * All Hostile Humans tiers share one entity type, so that blanket rule removes
 * TaCZ bullets before their damage event can run. Only override the answer for
 * Human pairs that Human Gunner intentionally considers hostile.
 */
@Pseudo
@Mixin(targets = "com.scarasol.pillagers_gun.entity.projectile.Ammo", remap = false)
public abstract class PillagersGunAmmoMixin {
    @Inject(method = "checkFriendlyFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void humanGunner$allowHostileHumanGunfire(
            Entity target,
            Entity shooter,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (target instanceof Human targetHuman
                && shooter instanceof Human shooterHuman
                && !HumanGunner.areCombatAllies(shooterHuman, targetHuman)) {
            cir.setReturnValue(false);
        }
    }
}
