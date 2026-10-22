package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanProjectileRelations;
import club.someoneice.humangunner.ThrowableSource;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ThrownPotion.class, remap = false)
public abstract class ThrowableSplashPotionMixin {
    @Redirect(method = "m_37547_(Ljava/util/List;Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"), require = 1, allow = 1)
    private <T extends Entity> List<T> hostileHumans$recordedSplashCandidates(Level level, Class<T> type, AABB bounds) {
        Entity source = (Entity) (Object) this;
        List<T> original = level.getEntitiesOfClass(type, bounds);
        return !ThrowableSource.recordedHuman(source) ? original
                : original.stream().filter(entity -> !HumanProjectileRelations.passesThroughEntity(source, entity)).toList();
    }
    @Redirect(method = "m_37537_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/alchemy/Potion;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z"), require = 1, allow = 1)
    private boolean hostileHumans$recordedLingeringChild(Level level, Entity child) {
        ThrowableSource.inherit((Entity) (Object) this, child);
        return level.addFreshEntity(child);
    }
}
