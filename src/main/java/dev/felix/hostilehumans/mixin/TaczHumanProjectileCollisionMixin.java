package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.damage.HumanProjectileRelations;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Filter the candidate list after native/Arcana broad-phase queries, before sorting and piercing.
 * Arcana redirects Level.getEntities to extend its proximity-fuze search; leave that call intact.
 * Local slots are verified against supported TaCZ bytecode by the compatibility audit.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczHumanProjectileCollisionMixin {
    @ModifyVariable(method = "findEntityOnPath", at = @At("STORE"), index = 6, require = 1, allow = 1)
    private static List<Entity> hhu$filterSingle(List<Entity> candidates, Projectile projectile, Vec3 start, Vec3 end) {
        return hhu$filter(candidates,projectile);
    }
    @ModifyVariable(method = "findEntitiesOnPath", at = @At("STORE"), index = 4, require = 1, allow = 1)
    private static List<Entity> hhu$filterMultiple(List<Entity> candidates, Projectile projectile, Vec3 start, Vec3 end) {
        return hhu$filter(candidates,projectile);
    }
    private static List<Entity> hhu$filter(List<Entity> candidates,Projectile projectile) {
        List<Entity> filtered=new ArrayList<>(candidates);
        filtered.removeIf(candidate->HumanProjectileRelations.passesThrough(projectile,candidate));
        return filtered;
    }
}
