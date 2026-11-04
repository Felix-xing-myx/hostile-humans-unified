package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.monitor.MonitorWeaponSnapshot;
import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiTheme;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import java.util.Locale;

/** Session-owned read-only telemetry. Never borrows the real player's inventory or upstream HUD caches. */
public final class MonitorGunHud {
    /** Reserved width of the fire-mode word; wide enough for "全自动" and "RELOAD". */
    private static final int MODE_SLOT = 36;
    private static MonitorWeaponSnapshot snapshot;
    private MonitorGunHud() {}
    public static Human viewed() {
        var client=Minecraft.getInstance(); var state=HumanMonitorClient.state();
        if(state==null || client.level==null || !(client.level.getEntity(state.entityId()) instanceof Human human)
                || !HumanMonitorClient.viewing(human) || client.getCameraEntity()!=human) return null;
        return human;
    }
    public static void accept(MonitorWeaponSnapshot value) {
        var state=HumanMonitorClient.state();
        if(state!=null && state.token().equals(value.token()) && state.target().equals(value.target())
                && state.entityId()==value.entityId() && state.generation()==value.generation()) snapshot=value;
    }
    public static void clear() {
        snapshot=null;
        if(ModList.get().isLoaded("tacz")) dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.clear();
    }
    public static void activate() {
        if(ModList.get().isLoaded("tacz")) dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.install();
    }
    public static boolean visible() {
        Human h=viewed();return h!=null && GunSupport.isGunItem(h.getMainHandItem());
    }
    /**
     * Truncates with an ellipsis instead of a hard chop, so a long gun name reads as shortened
     * rather than broken. Falls back to a plain cut when even the ellipsis would not fit.
     */
    private static String fitWithEllipsis(net.minecraft.client.gui.Font font, String text, int room) {
        if (room <= 0) return "";
        if (font.width(text) <= room) return text;
        String ellipsis = "…";
        int limit = room - font.width(ellipsis);
        if (limit <= 0) return font.plainSubstrByWidth(text, room);
        String cut = font.plainSubstrByWidth(text, limit);
        return cut + ellipsis;
    }

    /** Localised fire-mode word; unknown modes keep the raw packet value. */
    private static String modeLabel(String mode) {
        String kind=mode==null?"":mode.toUpperCase(Locale.ROOT);
        String key=switch(kind) {
            case "AUTO" -> "human_monitor.gun.mode.auto";
            case "BURST" -> "human_monitor.gun.mode.burst";
            case "SEMI", "SINGLE" -> "human_monitor.gun.mode.semi";
            default -> "";
        };
        return key.isEmpty()?(mode==null?"":mode):Component.translatable(key).getString();
    }

    /** Keeps the tail of a right aligned label so a clipped "弹匣 120" still shows the count. */
    private static String clipTail(net.minecraft.client.gui.Font font, String text, int room) {
        String out = text;
        while (out.length() > 1 && font.width(out) > room) out = out.substring(1);
        return out;
    }

    public static void render(GuiGraphics g) {
        Human human=viewed(); if(human==null)return;
        var client=Minecraft.getInstance(); var font=client.font;
        if(ModList.get().isLoaded("tacz")) dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.hitMarker(g);
        var value=snapshot; var state=HumanMonitorClient.state();
        if(value==null || state==null || !state.token().equals(value.token()) || state.generation()!=value.generation())return;
        var data=value.weapon(); var held=human.getMainHandItem();
        if(!data.present() || !GunSupport.isGunItem(held))return;
        // Main-hand equipment and reserve packets can arrive in different ticks. Do not show the previous gun's telemetry.
        boolean matches=switch(data.backend()) {
            case "tacz" -> GunSupport.isTaczGunItem(held);
            case "scguns" -> GunSupport.isScgunsGunItem(held);
            case "jeg" -> GunSupport.isJegGunItem(held);
            default -> false;
        };
        if(!matches)return;
        var identity=data.backend().equals("tacz")?dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.gunId(held)
                :net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        if(!data.gun().equals(identity))return;
        var layout=MonitorHudLayout.of(client.getWindow().getGuiScaledWidth(),client.getWindow().getGuiScaledHeight(),true);
        var theme=UiThemes.faction(dev.felix.hostilehumans.faction.HumanFactions.id(human));
        boolean jeg=data.backend().equals("jeg");
        int pw=MonitorHudLayout.WEAPON_WIDTH, ph=MonitorHudLayout.WEAPON_HEIGHT;
        g.pose().pushPose();
        try {
        g.pose().translate(layout.weaponX(),layout.weaponY(),0);
        int x=0,y=0;
        UiDraw.roundRect(g,x,y,pw,ph,3,(theme.bottom()&0xFFFFFF)|0xB0000000);
        UiDraw.outline(g,x,y,pw,ph,(theme.edge()&0xFFFFFF)|0xA0000000);
        UiDraw.ruleFade(g,x+3,y+1,pw-6,theme.accent());
        try {
            switch(data.backend()) {
                case "tacz" -> dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.icon(g,held,x+2,y+2,data.loaded()==0);
                case "scguns" -> dev.felix.hostilehumans.client.ui.gun.ScgunsMonitorHud.icon(g,held,x+2,y+9);
                default -> { if(!jeg)g.renderItem(held,x+2,y+9); }
            }
        } catch(RuntimeException|LinkageError failure) {
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce("monitor weapon HUD "+data.backend(),"Using generic weapon icon",failure);
            g.drawString(font,"?",x+2,y+9,0xFFB4C3CC,false);
        }
        boolean percent=false;
        if(data.backend().equals("tacz"))try {percent=dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.percentage(held);}
        catch(RuntimeException|LinkageError ignored) { }
        String amount=percent?String.format(Locale.ROOT,"%.0f%%",data.capacity()==0?0:100D*data.loaded()/data.capacity())
                :String.format(Locale.ROOT,jeg?"%03d":"%02d",data.loaded());
        // Right column: "18 / 30" on the first line, the reserve magazines on the second. Both
        // start right after the gun art instead of floating at the far edge.
        String capacity=percent?"":"/ "+data.capacity();
        int capacityWidth=capacity.isEmpty()?0:font.width(capacity);
        int columnX=x+58, columnRight=x+pw-4;
        int amountMax=Math.max(24,columnRight-columnX-(capacityWidth>0?capacityWidth+3:0));
        float scale=Math.min(2F,amountMax/(float)Math.max(1,font.width(amount)));
        int amountWidth=Math.min(amountMax,Math.round(font.width(amount)*scale));
        g.pose().pushPose();try {g.pose().translate(columnX,y+4,0);g.pose().scale(scale,scale,1);g.drawString(font,amount,0,0,data.loaded()==0?theme.danger():theme.text(),false);}
        finally{g.pose().popPose();}
        if(capacityWidth>0)g.drawString(font,capacity,columnX+amountWidth+3,y+13,theme.muted(),false);
        String reserve=Component.translatable("human_monitor.gun.magazines",value.infinite()?"∞":value.magazines()).getString();
        // Reserve sits in the bottom-right corner of the upper band, a step smaller than the rest.
        float reserveScale=.85F;
        String reserveText=clipTail(font,reserve,(int)((columnRight-columnX)/reserveScale));
        int reserveWidth=Math.round(font.width(reserveText)*reserveScale);
        g.pose().pushPose();try {g.pose().translate(columnRight-reserveWidth,y+24,0);g.pose().scale(reserveScale,reserveScale,1);g.drawString(font,reserveText,0,0,theme.muted(),false);}
        finally{g.pose().popPose();}
        UiDraw.ruleFade(g,x+4,y+36,pw-8,theme.edge());
        // Caption row: the fire mode is a localised word in a fixed-width slot, so the name below
        // never shifts when the mode changes or the weapon starts reloading.
        String mode=value.reloading()?Component.translatable("human_monitor.gun.reloading").getString()
                :data.energy()?"FE":modeLabel(data.mode());
        g.drawString(font,font.plainSubstrByWidth(mode,MODE_SLOT),x+3,y+39,theme.text(),false);
        int nameX=x+3+MODE_SLOT+4;
        int nameRoom=Math.max(20,columnRight-nameX);
        String name=fitWithEllipsis(font,held.getHoverName().getString(),(int)(nameRoom/.85F));
        g.pose().pushPose();try {g.pose().translate(nameX,y+39,0);g.pose().scale(.85F,.85F,1);g.drawString(font,name,0,0,theme.muted(),false);}
        finally{g.pose().popPose();}
        }finally{g.pose().popPose();}
    }
}
