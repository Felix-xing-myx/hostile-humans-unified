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
        Entity direct = event.getExplosion().getDirectSourceEntity();
        Entity attacker = event.getExplosion().getIndirectSourceEntity();
        if (!(attacker instanceof Player) && !(direct instanceof Projectile)) return;
        event.getAffectedEntities().removeIf(candidate -> HumanOwnerProtection.protects(candidate, attacker)
                || (direct instanceof Projectile projectile && passesThrough(projectile, candidate)));
    }

    /** Protect both owner/soldier directions before hurt feedback and retaliation listeners. */
    static void onProtectedPlayerAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Human human && HumanOwnerProtection.blocksDamage(human, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Human shooter
                && event.getEntity() instanceof Player player && protectsPlayer(shooter, player))
            event.setCanceled(true);
    }

    public static boolean passesThrough(Projectile projectile, Entity candidate) {
        if (HumanOwnerProtection.protects(candidate, projectile.getOwner())) return true;
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
