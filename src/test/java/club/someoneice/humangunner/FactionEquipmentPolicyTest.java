package club.someoneice.humangunner;

import java.util.Random;

/** Standalone JDK-only policy checks; Minecraft is not initialized. */
public final class FactionEquipmentPolicyTest {
    public static void main(String[] args) {
        double[] damage={4,6,8,11},shield={200,450,1000};
        check(FactionEquipmentPolicy.band(4,damage,false)==0,"damage boundary inclusive");
        check(FactionEquipmentPolicy.band(4.5,damage,false)==1,"fractional damage has a band");
        check(FactionEquipmentPolicy.band(6.5,damage,false)==2,"fractional damage above six");
        check(FactionEquipmentPolicy.band(200,shield,true)==1,"shield lower boundary inclusive");
        check(FactionEquipmentPolicy.band(1000,shield,true)==3,"terminal shield band");
        check(FactionEquipmentPolicy.nearest(2,new boolean[]{false,true,false,true,false})==1,"equal-distance borrowing favors lower");
        check(FactionEquipmentPolicy.nearest(4,new boolean[]{false,true,false,false,false})==1,"walk through multiple empty bands");
        check(FactionEquipmentPolicy.nearest(1,new boolean[5])==-1,"empty category");
        check(FactionEquipmentPolicy.choose(new double[]{0,0},.5)==-1,"zero sum is explicit empty");
        check(FactionEquipmentPolicy.choose(new double[]{Double.NaN,-1,2},.5)==2,"invalid weights cannot pollute selection");
        check(FactionEquipmentPolicy.scaledCount(24,1.6)==38,"ammunition rounds once after base roll");
        check(FactionEquipmentPolicy.scaledCount(128,1.6)==205,"not clipped to old maximum");
        check(FactionEquipmentPolicy.scaledCount(0,.6)==0,"zero stays zero");
        check(FactionEquipmentPolicy.enchantmentLevel(3,5,2,1,.9,.1)==2,"tier3 downgrades to affordable cap");
        check(FactionEquipmentPolicy.enchantmentLevel(3,5,12,1,.9,0)==4,"tier3 lower level is maximum minus one");
        check(FactionEquipmentPolicy.enchantmentLevel(1,5,12,1,0,0)==5,"tier1 direct maximum branch");
        check(FactionEquipmentPolicy.enchantmentLevel(2,5,12,0,.9,.5)==0,"zero cost rejected");
        for(int rank=0;rank<=3;rank++) for(int maximum=1;maximum<=10;maximum++)
            for(int budget=0;budget<=12;budget++) for(int cost=1;cost<=9;cost++) {
                int level=FactionEquipmentPolicy.enchantmentLevel(rank,maximum,budget,cost,.9,.999999);
                check(level>=0&&level<=maximum&&level*cost<=budget,"level affordability invariant");
            }
        double[] weights={5,15,45,35,0};
        int[] counts=new int[5]; Random random=new Random(11019);
        for(int i=0;i<100000;i++) counts[FactionEquipmentPolicy.choose(weights,random.nextDouble())]++;
        for(int i=0;i<counts.length;i++) check(Math.abs(counts[i]/1000.0-weights[i])<1,"requested band distribution "+i);
        System.out.println("FactionEquipmentPolicyTest: passed");
    }
    private static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
