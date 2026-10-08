package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.level.ExplosionEvent;

/** Decide before collision: cancelling damage afterwards cannot undo fire or hit feedback. */
public final class HumanProjectileRelations {
    private HumanProjectileRelations() {}

    static boolean protectsPlayer(Human shooter, Player player) {
        return HumanRelationshipPolicy.ownedHumanAlliedToPlayer(HumanRelations.effectiveOwner(shooter),
                player.getUUID(), UnifiedConfig.get().allowHiredPvpDamage());
    }

    /** Explosion hit lists are independent of projectile collision candidates. */
    static void onProjectileExplosion(ExplosionEvent.Detonate event) {
        if (event.getExplosion().getDirectSourceEntity() instanceof Projectile projectile)
            event.getAffectedEntities().removeIf(candidate -> passesThrough(projectile, candidate));
    }

    /** Stop owner-attributed damage before hurt feedback and retaliation listeners. */
    static void onProtectedPlayerAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Human shooter
                && event.getEntity() instanceof Player player && protectsPlayer(shooter, player))
            event.setCanceled(true);
    }

    public static boolean passesThrough(Projectile projectile, Entity candidate) {
        if (!(projectile.getOwner() instanceof Human shooter)) return false;
        if (candidate instanceof Player player) {
            return protectsPlayer(shooter, player);
        }
        if (!(candidate instanceof Human target)) return false;
        // Damage permissions are directional. Both directions must agree: a wild
        // same-tier human can attack a hired one even before its first hit.
        return HumanGunner.areCombatAllies(shooter, target) && HumanGunner.areCombatAllies(target, shooter);
    }
}
