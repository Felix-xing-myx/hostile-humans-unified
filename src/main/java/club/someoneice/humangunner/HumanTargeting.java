package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Shared classification for autonomous attacks against vanilla and modded enemies. */
public final class HumanTargeting {
    private HumanTargeting() {}

    public static boolean isAutonomousPlayerEnemy(Human observer, LivingEntity candidate) {
        if (!(candidate instanceof Mob mob) || candidate == observer
                || !SoldierCombatMode.allowsAutonomousTargeting(observer)) return false;
        if (candidate instanceof Human wild) {
            return isAutonomousWildEnemy(observer, wild);
        }
        return mob instanceof Enemy
                || mob.getType().getCategory() == MobCategory.MONSTER
                || mob.getTarget() instanceof Player;
    }

    /** Active recruits may initiate combat with wild humans hostile to their owner. */
    public static boolean isAutonomousWildEnemy(Human observer, Human wild) {
        if (wild == observer || !wild.isAlive() || !observer.hasOwner()
                || SoldierCombatMode.get(observer) != SoldierCombatMode.ACTIVE) return false;
        Player owner = observer.level().getPlayerByUUID(observer.getOwnerUUID());
        if (owner == null || !owner.isAlive()) return false;
        return HumanRelationshipPolicy.activeRecruitMayAttackWild(
                observer.hasOwner(), SoldierCombatMode.get(observer) == SoldierCombatMode.ACTIVE,
                HumanRelations.effectiveOwner(wild) == null,
                IdentityBadgeAccess.highestClearance(owner) >= 0,
                HumanRelations.isForcedHostileTo(wild, owner));
    }
}
