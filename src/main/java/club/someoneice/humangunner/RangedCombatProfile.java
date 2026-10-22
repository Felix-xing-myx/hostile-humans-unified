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
        String faction = identity.enabled() && identity.core() ? HumanFactions.shortId(human) : "";
        return of(RangedWeaponCustody.isCrossbowWeapon(weapon), weapon.getItem() instanceof TridentItem, faction);
    }
    static RangedCombatProfile of(boolean crossbow, boolean trident, String faction) {
        boolean blue = faction.equals("azure"), green = faction.equals("verdant");
        boolean red = faction.equals("crimson") && !trident;
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
                !trident && !crossbow && green, red, !trident && faction.equals("ashen"));
    }
    public double maximumSqr() { return maximum * maximum; }
    public double minimumBandSqr() { return minimumBand * minimumBand; }
    public double maximumBandSqr() { return maximumBand * maximumBand; }
}
