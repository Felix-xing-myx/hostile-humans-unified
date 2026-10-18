package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Single runtime resolver shared by combat, attributes, recovery and server detail snapshots. */
public final class EffectiveAttributes {
    private EffectiveAttributes() { }
    /** Allocation and the independent boss baseline are separate factors. */
    public static double multiplier(Human human, SpecialtyAttributes.Dimension dimension) {
        return SpecialtyAttributes.multiplier(human, dimension) * bossMultiplier(human, dimension);
    }
    public static double bossMultiplier(Human human, SpecialtyAttributes.Dimension dimension) {
        return HumanFactions.bossBase(human, dimension.key());
    }
    public static double healthBase(Human human) { return TierAttributes.of(human).maxHealth() * multiplier(human, SpecialtyAttributes.Dimension.HEALTH); }
    public static double armorBase(Human human) { return TierAttributes.of(human).armor() * multiplier(human, SpecialtyAttributes.Dimension.ARMOR); }
    public static double toughnessBase(Human human) { return TierAttributes.of(human).armorToughness() * multiplier(human, SpecialtyAttributes.Dimension.TOUGHNESS); }
    public static double knockbackBase(Human human) {
        return Math.max(0, Math.min(1, TierAttributes.of(human).knockbackResistance()
                * multiplier(human, SpecialtyAttributes.Dimension.KNOCKBACK_RESISTANCE) + HumanFactions.bossAdd(human, "knockback_resistance")));
    }
    public static double speedBase(Human human) { return TierAttributes.of(human).baseMovementSpeed() * multiplier(human, SpecialtyAttributes.Dimension.SPEED); }
    public static double detectionBase(Human human) { return TierAttributes.of(human).followRange() * multiplier(human, SpecialtyAttributes.Dimension.DETECTION); }
    public static double meleeMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.MELEE); }
    public static double projectileMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.PROJECTILE); }
    public static double gunMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.GUN); }
    public static double accuracyMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.ACCURACY); }
    public static double spreadMultiplier(Human human) { return 1 / accuracyMultiplier(human); }
    public static double regenerationMultiplier(Human human) { return multiplier(human, SpecialtyAttributes.Dimension.REGENERATION); }
    /** Base recovery only; callers own health clamps and must not feed the scaled amount back here. */
    public static float healing(Human human, float baseAmount) {
        return baseAmount > 0 && Float.isFinite(baseAmount) ? (float) (baseAmount * regenerationMultiplier(human)) : baseAmount;
    }
    /** Live physical attributes; combat entries are weapon-independent factors, not predicted damage.
     * UI must add the selected projectile branch / firearm backend config and known weapon base. */
    public static double liveValue(Human human, SpecialtyAttributes.Dimension dimension) {
        return switch (dimension) {
            case HEALTH -> human.getMaxHealth();
            case ARMOR -> human.getAttributeValue(Attributes.ARMOR);
            case TOUGHNESS -> human.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            case KNOCKBACK_RESISTANCE -> human.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            case SPEED -> human.getAttributeValue(Attributes.MOVEMENT_SPEED);
            case DETECTION -> human.getAttributeValue(Attributes.FOLLOW_RANGE);
            case MELEE -> HumanGrowth.multiplier(human) * meleeMultiplier(human) * TierAttributes.of(human).meleeDamage();
            case GUN -> HumanGrowth.gunDamageMultiplier(human) * gunMultiplier(human);
            case PROJECTILE -> HumanGrowth.multiplier(human) * projectileMultiplier(human);
            case ACCURACY -> accuracyMultiplier(human);
            case REGENERATION -> regenerationMultiplier(human);
        };
    }
}
