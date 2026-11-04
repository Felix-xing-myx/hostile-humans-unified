package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.behavior.combat.HumanCombatDirector;
import dev.felix.hostilehumans.equipment.EquipmentArchetypes;

/** Firing reach is independent of the distance a gunner tries to maintain. */
public final class GunRangePolicy {
    public record Band(double minimum, double maximum, double retreatResume, double fireRange) {}

    private GunRangePolicy() {}

    /** Preferred spacing describes the station to approach with safe local movement. */
    static boolean withinPreferredRange(double distance, Band band) {
        return Double.isFinite(distance) && distance >= band.minimum() && distance <= band.maximum();
    }

    /** Preferred spacing chooses local inputs, not whether a clear in-range shot must navigate. */
    static boolean directFiringMovement(boolean obscured,double distanceSquared,double reachSquared) {
        return !obscured&&Double.isFinite(distanceSquared)&&distanceSquared>=0
                &&Double.isFinite(reachSquared)&&reachSquared>0&&distanceSquared<=reachSquared;
    }

    public static double firingReach(Band band, double followRange) {
        return band.fireRange();
    }

    /** Some native flame/sonic projectiles expire before the generic weapon-type range. */
    static Band forWeapon(net.minecraft.world.item.ItemStack weapon) {
        var support = GunSupport.get();
        return nativeBand(support.gunType(weapon),support.maximumProjectileReach(weapon));
    }
    static Band nativeBand(String type,double nativeReach) {
        Band base=forType(type);
        // Native backends expose projectile lifetime/reach, but no shared preferred
        // spacing. Preserve 8-12 for other weapons and use a known native maximum.
        if(GunDoctrine.category(type).equals("other")&&Double.isFinite(nativeReach)&&nativeReach>0)
            base=new Band(base.minimum(),base.maximum(),base.retreatResume(),nativeReach);
        return limited(base,nativeReach);
    }
    static Band limited(Band base, double nativeReach) {
        if (!Double.isFinite(nativeReach)) return base;
        if(nativeReach<=0) return new Band(0,0,0,0);
        double preferredMax = Math.min(base.maximum(), nativeReach);
        double preferredMin = Math.min(base.minimum(), preferredMax);
        return new Band(preferredMin, preferredMax,
                Math.min(base.retreatResume(), preferredMax), Math.min(base.fireRange(), nativeReach));
    }
    public static double firingReach(net.minecraft.world.item.ItemStack weapon, double followRange) {
        return Math.min(firingReach(forWeapon(weapon), followRange), GunSupport.get().maximumProjectileReach(weapon));
    }
    public static Band forWeapon(dev.felix.hostilehumans.entity.Human human,net.minecraft.world.item.ItemStack weapon) {
        var identity=EquipmentArchetypes.describe(human,weapon);
        double nativeReach=GunSupport.get().maximumProjectileReach(weapon);
        Band base=GunDoctrine.category(identity.family()).equals("other")?nativeBand("other",nativeReach):forType(identity.family());
        if(HumanCombatDirector.strategies(human).extendedGunRange(identity.core(),identity.enabled()))
            base=new Band(Math.floor(base.minimum()*1.2),Math.floor(base.maximum()*1.2),
                    Math.floor(base.retreatResume()*1.2),Math.floor(base.fireRange()*1.3));
        return limited(base,nativeReach);
    }
    public static double firingReach(dev.felix.hostilehumans.entity.Human human,net.minecraft.world.item.ItemStack weapon) {
        return forWeapon(human,weapon).fireRange();
    }
    public static Band forType(String rawType) {
        String type=GunDoctrine.category(rawType);
        return switch(type) {
            case "pistol"->new Band(8,12,10,36);
            case "sniper"->new Band(48,64,56,96);
            case "rifle"->new Band(24,36,30,80);
            case "smg"->new Band(12,20,16,48);
            case "shotgun"->new Band(3,8,5.5,24);
            case "mg"->new Band(20,32,26,56);
            case "launcher"->new Band(32,48,40,56);
            default->new Band(8,12,10,24);
        };
    }
}
