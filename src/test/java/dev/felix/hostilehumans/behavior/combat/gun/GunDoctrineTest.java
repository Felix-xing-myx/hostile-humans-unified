package dev.felix.hostilehumans.behavior.combat.gun;

/** Pure doctrine boundaries; does not require a world or an optional gun mod. */
public final class GunDoctrineTest {
    public static void main(String[] args) {
        var green=GunDoctrine.style("verdant","sniper",true);
        check(green.spread()==.6&&green.movement()==.80,"green core precision and movement");
        near(GunDoctrine.workPerTick(1,2,false),1/1.4,"common human reload duration scales once");
        near(GunDoctrine.workPerTick(green.reloadDuration(),2,false),.7/1.4,"green strategy multiplies the new baseline");
        var nonCoreGreen = GunDoctrine.style("verdant", "pistol", false);
        check(!nonCoreGreen.advance() && !nonCoreGreen.forceMelee(), "green non-core retains spacing");
        check(nonCoreGreen.movement()==.4 && nonCoreGreen.spread()==1 && nonCoreGreen.reloadDuration()==1,
                "non-core spacing retains normal numerical modifiers");
        var blue=GunDoctrine.style("azure","mg",true);
        near(GunDoctrine.workPerTick(blue.reloadDuration(),2,false),1/(1.4*.7),"blue duration multiplies the new baseline");
        near(GunDoctrine.workPerTick(green.reloadDuration(),2,true),.7/(1.4*2),"background penalty multiplies once");
        near(GunDoctrine.workPerTick(1,1.2,true),1/(1.4*1.2),"red close guns retain their background strategy");
        check(GunDoctrine.reloadHardLimit(600,green.reloadDuration(),2)>=2400,
                "deadline permits slowed green background work instead of cancelling a healthy reload");
        check(GunDoctrine.reloadHardLimit(216000,100,100)==216000,"hard deadline remains bounded");
        check(blue.spread()==.7&&blue.movement()==.2,"blue core precision and movement");
        check(GunDoctrine.style("azure","pistol",false).spread()==1,"blue pistol has no rifle bonus");
        for(String type:new String[]{"pistol","rifle","sniper","smg","shotgun","mg","launcher","other"}) {
            var red=GunDoctrine.style("crimson",type,true);
            boolean close=type.equals("smg")||type.equals("shotgun");
            check(red.advance()&&red.forceMelee(),"red close melee priority for all gun classes: "+type);
            near(red.backgroundDuration(),close?1.2:2,"red silent duration: "+type);
            check(GunDoctrine.style("ashen",type,false).forceMelee(),"ashen forces melee for every gun");
        }
        // A native 100 tick cycle: 28 held + 56 stowed + 84 held at the 1.4 baseline.
        // baseline work. Switching modes changes rate without restarting progress.
        double progress=0;
        for(int i=0;i<28;i++) progress=GunDoctrine.advance(progress,100,GunDoctrine.workPerTick(1,2,false));
        near(progress,.2,"held progress");
        for(int i=0;i<56;i++) progress=GunDoctrine.advance(progress,100,GunDoctrine.workPerTick(1,2,true));
        near(progress,.4,"silent progress resumes from held fraction");
        for(int i=0;i<84;i++) progress=GunDoctrine.advance(progress,100,GunDoctrine.workPerTick(1,2,false));
        near(progress,1,"return to held completes same cycle");
        near(GunDoctrine.advance(.3,100,Double.NaN),.3,"invalid work cannot grant ammunition");
        near(GunDoctrine.projectileReach(5,20,0),100,"finite native projectile lifetime");
        check(GunDoctrine.projectileReach(5,20,.1)<100,"drag cannot extend ballistic reach");
        near(GunDoctrine.projectileReach(5,0,0),0,"zero lifetime cannot fire");
        System.out.println("GunDoctrineTest passed");
    }
    private static void near(double actual,double expected,String reason) {
        check(Math.abs(actual-expected)<1e-8,reason+": "+actual);
    }
    private static void check(boolean value,String reason) {
        if(!value) throw new AssertionError(reason);
    }
}
