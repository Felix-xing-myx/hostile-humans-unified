package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/** Registered identity and dimensions only. Combat, growth, drops and ownership remain the ordinary Human modules. */
public final class FactionBossHuman extends Human {
    private final String faction;
    private final int birthTier;
    FactionBossHuman(EntityType<? extends FactionBossHuman> type, Level level, int tier, String faction) {
        super(type, level, tier == 1 ? HumanTier.LEVEL1 : HumanTier.LEVEL2);
        this.faction = faction;
        this.birthTier = tier;
        restoreIdentity();
        // Entity's constructor starts with the unscaled type dimensions; all synchronized
        // data has been defined by this point, so install the pose-dependent bounds once.
        refreshDimensions();
    }
    void restoreIdentity() {
        int tier = birthTier;
        setTier(tier == 1 ? HumanTier.LEVEL1 : HumanTier.LEVEL2);
        getPersistentData().putBoolean(TierThreeHuman.MARKER, tier == 3);
        getPersistentData().putBoolean(HumanFactions.BOSS, true);
        getPersistentData().putString(HumanFactions.FACTION, FactionDefinitions.id(faction).toString());
    }
    String fixedFaction() { return faction; }
    @Override protected net.minecraft.resources.ResourceLocation getDefaultLootTable() {
        return FactionContent.ordinaryType(birthTier).getDefaultLootTable();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions ordinary = super.getDimensions(pose);
        // Keep the larger silhouette/width, but never exceed a two-block doorway.
        // Sleeping and other short poses retain their ordinary proportional height.
        return EntityDimensions.scalable(ordinary.width * HumanFactions.BOSS_BODY_SCALE,
                Math.min(2.0F, ordinary.height * HumanFactions.BOSS_BODY_SCALE));
    }
}
