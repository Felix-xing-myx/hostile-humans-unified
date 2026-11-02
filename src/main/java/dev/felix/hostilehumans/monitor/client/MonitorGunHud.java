package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.monitor.MonitorWeaponSnapshot;
import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import java.util.Locale;

/** Session-owned read-only telemetry. Never borrows the real player's inventory or upstream HUD caches. */
public final class MonitorGunHud {
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
        g.pose().pushPose();
        try {
        g.pose().translate(layout.weaponX(),layout.weaponY(),0);
        g.pose().scale(layout.weaponScale(),layout.weaponScale(),1);
        int x=0,y=0;
        var theme=UiThemes.faction(dev.felix.hostilehumans.faction.HumanFactions.id(human));
        UiDraw.roundRect(g,x,y,180,64,3,(theme.bottom()&0xFFFFFF)|0xB0000000);
        UiDraw.outline(g,x,y,180,64,(theme.edge()&0xFFFFFF)|0xA0000000);
        UiDraw.ruleFade(g,x+3,y+1,174,theme.accent());
        if(!data.backend().equals("jeg"))UiDraw.ruleFade(g,x+82,y+43,92,theme.edge());
        try {
            switch(data.backend()) {
                case "tacz" -> dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.icon(g,held,x+2,y+3,data.loaded()==0);
                case "scguns" -> dev.felix.hostilehumans.client.ui.gun.ScgunsMonitorHud.icon(g,held,x+31,y+12);
                default -> { if(!data.backend().equals("jeg"))g.renderItem(held,x+31,y+12); }
            }
        } catch(RuntimeException|LinkageError failure) {
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce("monitor weapon HUD "+data.backend(),"Using generic weapon icon",failure);
            g.drawString(font,"?",x+31,y+12,0xFFB4C3CC,false);
        }
        boolean jeg=data.backend().equals("jeg");
        boolean percent=false;
        if(data.backend().equals("tacz"))try {percent=dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.percentage(held);}
        catch(RuntimeException|LinkageError ignored) { }
        String amount=percent?String.format(Locale.ROOT,"%.0f%%",data.capacity()==0?0:100D*data.loaded()/data.capacity())
                :String.format(Locale.ROOT,jeg?"%03d":"%02d",data.loaded());
        float scale=Math.min(2F,70F/Math.max(1,font.width(amount)));
        g.pose().pushPose();try {g.pose().translate(x+(jeg?5:89),y+(jeg?21:8),0);g.pose().scale(scale,scale,1);g.drawString(font,amount,0,0,data.loaded()==0?theme.danger():theme.text(),false);}
        finally{g.pose().popPose();}
        String reserve=Component.translatable("human_monitor.gun.magazines",value.infinite()?"∞":value.magazines()).getString();
        g.drawString(font,font.plainSubstrByWidth(reserve,86),x+(jeg?95:89),y+(jeg?23:31),theme.muted(),false);
        String mode=value.reloading()?Component.translatable("human_monitor.gun.reloading").getString():data.energy()?"FE":data.mode();
        if(data.backend().equals("tacz") && !value.reloading())dev.felix.hostilehumans.client.ui.gun.TaczMonitorHud.mode(g,data.mode(),x+54,y+35);
        else g.drawString(font,font.plainSubstrByWidth(mode,77),x+4,y+(jeg?48:37),theme.text(),false);
        String name=held.getHoverName().getString();
        g.drawString(font,font.plainSubstrByWidth(name,172),x+4,y+(jeg?4:52),theme.text(),false);
        if(!data.backend().equals("tacz"))g.drawString(font,"/ "+data.capacity(),x+89,y+43,theme.muted(),false);
        }finally{g.pose().popPose();}
    }
}
