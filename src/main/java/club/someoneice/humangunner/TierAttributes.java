package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Applies configured base attributes without removing equipment/potion modifiers. */
public final class TierAttributes {
    private TierAttributes() {}
    public static String key(Human human) {
        if (TierThreeHuman.isTierThree(human)) return "tier3";
        if (human.getTier() == HumanTier.LEVEL2) return "tier2";
        if (human.getTier() == HumanTier.LEVEL1) return "tier1";
        return "roamer";
    }
    public static UnifiedConfig.Tier of(Human human) { return UnifiedConfig.get().tier(key(human)); }
    public static void apply(Human human) {
        var tier = of(human);
        set(human, Attributes.ATTACK_DAMAGE, tier.attackDamage());
        set(human, Attributes.ARMOR, tier.armor());
        set(human, Attributes.ARMOR_TOUGHNESS, tier.armorToughness());
        set(human, Attributes.KNOCKBACK_RESISTANCE, tier.knockbackResistance());
        set(human, Attributes.FOLLOW_RANGE, tier.followRange());
    }
    private static void set(Human human, Attribute key, double value) {
        var attribute = human.getAttribute(key);
        if (attribute != null && Math.abs(attribute.getBaseValue() - value) > 1.0e-6)
            attribute.setBaseValue(value);
    }
}
