package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A separately registered elite human while retaining Hostile Humans' proven AI and renderer. */
public final class TierThreeHuman extends Human {
    public static final String MARKER = HumanGunner.MOD_ID + ":tier_three";

    public TierThreeHuman(EntityType<? extends TierThreeHuman> type, Level level) {
        super(type, level, HumanTier.LEVEL2);
        getPersistentData().putBoolean(MARKER, true);
        team = "human_level3";
    }

    public static boolean isTierThree(Human human) {
        return human instanceof TierThreeHuman || human.getPersistentData().getBoolean(MARKER);
    }
}
