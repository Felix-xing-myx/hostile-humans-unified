package dev.felix.hostilehumans.faction;

public final class IdentityBandPriorityTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static IdentityBands.Selection band(int grade,int source,String slot){return new IdentityBands.Selection("test:faction",grade,source,slot);}
    public static void main(String[] args){
        check(IdentityBands.prefers(band(1,0,"identity_band:0"),band(4,2,"00000")),"equipped low grade outranks backpack boss grade");
        check(IdentityBands.prefers(band(1,1,"charm:0"),band(4,2,"00000")),"all functional Curios slots outrank backpack");
        check(!IdentityBands.prefers(band(4,2,"00000"),band(1,0,"identity_band:0")),"inventory scan cannot replace equipped identity");
        check(IdentityBands.prefers(band(4,1,"charm:0"),band(1,0,"identity_band:0")),"within Curios choose highest grade");
        check(IdentityBands.prefers(band(3,0,"identity_band:1"),band(3,1,"charm:0")),"reserved slot wins equal grade");
        check(IdentityBands.prefers(band(4,2,"00003"),band(2,2,"00000")),"backpack-only fallback retains highest grade");
        check(IdentityBands.prefers(band(2,2,"00001"),band(2,2,"00002")),"equal backpack grades resolve deterministically");
        System.out.println("IdentityBandPriorityTest passed");
    }
}
