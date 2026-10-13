package club.someoneice.humangunner.mixin;

import club.someoneice.humangunner.HumanProjectileRelations;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Filter before native water slowdown/riding/fire-extinguishing effects, not after damage. */
@Pseudo
@Mixin(targets = {"ttv.migami.jeg.entity.Splash", "ttv.migami.jeg.entity.Bubble"}, remap = false)
public abstract class JegWaterAreaCollisionMixin {
    @Shadow public abstract LivingEntity getOwner();
    @Redirect(method = "m_8119_()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45933_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List<Entity> hostileHumans$waterCandidates(Level level, Entity except, AABB box) {
        return level.getEntities(except, box,
                candidate -> !HumanProjectileRelations.passesThroughFromOwner(getOwner(), candidate));
    }
}
