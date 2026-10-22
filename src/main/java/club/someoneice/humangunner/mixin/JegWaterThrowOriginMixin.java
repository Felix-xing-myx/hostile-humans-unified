package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.ThrowableSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.projectile.WhirpoolEntity", remap = false)
public abstract class JegWaterThrowOriginMixin {
    @Redirect(method = "createLandWhirpool", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"), require = 1, allow = 1)
    private static boolean hostileHumans$waterChild(Level level, Entity child, Entity parent, LivingEntity owner, BlockPos position) {
        ThrowableSource.inherit(parent, child);
        return level.addFreshEntity(child);
    }
}
