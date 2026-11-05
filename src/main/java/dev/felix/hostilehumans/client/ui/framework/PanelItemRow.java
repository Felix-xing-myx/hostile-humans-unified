package dev.felix.hostilehumans.client.ui.framework;

import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One localized item, source identity and optional detail, with a native 16px item icon. */
public final class PanelItemRow extends PanelButton {
    private final ItemStack icon;
    private final Component subtitle, detail;
    public PanelItemRow(ItemStack icon, Component title, Component subtitle, Component detail, OnPress action, Supplier<UiTheme> theme){
        super(title,action,theme);this.icon=icon.copy();this.subtitle=subtitle;this.detail=detail;
    }
    @Override public int measureHeight(int width){return detail.getString().isEmpty()?32:44;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial){
        var t=theme.get();var font=Minecraft.getInstance().font;
        UiDraw.control(g,getX(),getY(),width,height,t,isHovered&&inViewport(mx,my),active,selected(),false,isFocused(),sheen());
        if(!icon.isEmpty())g.renderItem(icon,getX()+6,getY()+(height-16)/2);
        int x=getX()+28,room=Math.max(1,width-34),y=getY()+6;
        g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),room),x,y,active?t.text():t.disabledText(),false);
        g.drawString(font,font.plainSubstrByWidth(subtitle.getString(),room),x,y+11,t.muted(),false);
        if(!detail.getString().isEmpty())g.drawString(font,font.plainSubstrByWidth(detail.getString(),room),x,y+23,t.accent(),false);
    }
}
