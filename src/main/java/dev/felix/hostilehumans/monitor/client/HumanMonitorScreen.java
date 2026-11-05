package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.client.ui.framework.UiColor;
import dev.felix.hostilehumans.client.ui.framework.UiDraw;
import dev.felix.hostilehumans.client.ui.framework.UiThemes;
import dev.felix.hostilehumans.faction.HumanFactions;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Passive compact HUD, intentionally not a Screen: chat, mouse look and pause are native. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class HumanMonitorScreen {
    private HumanMonitorScreen() {}
    private static Component text(String key, Object... args) { return Component.translatable("human_monitor." + key, args); }
    @SubscribeEvent public static void hidePlayerHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (!HumanMonitorClient.active()) return;
        var id = event.getOverlay().id();
        if (id.getNamespace().equals("tacz") && (id.getPath().equals("tac_gun_hud_overlay")
                || id.getPath().equals("tac_heat_bar") || id.getPath().equals("tac_kill_amount_overlay")
                || id.getPath().equals("tac_interact_key_overlay"))) { event.setCanceled(true); return; }
        if (!id.getNamespace().equals("minecraft")) return;
        switch (id.getPath()) {
            case "hotbar", "item_name", "player_health", "armor_level", "food_level", "air_level",
                    "mount_health", "jump_bar", "experience_bar" -> event.setCanceled(true);
            default -> { }
        }
    }
    /** Passive chat has finished drawing; drain its queued geometry before placing our HUD above it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen instanceof ChatScreen) return;
        renderHud(event.getGuiGraphics());
    }

    /** Focused chat owns the foreground, so draw exactly once, before the native chat overlay. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderBelowFocusedChat(RenderGuiOverlayEvent.Pre event) {
        if (!(Minecraft.getInstance().screen instanceof ChatScreen)
                || !event.getOverlay().id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) return;
        renderHud(event.getGuiGraphics());
    }

    private static void renderHud(GuiGraphics g) {
        var client = Minecraft.getInstance(); var state = HumanMonitorClient.state();
        if (state == null || client.options.hideGui
                || client.screen != null && !(client.screen instanceof ChatScreen)) return;
        // Separate batches matter here: native chat text, background and our controls use
        // different RenderTypes. Explicit boundaries preserve painter order without a global Z hack.
        g.flush();
        g.pose().pushPose();
        try {
            renderContents(g);
        } finally {
            g.pose().popPose();
            g.flush();
        }
    }

    private static void renderContents(GuiGraphics g) {
        var client = Minecraft.getInstance(); var state = HumanMonitorClient.state();
        var font = client.font;
        var layout=MonitorHudLayout.of(client.getWindow().getGuiScaledWidth(),client.getWindow().getGuiScaledHeight(),MonitorGunHud.visible());
        int width=layout.commandWidth(),x=layout.commandX(),y=layout.commandY();
        String faction=client.level!=null&&client.level.getEntity(state.entityId()) instanceof Human human
                ?HumanFactions.id(human):"";
        var theme=UiThemes.faction(faction);int accent=theme.accent();
        // Compact HUD composes the common primitives; the full-screen frame's header/footer spacing is not reused.
        // Reference HUD: translucent slab, hairline edge and a 3px faction spine.
        UiDraw.roundRect(g,x-2,y-2,width+4,66,3,0x9505090D);
        UiDraw.roundRect(g,x,y,width,62,3,0xD9191D25);
        UiDraw.outline(g,x,y,width,62,0xFF465562);
        UiDraw.rect(g,x,y+1,3,60,UiColor.alpha(accent,.85F));
        g.drawString(font,font.plainSubstrByWidth(state.name(),Math.max(24,width-100)),x+10,y+6,0xFFEAF1F4,false);
        var status=text(state.takeover()?"manual":"observing");
        g.drawString(font,status,x+width-8-font.width(status),y+6,state.takeover()?0xFFFFCE78:0xFF77D5A1,false);
        int barWidth=Math.min(116,Math.max(24,width-120)), bx=x+20, by=y+21;
        UiDraw.health(g,bx,by,state.health(),state.maximumHealth(),false,barWidth);
        UiDraw.meter(g,bx,by-5,barWidth,3,state.pendingFood(),state.maximumHealth(),0xFFE5BC69);
        String hp=String.format(Locale.ROOT,"%.1f / %.1f  +%.1f",state.health(),state.maximumHealth(),state.pendingFood());
        int hpX=bx+barWidth+7;
        float hpScale=Math.min(1F,Math.max(8,x+width-8-hpX)/(float)Math.max(1,font.width(hp)));
        g.pose().pushPose();try {g.pose().translate(hpX,by-1,0);g.pose().scale(hpScale,hpScale,1);
            g.drawString(font,hp,0,0,theme.muted(),false);
        }finally{g.pose().popPose();}
        String first=text("keyline.actions",client.options.keyInventory.getTranslatedKeyMessage(),
                HumanMonitorClient.Keys.TAKEOVER.getTranslatedKeyMessage(),HumanMonitorClient.Keys.DEBUG.getTranslatedKeyMessage(),
                dev.felix.hostilehumans.observer.client.NavigationDebugClient.legendKeyMessage()).getString();
        var slots=client.options.keyHotbarSlots;
        var exit=HumanMonitorClient.Keys.EXIT.getTranslatedKeyMessage();
        String second=state.hired()?text("keyline.modes.hired",slots[0].getTranslatedKeyMessage(),slots[1].getTranslatedKeyMessage(),
                slots[2].getTranslatedKeyMessage(),slots[3].getTranslatedKeyMessage(),exit).getString()
                :text("keyline.modes.wild",slots[0].getTranslatedKeyMessage(),slots[1].getTranslatedKeyMessage(),
                slots[2].getTranslatedKeyMessage(),exit).getString();
        if (state.takeover()) second = text("keyline.combat",client.options.keySwapOffhand.getTranslatedKeyMessage(),
                HumanMonitorClient.Keys.SUPPLY.getTranslatedKeyMessage(),exit).getString();
        g.drawString(font,font.plainSubstrByWidth(first,width-18),x+10,y+35,0xFFD6DFE4,false);
        g.drawString(font,font.plainSubstrByWidth(second,width-18),x+10,y+47,0xFF97ABB7,false);
        MonitorGunHud.render(g);
        var view = HumanMonitorClient.loadout();
        if (state.takeover() && view != null) {
            int start = HumanMonitorClient.menuPage(), count = Math.min(9, view.options().size()-start);
            int cell = Math.max(16, (width - 8) / 9), row = y - 29;
            for (int i = 0; i < count; i++) {
                var option = view.options().get(start+i); int cx = x + 4 + i*cell;
                boolean selected = option.id().equals(view.selected());
                UiDraw.control(g,cx,row,cell-2,25,theme,false,true,selected,false,false);
                String label = option.kind()==0?text("unarmed").getString():option.name();
                var keyName = slots[i].getTranslatedKeyMessage();
                float keyScale = Math.min(1F, (cell - 6F) / Math.max(1, font.width(keyName)));
                g.pose().pushPose();
                try {
                    g.pose().translate(cx+3,row+2,0); g.pose().scale(keyScale,keyScale,1);
                    g.drawString(font,keyName,0,0,selected?accent:theme.muted(),false);
                } finally { g.pose().popPose(); }
                g.drawString(font,font.plainSubstrByWidth(label,cell-6),cx+3,row+13,theme.text(),false);
            }
        }
    }
}
