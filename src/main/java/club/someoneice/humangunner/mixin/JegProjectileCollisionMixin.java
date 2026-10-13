package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanProjectileRelations;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** JEG bullets extend Entity, not Projectile. Filter direct and collateral queries before impact. */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.entity.projectile.ProjectileEntity", remap = false)
public abstract class JegProjectileCollisionMixin {
    @Shadow public abstract LivingEntity getShooter();
    @Redirect(method = {"findEntityOnPath", "findEntitiesOnPath"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 3, allow = 3)
    private List<Entity> hostileHumans$filterCandidates(Level level, Entity except, AABB bounds, Predicate<Entity> predicate) {
        return level.getEntities(except, bounds,
                predicate.and(candidate -> !HumanProjectileRelations.passesThroughFromOwner(getShooter(), candidate)));
    }
}
