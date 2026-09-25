package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

final class IdentityBadgeAccess {
    private static final String RETALIATION_PLAYER = HumanGunner.MOD_ID + ":badge_retaliation_player";
    private static final String RETALIATION_UNTIL = HumanGunner.MOD_ID + ":badge_retaliation_until";
    private static final int RETALIATION_MEMORY_TICKS = 600;

    private IdentityBadgeAccess() {
    }

    static boolean isNeutralTo(Human human, Player player) {
        return relationTo(human, player) != BadgePolicy.Relation.HOSTILE;
    }

    static boolean isFriendlyTo(Human human, Player player) {
        return relationTo(human, player) == BadgePolicy.Relation.FRIENDLY;
    }

    static BadgePolicy.Relation relationTo(Human human, Player player) {
        return BadgePolicy.relation(highestClearance(player), humanRank(human));
    }

    static boolean blocksInitiatedHostility(Human human, Player player) {
        return isNeutralTo(human, player) && !mayRetaliateAgainst(human, player);
    }

    static void rememberPlayerAttack(Human human, Player player) {
        if (!isNeutralTo(human, player)) {
            return;
        }
        human.getPersistentData().putUUID(RETALIATION_PLAYER, player.getUUID());
        human.getPersistentData().putInt(RETALIATION_UNTIL, human.tickCount + RETALIATION_MEMORY_TICKS);
    }

    static boolean mayRetaliateAgainst(Human human, Player player) {
        return human.getPersistentData().hasUUID(RETALIATION_PLAYER)
                && human.getPersistentData().getUUID(RETALIATION_PLAYER).equals(player.getUUID())
                && human.getPersistentData().getInt(RETALIATION_UNTIL) >= human.tickCount;
    }

    static int highestClearance(Player player) {
        int clearance = -1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof IdentityBadgeItem badge) {
                clearance = Math.max(clearance, badge.clearance());
            }
        }
        if (ModList.get().isLoaded("curios")) {
            clearance = Math.max(clearance, CuriosBadgeAccess.highestClearance(player));
        }
        return clearance;
    }

    static int humanRank(Human human) {
        if (TierThreeHuman.isTierThree(human)) {
            return 3;
        }
        if (human.getTier() == HumanTier.LEVEL2) {
            return 2;
        }
        if (human.getTier() == HumanTier.LEVEL1) {
            return 1;
        }
        return 0;
    }
}
