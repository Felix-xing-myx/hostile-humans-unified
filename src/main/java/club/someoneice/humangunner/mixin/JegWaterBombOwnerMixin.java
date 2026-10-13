package club.someoneice.humangunner.mixin;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native hand-thrown water bombs pass null; retain the Human cause for allied effects filtering. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.throwable.ThrowableWaterBombEntity", remap = false)
public abstract class JegWaterBombOwnerMixin {
    @ModifyArg(method = "onDeath", at = @At(value = "INVOKE",
            target = "Lttv/migami/jeg/entity/projectile/WhirpoolEntity;createLandWhirpool(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/BlockPos;)V"),
            index = 1, require = 1, allow = 1)
    private LivingEntity hostileHumans$keepWaterBombOwner(LivingEntity original) {
        var owner = ((Projectile) (Object) this).getOwner();
        return owner instanceof Human human ? human : original;
    }
}
