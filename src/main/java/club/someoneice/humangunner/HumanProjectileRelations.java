package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** Decide before collision: cancelling damage afterwards cannot undo fire or hit feedback. */
public final class HumanProjectileRelations {
    private HumanProjectileRelations() {}

    public static boolean passesThrough(Projectile projectile, Entity candidate) {
        if (!(projectile.getOwner() instanceof Human shooter) || !(candidate instanceof Human target)) return false;
        // Damage permissions are directional. Both directions must agree: a wild
        // same-tier human can attack a hired one even before its first hit.
        return HumanGunner.areCombatAllies(shooter, target) && HumanGunner.areCombatAllies(target, shooter);
    }
}
