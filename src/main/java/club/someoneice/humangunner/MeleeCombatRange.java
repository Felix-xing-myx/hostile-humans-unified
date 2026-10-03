package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
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
        if (target == null || !target.isAlive() || !human.canAttack(target)) return false;
        if (BetterCombatMeleeCombat.hasWeaponProfile(human))
            return BetterCombatMeleeCombat.canStrike(human, target);
        return human.distanceToSqr(target) <= reachSqr(human, target)
                && human.getSensing().hasLineOfSight(target);
    }

    /** Aim the active melee hitbox before testing it, including opponents above/below us. */
    public static void faceTarget(Human human, LivingEntity target) {
        Vec3 point = aimPoint(attackOrigin(human), target.getBoundingBox());
        var delta = point.subtract(attackOrigin(human));
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = horizontal < 1.0E-6D ? human.getYRot()
                : (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        yaw = Mth.approachDegrees(human.getYRot(), yaw, 75.0F);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        human.setYRot(yaw);
        human.setYHeadRot(yaw);
        human.setYBodyRot(yaw);
        boolean betterCombat = BetterCombatMeleeCombat.hasWeaponProfile(human);
        pitch = betterCombat ? pitch : Mth.approachDegrees(human.getXRot(), pitch, 35.0F);
        human.setXRot(pitch);
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
