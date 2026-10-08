package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanProjectileRelations;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TaCZ searches its own hit list without Projectile.canHitEntity; filter before sorting/piercing. */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczHumanProjectileCollisionMixin {
    @Redirect(method = {"findEntityOnPath", "findEntitiesOnPath"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6249_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            require = 2)
    private static List<Entity> humanGunner$filterBulletCandidates(Level level, Entity except, AABB bounds,
            Predicate<Entity> predicate, Projectile projectile, Vec3 start, Vec3 end) {
        return level.getEntities(except, bounds,
                predicate.and(candidate -> !HumanProjectileRelations.passesThrough(projectile, candidate)));
    }
}
