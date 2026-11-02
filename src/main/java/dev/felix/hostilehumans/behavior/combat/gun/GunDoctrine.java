package dev.felix.hostilehumans.behavior.combat.gun;

import dev.felix.hostilehumans.behavior.combat.CombatStrategySet;

/** Confirmed firearm numbers. Reload multipliers describe duration, never rate. */
public final class GunDoctrine {
    static final double HUMAN_RELOAD_DURATION = 1.4;
    public record Style(double spread, double reloadDuration, double movement, boolean advance,
                 boolean forceMelee, double backgroundDuration) {}
    private GunDoctrine() {}
    static String category(String type) {
        return switch(type==null?"":type.toLowerCase(java.util.Locale.ROOT)) {
            case "pistol","revolver","handgun","magnum"->"pistol";
            case "smg","pdw","submachine_gun"->"smg";
            case "rifle","carbine","assault_rifle"->"rifle";
            case "sniper","snipers_rifle","sniper_rifle","anti_material_rifle"->"sniper";
            case "mg","lmg","hmg","machine_gun"->"mg";
            case "rpg","heavy","launcher","rocket_launcher","grenade_launcher"->"launcher";
            case "shotgun"->"shotgun";
            default->"other";
        };
    }
    public static Style style(String faction, String category, boolean core) {
        return style(CombatStrategySet.forFaction(faction).gun(), category, core);
    }
    public static Style style(CombatStrategySet.GunStyle strategy, String category, boolean core) {
        if (strategy == CombatStrategySet.GunStyle.MOBILE && core) return new Style(.6,1/.7,.80,false,false,2);
        if (strategy == CombatStrategySet.GunStyle.FORTIFIED && (category.equals("rifle") || category.equals("mg")))
            return new Style(.7,.7,.2,false,false,2);
        if (strategy == CombatStrategySet.GunStyle.ASSAULT) {
            boolean close=category.equals("smg")||category.equals("shotgun");
            return new Style(1,1,.4,true,true,close?1.2:2);
        }
        if (strategy == CombatStrategySet.GunStyle.MELEE_ASSAULT) return new Style(1,1,.4,true,true,2);
        boolean nonCore=!core && strategy == CombatStrategySet.GunStyle.FORTIFIED;
        return new Style(1,1,.4,nonCore,nonCore,2);
    }
    static double workPerTick(double duration, double background, boolean silent) {
        double divisor=HUMAN_RELOAD_DURATION*duration*(silent?background:1);
        return Double.isFinite(divisor)&&divisor>0?1/divisor:0;
    }
    /** Native recovery deadlines must allow the slowest foreground/background phase. */
    static int reloadHardLimit(int nativeLimit, double duration, double background) {
        double rate = workPerTick(duration, Math.max(1, background), true);
        return rate > 0 ? (int) Math.min(216000, Math.ceil(nativeLimit * Math.max(1, 1 / rate)))
                : nativeLimit;
    }
    public static double projectileReach(double speedPerTick,double ticks,double drag) {
        if(!Double.isFinite(speedPerTick)||!Double.isFinite(ticks)||speedPerTick<=0||ticks<=0) return 0;
        double friction=Double.isFinite(drag)?Math.max(0,Math.min(1,drag)):0;
        if(friction==0) return speedPerTick*ticks;
        return speedPerTick*(1-Math.pow(1-friction,ticks))/friction;
    }
    static double advance(double progress,double baselineTicks,double work) {
        if(!(baselineTicks>0)||!Double.isFinite(baselineTicks)||!(work>=0)||!Double.isFinite(work)) return progress;
        return Math.min(1,Math.max(0,progress)+work/baselineTicks);
    }
}
