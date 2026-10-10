package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

/** Owner-to-soldier protection is independent of soldier-to-player PvP permissions. */
public final class HumanOwnerProtection {
    private HumanOwnerProtection() {}

    public static boolean protects(Entity target, Entity attacker) {
        return target instanceof Human human && attacker instanceof Player player
                && HumanRelations.isOwnedBy(human, player);
    }

    public static boolean blocksDamage(Human target, DamageSource source) {
        if (protects(target, source.getEntity())) return true;
        // Some indirect damage sources omit the causing entity but retain the projectile.
        return source.getDirectEntity() instanceof Projectile projectile
                && protects(target, projectile.getOwner());
    }

    static void onPlayerAttack(AttackEntityEvent event) {
        if (protects(event.getTarget(), event.getEntity())) event.setCanceled(true);
    }
}
