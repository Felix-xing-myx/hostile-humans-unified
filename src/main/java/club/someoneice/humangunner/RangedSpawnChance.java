package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;

/** One ranged-loadout roll per newly generated human, regardless of weapon provider. */
public final class RangedSpawnChance {
    private RangedSpawnChance() {
    }

    public static float forHuman(Human human) {
        if (TierThreeHuman.isTierThree(human)) {
            return 1.0F;
        }
        return switch (human.getTier()) {
            case ROAMER -> 0.20F;
            case LEVEL1 -> 0.40F;
            case LEVEL2 -> 0.70F;
        };
    }
}
