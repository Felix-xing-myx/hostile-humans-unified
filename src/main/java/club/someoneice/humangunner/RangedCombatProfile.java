package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

/** Live capability view; never caches a profession on the entity. */
public record RangedCombatProfile(double maximum, double minimumBand, double maximumBand,
        double strafeSpeed, double attackRate, double damage, double savingChance,
        boolean doubleArrow, boolean tripleArrow, boolean advance) {
    public static RangedCombatProfile of(Human human, ItemStack weapon) {
        var identity = EquipmentArchetypes.describe(human, weapon);
        var strategy = HumanCombatDirector.strategies(human).ranged(identity.core(), identity.enabled());
        return of(RangedWeaponCustody.isCrossbowWeapon(weapon), weapon.getItem() instanceof TridentItem, strategy);
    }
    static RangedCombatProfile of(boolean crossbow, boolean trident, String faction) {
        return of(crossbow, trident, CombatStrategySet.forFaction(faction).ranged());
    }
    static RangedCombatProfile of(boolean crossbow, boolean trident, CombatStrategySet.RangedStyle strategy) {
        boolean blue = strategy == CombatStrategySet.RangedStyle.FORTIFIED;
        boolean green = strategy == CombatStrategySet.RangedStyle.MOBILE;
        boolean red = strategy == CombatStrategySet.RangedStyle.VOLLEY && !trident;
        double maximum = trident ? 16 : crossbow ? 48 : 32;
        double lower = trident ? 6 : crossbow ? 24 : 16;
        double upper = trident ? 16 : crossbow ? 36 : 24;
        double speed = trident ? (blue ? .35 : .65) : crossbow ? (blue ? .10 : .30)
                : blue ? .30 : green ? 1 : .50;
        return new RangedCombatProfile(red ? Math.floor(maximum * .60) : maximum,
                red ? lower * .50 : lower, red ? upper * .50 : upper, speed,
                blue && !crossbow && !trident ? 1.40 : 1,
                blue ? (trident ? 1.20 : crossbow ? 1.30 : 1) : 1,
                !trident && !crossbow && (blue || green) ? .40 : 0,
                !trident && !crossbow && green, red, !trident && strategy == CombatStrategySet.RangedStyle.ADVANCE);
    }
    public double maximumSqr() { return maximum * maximum; }
    public double minimumBandSqr() { return minimumBand * minimumBand; }
    public double maximumBandSqr() { return maximumBand * maximumBand; }
}
