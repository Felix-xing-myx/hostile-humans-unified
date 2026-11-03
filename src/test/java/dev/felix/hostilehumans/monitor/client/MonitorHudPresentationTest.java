package dev.felix.hostilehumans.monitor.client;

public final class MonitorHudPresentationTest {
    private static void check(boolean b){if(!b)throw new AssertionError();}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args){
        for(int w:new int[]{320,427,620,640,960})for(int h:new int[]{240,360,540}) {
            var layout=MonitorHudLayout.of(w,h,true);
            check(layout.commandX()>=0 && layout.commandY()-29>=0);
            check(layout.weaponX()+MonitorHudLayout.WEAPON_WIDTH*layout.weaponScale()<=w
                    && layout.weaponY()+MonitorHudLayout.WEAPON_HEIGHT*layout.weaponScale()<=h);
            check(layout.weaponX()>=4 && layout.weaponY()>=4);
            boolean above=layout.weaponY()+MonitorHudLayout.WEAPON_HEIGHT<=layout.commandY();
            boolean rightOf=layout.weaponX()>=layout.commandX()+layout.commandWidth();
            check(above||rightOf,"weapon slab never covers the command slab");
            var ordinary=MonitorHudLayout.of(w,h,false);
            check(ordinary.commandY()==h-72 && ordinary.commandX()==layout.commandX()
                    && ordinary.commandWidth()==layout.commandWidth() && ordinary.commandY()==layout.commandY());
            check(ordinary.weaponX()==layout.weaponX() && ordinary.weaponY()==layout.weaponY());
        }
        for(int[] size:new int[][]{{39,13},{512,128},{128,512},{512,512}}) {
            var fit=MonitorIconFit.contain(size[0],size[1],MonitorHudLayout.ART_WIDTH,MonitorHudLayout.ART_HEIGHT);
            check(fit.x()>=0 && fit.y()>=0);
            check(fit.x()+size[0]*fit.scale()<=MonitorHudLayout.ART_WIDTH+.001
                    && fit.y()+size[1]*fit.scale()<=MonitorHudLayout.ART_HEIGHT+.001);
            check(Math.abs((size[0]*fit.scale())/(size[1]*fit.scale())-(float)size[0]/size[1])<.001);
            var snapped=MonitorIconFit.containSnapped(size[0],size[1],MonitorHudLayout.ART_WIDTH,MonitorHudLayout.ART_HEIGHT);
            check(snapped.x()>=0 && snapped.y()>=0);
            check(snapped.x()+size[0]*snapped.scale()<=MonitorHudLayout.ART_WIDTH+.001
                    && snapped.y()+size[1]*snapped.scale()<=MonitorHudLayout.ART_HEIGHT+.001);
            float unit=snapped.scale()>=1?snapped.scale():1/snapped.scale();
            check(Math.abs(unit-Math.round(unit))<.001,"snapped art keeps square pixels");
        }
        var hit=new MonitorHitFeedback();check(hit.frame(0)==null);
        hit.mark(1_000_000_000L,false,false);check(!hit.frame(1_000_000_000L).headshot());
        check(Math.abs(hit.frame(1_150_000_000L).alpha()-.5)<.0001);
        check(hit.frame(1_300_000_000L)==null);
        hit.mark(2_000_000_000L,true,true);
        check(hit.frame(2_150_000_000L).headshot() && Math.abs(hit.frame(2_150_000_000L).extraOffset()-2)<.0001);
        hit.clear();check(hit.frame(2_150_000_000L)==null);
        System.out.println("Monitor HUD layout and native feedback envelope passed");
    }
}
