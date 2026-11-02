package dev.felix.hostilehumans.monitor.client;

/** Stable centered controls; the gun owns a separate right-hand row above the controls. */
public record MonitorHudLayout(int commandX, int commandY, int commandWidth, int weaponX, int weaponY, float weaponScale) {
    public static final int WEAPON_WIDTH = 180, WEAPON_HEIGHT = 64;
    public static MonitorHudLayout of(int width, int height, boolean weapon) {
        int w=Math.max(80,Math.min(360,width-16));
        int x=Math.max(4,(width-w)/2),y=Math.max(33,height-72);
        float scale=Math.max(.35F,Math.min(1F,(width-12F)/WEAPON_WIDTH));
        int ww=Math.round(WEAPON_WIDTH*scale),wh=Math.round(WEAPON_HEIGHT*scale);
        int wx=Math.max(4,width-ww-6),wy=Math.max(4,y-29-wh-8);
        return new MonitorHudLayout(x,y,w,wx,wy,scale);
    }
}
