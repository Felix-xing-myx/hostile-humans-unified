package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** A bounded, read-only forecast for the current melee target only. No attack or path creation. */
public final class CreeperMeleeCommitment {
    private CreeperMeleeCommitment() {}

    public static boolean canFinish(Human human, Creeper creeper) {
        if (human.level().isClientSide || human.getTarget() != creeper || !human.isAlive()
                || !creeper.isAlive() || creeper.isRemoved() || !human.canAttack(creeper)
                || !CreeperSafetyPolicy.imminent(creeper.isIgnited(), creeper.swell, creeper.getSwellDir())
                || !human.humanGunner$canMeleeAttack() || human.isUsingItem()
                || human.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                || !human.getSensing().hasLineOfSight(creeper)) return false;
        int approach = approachTicks(human, creeper);
        if (approach < 0) return false;
        boolean betterCombat = BetterCombatMeleeCombat.hasWeaponProfile(human);
        var forecast = betterCombat ? BetterCombatMeleeCombat.creeperForecast(human, creeper) : null;
        var profile = betterCombat ? forecast.subsequent() : null;
        int cooldown = betterCombat ? profile.cooldownTicks() : TierAttributes.of(human).meleeCooldownMax();
        int upswing = betterCombat ? Math.max(1, (int) Math.ceil(cooldown * profile.animationUpswing())) : 0;
        int pending = human.humanGunner$pendingMeleeTicks(creeper);
        if (human.humanGunner$hasPendingMeleeAttack() && pending < 0) return false;
        if (pending >= 0 && approach > pending) return false;
        int firstImpact = pending >= 0 ? pending
                : Math.max(approach, MeleeAttackTiming.remainingCooldown(human)) + upswing + 2;
        boolean bypassesCooldown = HumanAttackCooldownPolicy.bypassesCooldown(creeper, human.damageSources().mobAttack(human));
        if (!bypassesCooldown) firstImpact = Math.max(firstImpact, Math.max(0, creeper.invulnerableTime - 10));

        // Deliberately omit speculative jump criticals, fire ticks and allies' damage.
        double firstDamage = damageAfterProtection(human, creeper, betterCombat ? forecast.firstDamageMultiplier() : 1);
        double subsequentDamage = damageAfterProtection(human, creeper, betterCombat ? profile.damageMultiplier() : 1);
        return CreeperFinishPolicy.canFinish(creeper.getHealth() + creeper.getAbsorptionAmount(), firstDamage,
                subsequentDamage, firstImpact, cooldown, Math.max(0, creeper.maxSwell - creeper.swell), bypassesCooldown);
    }

    private static double damageAfterProtection(Human human, Creeper creeper, double weaponMultiplier) {
        double damage = human.getAttributeValue(Attributes.ATTACK_DAMAGE) * weaponMultiplier;
        damage += EnchantmentHelper.getDamageBonus(human.getMainHandItem(), creeper.getMobType());
        float scaled = HumanDamageScaling.outgoing(human, human.damageSources().mobAttack(human), (float) damage);
        scaled = CombatRules.getDamageAfterAbsorb(scaled, creeper.getArmorValue(),
                (float) creeper.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        var resistance = creeper.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistance != null) scaled *= Math.max(0, 1 - .2F * (resistance.getAmplifier() + 1));
        scaled = CombatRules.getDamageAfterMagicAbsorb(scaled,
                EnchantmentHelper.getDamageProtection(creeper.getArmorSlots(), human.damageSources().mobAttack(human)));
        return scaled;
    }

    private static int approachTicks(Human human, Creeper creeper) {
        if (MeleeCombatRange.canStrike(human, creeper)) return 0;
        // No optimistic straight-line estimate across water, a ledge or an unreachable endpoint.
        if (!human.onGround() || human.isInWater() || Math.abs(human.getY() - creeper.getY()) > 1) return -1;
        Path path = human.getNavigation().getPath();
        if (path == null || path.isDone() || !path.canReach() || path.getEndNode() == null
                || path.getNodeCount() - path.getNextNodeIndex() > 12) return -1;
        double reach = MeleeCombatRange.reach(human, creeper);
        if (Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceTo(creeper.position()) > reach) return -1;
        Vec3 previous = human.position();
        double distance = 0;
        boolean reached = false;
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Vec3 next = path.getEntityPosAtNode(human, i);
            if (Math.abs(next.y - previous.y) > 1) return -1;
            distance += previous.distanceTo(next);
            previous = next;
            if (previous.distanceTo(creeper.position()) <= reach) {
                reached = true;
                break;
            }
        }
        if (!reached) return -1;
        int ticks = (int) Math.ceil(distance / Math.max(.02, human.getAttributeValue(Attributes.MOVEMENT_SPEED) * .7));
        return ticks <= 12 ? ticks : -1;
    }
}
