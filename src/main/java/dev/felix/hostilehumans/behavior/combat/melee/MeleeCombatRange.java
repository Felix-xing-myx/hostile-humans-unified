package dev.felix.hostilehumans.behavior.combat.melee;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.HumanTurnControl;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The current weapon's reach, shared by attack checks and movement decisions. */
public final class MeleeCombatRange {
    private MeleeCombatRange() {}

    public static double reach(Human human, LivingEntity target) {
        double base = BetterCombatMeleeCombat.hasWeaponProfile(human)
                ? BetterCombatMeleeCombat.attackRange(human)
                : human.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        if (!Double.isFinite(base)) base = 3.0D;
        base = Math.max(0.1D, Math.min(16.0D, base));
        return base + target.getBbWidth() * 0.5D;
    }

    public static double reachSqr(Human human, LivingEntity target) {
        double reach = reach(human, target);
        return reach * reach;
    }

    /** Movement may stop only where this weapon can actually hit, not just within nominal reach. */
    public static boolean canStrike(Human human, LivingEntity target) {
        return human.hhu$canMeleeAttack() && canReachTarget(human, target);
    }

    /** Predict reach without granting permission to lower a shield or start an attack. */
    public static boolean canReachTarget(Human human, LivingEntity target) {
        if (GunSupport.isGunItem(human.getMainHandItem())
                || target == null || !target.isAlive() || !human.canAttack(target)) return false;
        if (BetterCombatMeleeCombat.backend(human) == BetterCombatMeleeCombat.Backend.WAIT) return false;
        if (BetterCombatMeleeCombat.hasWeaponProfile(human))
            return BetterCombatMeleeCombat.canStrike(human, target);
        double actualReach = human.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        if (!Double.isFinite(actualReach)) actualReach = 3;
        actualReach = Math.max(.1, Math.min(16, actualReach));
        Vec3 origin = attackOrigin(human);
        Vec3 nearest = new Vec3(Mth.clamp(origin.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
                Mth.clamp(origin.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
                Mth.clamp(origin.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
        return nearest.distanceToSqr(origin) <= actualReach * actualReach && human.getSensing().hasLineOfSight(target);
    }

    /** One pre-animation gate for native attacks and temporary defensive counters. */
    public static boolean canStartAttack(Human human, LivingEntity target) {
        if (target == null || !target.isAlive() || !human.canAttack(target)
                || !MeleeActionControl.movementAllowsMelee(human) || PursuitJumpAttack.awaitingAttackWindow(human) || !NpcDualWield.prepare(human) || !NpcDualWield.layoutReady(human)
                || !human.hhu$canMeleeAttack() || human.hhu$hasPendingMeleeAttack()
                || human.hhu$shieldDefense().shouldSuppressRecovery()) return false;
        if (BetterCombatMeleeCombat.hasWeaponProfile(human)) faceTarget(human, target);
        return canStrike(human, target);
    }

    /** Aim the active melee hitbox before testing it, including opponents above/below us. */
    public static void faceTarget(Human human, LivingEntity target) {
        if (dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.live(human)) return; // The owner's actual view orients manual hitboxes.
        if (!HumanPerception.allowCombatLook(human,target)) return;
        Vec3 point = aimPoint(attackOrigin(human), target.getBoundingBox());
        var delta = point.subtract(attackOrigin(human));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = horizontal < 1.0E-6D ? human.getYRot()
                : (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        HumanTurnControl.action(human,yaw,pitch);
        human.getLookControl().setLookAt(point.x, point.y, point.z, 75.0F, 90.0F);
        human.markMeleeFacing(target, yaw, pitch);
    }

    public static Vec3 attackOrigin(Human human) {
        return human.position().add(0.0D, human.getBbHeight() * 0.85D, 0.0D);
    }

    /** Aim inside the nearest hittable part, not an unreachable tall entity's center. */
    public static Vec3 aimPoint(Vec3 origin, AABB box) {
        double ix = Math.min(0.1D, box.getXsize() * 0.25D);
        double iy = Math.min(0.1D, box.getYsize() * 0.25D);
        double iz = Math.min(0.1D, box.getZsize() * 0.25D);
        return new Vec3(Mth.clamp(origin.x, box.minX + ix, box.maxX - ix),
                Mth.clamp(origin.y, box.minY + iy, box.maxY - iy),
                Mth.clamp(origin.z, box.minZ + iz, box.maxZ - iz));
    }
}
