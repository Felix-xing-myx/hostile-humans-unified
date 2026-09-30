package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeMod;

/** The current weapon's reach, shared by attack checks and movement decisions. */
public final class MeleeCombatRange {
    private MeleeCombatRange() {}

    public static double reach(Human human, LivingEntity target) {
        double base = BetterCombatMeleeCombat.hasWeaponProfile(human)
                ? BetterCombatMeleeCombat.attackRange(human)
                : human.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        if (!Double.isFinite(base)) base = 3.0D;
        base = Math.max(1.0D, Math.min(16.0D, base));
        return base + target.getBbWidth() * 0.5D;
    }

    public static double reachSqr(Human human, LivingEntity target) {
        double reach = reach(human, target);
        return reach * reach;
    }
}
