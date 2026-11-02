package dev.felix.hostilehumans.damage;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableEffectContext;
import dev.felix.hostilehumans.behavior.combat.throwable.ThrowableSource;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.jeg.JegCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.faction.HumanRelationshipPolicy;

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
    public static void onProjectileExplosion(ExplosionEvent.Detonate event) {
        Entity direct = event.getExplosion().getDirectSourceEntity();
        if (direct == null) direct = ThrowableEffectContext.source();
        final Entity origin = direct;
        Entity nativeOwner = JegCompat.projectileOwner(direct);
        Entity attacker = nativeOwner != null ? nativeOwner : event.getExplosion().getIndirectSourceEntity();
        if (!(attacker instanceof Player) && !(direct instanceof Projectile) && nativeOwner == null
                && !ThrowableSource.recordedHuman(direct)) return;
        event.getAffectedEntities().removeIf(candidate -> ThrowableSource.protectsWithoutOwner(origin, candidate)
                || HumanOwnerProtection.protects(candidate, attacker)
                || (nativeOwner != null && passesThroughFromOwner(nativeOwner, candidate))
                || (origin instanceof Projectile projectile && passesThrough(projectile, candidate)));
    }

    /** Protect both owner/soldier directions before hurt feedback and retaliation listeners. */
    public static void onProtectedPlayerAttack(LivingAttackEvent event) {
        if (ThrowableEffectContext.protects(event.getEntity())
                || ThrowableSource.protectsWithoutOwner(event.getSource().getDirectEntity(), event.getEntity())) {
            event.setCanceled(true); return;
        }
        if (event.getEntity() instanceof Human human && HumanOwnerProtection.blocksDamage(human, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Human shooter
                && event.getEntity() instanceof Player player && protectsPlayer(shooter, player))
            event.setCanceled(true);
    }

    public static boolean passesThrough(Projectile projectile, Entity candidate) {
        return passesThroughEntity(projectile, candidate);
    }
    /** Native area effects reuse their original victim query; never scan for a replacement owner. */
    public static boolean passesThroughEntity(Entity source, Entity candidate) {
        if (ThrowableSource.recordedHuman(source)
                && source.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return true;
        return passesThroughFromOwner(ThrowableSource.nativeOwner(source), candidate)
                || ThrowableSource.protectsWithoutOwner(source, candidate);
    }

    /** Some native projectile APIs expose a shooter without setting vanilla owner. */
    public static boolean passesThroughFromOwner(Entity owner, Entity candidate) {
        if (HumanOwnerProtection.protects(candidate, owner)) return true;
        if (!(owner instanceof Human shooter)) return false;
        if (candidate instanceof Player player) {
            return protectsPlayer(shooter, player);
        }
        if (!(candidate instanceof Human target)) return false;
        // Damage permissions are directional. Both directions must agree: a wild
        // same-tier human can attack a hired one even before its first hit.
        return HumanRuntime.areCombatAllies(shooter, target) && HumanRuntime.areCombatAllies(target, shooter);
    }
}
