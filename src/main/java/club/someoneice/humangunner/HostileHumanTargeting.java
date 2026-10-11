package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.registries.ForgeRegistries;

/** Adds target discovery only. The original mob remains responsible for attacks and movement. */
public final class HostileHumanTargeting {
    private HostileHumanTargeting() {}

    static void onJoin(Mob mob) {
        if (mob.level().isClientSide || !eligible(mob)) return;
        // Inspect the live selector, never a saved tag: goals are reconstructed on chunk reload.
        if (mob.targetSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof HumanTargetGoal)) return;
        // Native retaliation/player/task targets keep their priority. This goal has no MOVE flag.
        mob.targetSelector.addGoal(10, new HumanTargetGoal(mob));
    }

    private static boolean eligible(Mob mob) {
        var config = UnifiedConfig.get().hostileTargeting();
        if (!config.enabled() || mob instanceof Human || mob instanceof OwnableEntity) return false;
        var id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (id == null || config.excludedEntities().contains(id.toString())) return false;
        boolean included = config.includedEntities().contains(id.toString());
        // NeutralMob includes unprovoked endermen and zombified piglins: not generally hostile to players.
        return included || (mob instanceof Enemy && !(mob instanceof NeutralMob));
    }

    private static boolean canTarget(Mob attacker, Human target) {
        return target.isAlive() && !target.isRemoved() && target.level() == attacker.level()
                && !attacker.isAlliedTo(target) && !target.isAlliedTo(attacker)
                && attacker.canAttack(target);
    }

    private static final class HumanTargetGoal extends NearestAttackableTargetGoal<Human> {
        HumanTargetGoal(Mob mob) {
            // Native random polling and visibility checks; ranged mobs need not walk to their target.
            super(mob, Human.class, 10, true, false,
                    candidate -> candidate instanceof Human human && canTarget(mob, human));
        }

        @Override protected double getFollowDistance() {
            // A gun mod may grant huge follow range; do not turn that into an unbounded new query.
            return Math.min(32, super.getFollowDistance());
        }

        @Override public boolean canUse() {
            return !mob.isNoAi() && eligible(mob)
                    && (mob.getTarget() == null || !mob.getTarget().isAlive()) && super.canUse();
        }

        @Override public boolean canContinueToUse() {
            return !mob.isNoAi() && eligible(mob) && mob.getTarget() instanceof Human human
                    && canTarget(mob, human) && super.canContinueToUse();
        }
    }
}
