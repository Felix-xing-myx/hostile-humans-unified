package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.Locale;

/** Server-owned preference; Forge persistent data preserves it with the human. */
public enum CombatPreference {
    DEFAULT, GUN, BOW, CROSSBOW, TRIDENT, MELEE;
    private static final String KEY = "hostile_humans_unified:combat_preference";
    public static CombatPreference get(Human human) {
        try { return valueOf(human.getPersistentData().getString(KEY).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return DEFAULT; }
    }
    public static void set(Human human, CombatPreference value) {
        if (!human.level().isClientSide) human.getPersistentData().putString(KEY, (value == null ? DEFAULT : value).name());
    }
}
