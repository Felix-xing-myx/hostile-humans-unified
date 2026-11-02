package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.damage.HumanProjectileRelations;
import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableSource;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AreaEffectCloud.class, remap = false)
public abstract class ThrowableAreaEffectCloudMixin {
    @Redirect(method = "m_8119_()V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"), require = 1, allow = 1)
    private <T extends Entity> List<T> hostileHumans$recordedCloudCandidates(Level level, Class<T> type, AABB bounds) {
        Entity source = (Entity) (Object) this;
        List<T> original = level.getEntitiesOfClass(type, bounds);
        return !ThrowableSource.recordedHuman(source) ? original
                : original.stream().filter(entity -> !HumanProjectileRelations.passesThroughEntity(source, entity)).toList();
    }
}
