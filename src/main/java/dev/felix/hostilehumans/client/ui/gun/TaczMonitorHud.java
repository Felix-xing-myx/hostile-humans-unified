package dev.felix.hostilehumans.client.ui.gun;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.config.client.RenderConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.felix.hostilehumans.monitor.client.MonitorGunHud;
import dev.felix.hostilehumans.monitor.client.MonitorHitFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

/** Optional TaCZ client adapter; reuses native art/sounds without impersonating a LocalPlayer. */
public final class TaczMonitorHud {
    private static final MonitorHitFeedback HITS=new MonitorHitFeedback();
    private static final ResourceLocation HIT=new ResourceLocation("tacz","textures/crosshair/hit/hit_marker.png");
    private static boolean listening;
    private record Size(int width,int height) {}
    private static final java.util.Map<ResourceLocation,Size> SIZES=new java.util.HashMap<>();
    private TaczMonitorHud() {}
    public static void install() {
        if(listening)return;
        MinecraftForge.EVENT_BUS.addListener(TaczMonitorHud::hurt);
        MinecraftForge.EVENT_BUS.addListener(TaczMonitorHud::killed);
        listening=true;
    }
    public static void clear() {HITS.clear();SIZES.clear();}
    public static ResourceLocation gunId(ItemStack stack) {var gun=IGun.getIGunOrNull(stack);return gun==null?null:gun.getGunId(stack);}
    public static boolean percentage(ItemStack stack) {
        return TimelessAPI.getGunDisplay(stack).map(d -> d.getAmmoCountStyle().toString().toLowerCase(java.util.Locale.ROOT).contains("percent")).orElse(false);
    }
    public static void icon(GuiGraphics g,ItemStack stack,int x,int y,boolean empty) {
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null){g.renderItem(stack,x+28,y+8);return;}
        var texture=empty?display.getHudEmptyTexture():display.getHUDTexture();
        if(texture==null)texture=display.getHUDTexture();
        if(texture!=null) {
            Size size=SIZES.computeIfAbsent(texture,TaczMonitorHud::textureSize);
            var fit=dev.felix.hostilehumans.monitor.client.MonitorIconFit.containSnapped(size.width(),size.height(),
                    dev.felix.hostilehumans.monitor.client.MonitorHudLayout.ART_WIDTH,
                    dev.felix.hostilehumans.monitor.client.MonitorHudLayout.ART_HEIGHT);
            g.pose().pushPose();
            try {
                g.pose().translate(x+fit.x(),y+fit.y(),0);
                g.pose().scale(fit.scale(),fit.scale(),1);
                g.blit(texture,0,0,0,0,size.width(),size.height(),size.width(),size.height());
            } finally {g.pose().popPose();}
        }
    }
    private static Size textureSize(ResourceLocation texture) {
        try (var stream=Minecraft.getInstance().getResourceManager().open(texture);
                var image=com.mojang.blaze3d.platform.NativeImage.read(stream)) {
            return new Size(image.getWidth(),image.getHeight());
        } catch (java.io.IOException|RuntimeException failure) {
            return new Size(39,13); // actual native HUD aspect, never stretch a missing pack resource
        }
    }
    /** Largest width the fire-mode art may occupy in the caption row. */
    public static final int MODE_WIDTH = 22;

    /**
     * Draws the native fire-mode art fitted into {@code MODE_WIDTH x rowHeight}; returns the width
     * used, or 0 when the pack has no such texture.
     *
     * <p>Two previous bugs lived here: the blit declared a hard-coded 18x10 as the <em>texture</em>
     * size, which made Minecraft squeeze whatever art the pack ships into that window (the smeared
     * glyph), and drawing it at its own size instead put a large sheet across the whole slab. The
     * UVs now use the real size and the scale is snapped to a whole ratio inside the row box, so it
     * can never overflow.
     */
    public static int mode(GuiGraphics g,String mode,int x,int y,int rowHeight) {
        String suffix=switch(mode){case "AUTO"->"auto";case "BURST"->"burst";default->"semi";};
        var texture=new ResourceLocation("tacz","textures/hud/fire_mode_"+suffix+".png");
        Size size=SIZES.computeIfAbsent(texture,TaczMonitorHud::textureSize);
        if(size.width()<=0||size.height()<=0||rowHeight<=0)return 0;
        var fit=dev.felix.hostilehumans.monitor.client.MonitorIconFit.containSnapped(
                size.width(),size.height(),MODE_WIDTH,rowHeight);
        g.pose().pushPose();
        try {
            g.pose().translate(x+fit.x(),y+fit.y(),0);
            g.pose().scale(fit.scale(),fit.scale(),1);
            g.blit(texture,0,0,0,0,size.width(),size.height(),size.width(),size.height());
        } finally { g.pose().popPose(); }
        return Math.min(MODE_WIDTH,Math.round(size.width()*fit.scale()));
    }
    private static boolean observing(LivingEntity attacker) {return attacker!=null && attacker==MonitorGunHud.viewed();}
    private static void hurt(EntityHurtByGunEvent.Post event) {
        if(event.getLogicalSide().isClient() && observing(event.getAttacker()))feedback(event.getGunDisplayId(),event.getGunId(),event.isHeadShot(),false);
    }
    private static void killed(EntityKillByGunEvent event) {
        if(event.getLogicalSide().isClient() && observing(event.getAttacker()))feedback(event.getGunDisplayId(),event.getGunId(),event.isHeadShot(),true);
    }
    private static void feedback(ResourceLocation displayId,ResourceLocation gunId,boolean head,boolean kill) {
        HITS.mark(System.nanoTime(),head,kill);
        var listener=MonitorGunHud.viewed();if(listener==null)return;
        // Native packets are mutually exclusive Post/Kill and already originate at server collision resolution.
        // Native hit semantics are intentionally preserved, including upstream treatment of damage immunity.
        // HHU's native sound-distance bridge follows this observed body, not the real player's remote position.
        try {
            TimelessAPI.getGunDisplay(displayId,gunId).ifPresent(display -> {
                if(kill)SoundPlayManager.playKillSound(listener,display);
                else if(head)SoundPlayManager.playHeadHitSound(listener,display);
                else SoundPlayManager.playFleshHitSound(listener,display);
            });
        }catch(RuntimeException|LinkageError failure){
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce("monitor hit sound","Native feedback sound unavailable",failure);
        }
    }
    public static void hitMarker(GuiGraphics g) {
        var frame=HITS.frame(System.nanoTime());if(frame==null)return;
        var window=Minecraft.getInstance().getWindow();
        float offset=RenderConfig.HIT_MARKET_START_POSITION.get().floatValue()+frame.extraOffset();
        float x=window.getGuiScaledWidth()/2F-8,y=window.getGuiScaledHeight()/2F-8;
        float[] color=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            g.setColor(1,frame.headshot()?0:1,frame.headshot()?0:1,frame.alpha());
            g.blit(HIT,(int)(x-offset),(int)(y-offset),0,0,8,8,16,16);
            g.blit(HIT,(int)(x+8+offset),(int)(y-offset),8,0,8,8,16,16);
            g.blit(HIT,(int)(x-offset),(int)(y+8+offset),0,8,8,8,16,16);
            g.blit(HIT,(int)(x+8+offset),(int)(y+8+offset),8,8,8,8,16,16);
        }finally{g.setColor(color[0],color[1],color[2],color[3]);}
    }
}
