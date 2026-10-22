package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.ThrowableEffectContext;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "top.ribs.scguns.common.ChokeBombCloud", remap = false)
public abstract class ScgunsChokeVictimFilterMixin {
    @Redirect(method = "applyChokeEffects(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;D)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private static <T extends Entity> List<T> hostileHumans$nativeGasCandidates(Level level, Class<T> type, AABB bounds) {
        List<T> original = level.getEntitiesOfClass(type, bounds);
        return ThrowableEffectContext.source() == null ? original
                : original.stream().filter(entity -> !ThrowableEffectContext.protects(entity)).toList();
    }
}
