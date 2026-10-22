package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.ThrowableSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "top.ribs.scguns.entity.throwable.ThrowableSwarmBombEntity", remap = false)
public abstract class ScgunsSwarmThrowOriginMixin {
    @Redirect(method = "onDeath", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"), require = 2, allow = 2)
    private boolean hostileHumans$swarmChild(Level level, Entity child) {
        ThrowableSource.inherit((Entity) (Object) this, child);
        return level.addFreshEntity(child);
    }
}
