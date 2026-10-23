package club.someoneice.humangunner;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Client-only field-console styling shared by command, radio and attribute panels. */
final class CommandPanelChrome {
    private CommandPanelChrome() { }
    static int accent(String faction) {
        return switch (faction) {
            case "hostile_humans_unified:crimson" -> 0xFFDC8A76;
            case "hostile_humans_unified:azure" -> 0xFF80B3DD;
            case "hostile_humans_unified:verdant" -> 0xFF91C99D;
            case "hostile_humans_unified:ashen" -> 0xFFC0B3CF;
            default -> CommandPanelScreen.GOLD;
        };
    }
    static Component factionName(String faction) {
        if (java.util.Set.of("hostile_humans_unified:crimson", "hostile_humans_unified:azure",
                "hostile_humans_unified:verdant", "hostile_humans_unified:ashen").contains(faction))
            return Component.translatable("screen.hostile_humans_unified.command.faction_name." + faction.substring(faction.indexOf(':')+1));
        return Component.literal(faction);
    }
    static void frame(GuiGraphics g,int x,int y,int w,int h,int accent) {
        g.fill(x-3,y+3,x+w+3,y+h+3,0x70000000);
        g.fill(x,y,x+w,y+h,0xFF46515B);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,0xFA17232C,0xFA101820);
        g.fill(x+1,y+1,x+4,y+h-1,accent);
        g.fill(x+12,y+27,x+w-12,y+28,0xFF394653);
        g.fill(x+12,y+h-43,x+w-12,y+h-42,0xFF394653);
        // Quiet brass corner caps and three receiver bars; no animated clutter.
        g.fill(x,y,x+18,y+2,accent); g.fill(x+w-18,y+h-2,x+w,y+h,accent);
        for(int i=0;i<3;i++) g.fill(x+w-29+i*5,y+16-i*3,x+w-26+i*5,y+20,accent);
    }
    static void badge(GuiGraphics g,int x,int y,String faction) {
        int c=accent(faction);
        g.fill(x,y,x+12,y+16,0xFF26343E);g.fill(x,y,x+2,y+16,c);
        // Different insignia silhouettes supplement the colour cue.
        if(faction.endsWith(":azure")) {for(int i=0;i<3;i++)g.fill(x+4,y+3+i*4,x+10,y+5+i*4,c);}
        else if(faction.endsWith(":verdant")) {for(int i=0;i<3;i++)g.fill(x+4+i*2,y+10-i*3,x+7+i*2,y+12-i*3,c);}
        else if(faction.endsWith(":crimson")) {g.fill(x+4,y+3,x+6,y+13,c);g.fill(x+8,y+3,x+10,y+10,c);}
        else {g.fill(x+6,y+3,x+8,y+13,c);g.fill(x+4,y+7,x+10,y+9,c);}
    }
    static void meter(GuiGraphics g,int x,int y,int w,double value,double maximum,int color) {
        double ratio=Double.isFinite(value)&&Double.isFinite(maximum)&&maximum>0?Math.max(0,Math.min(1,value/maximum)):0;
        g.fill(x,y,x+w,y+3,0xFF30414E);g.fill(x,y,x+(int)Math.round(w*ratio),y+3,color);
    }
    static void control(GuiGraphics g,Font font,Component label,int x,int y,int w,int h,
            boolean hovered,boolean active,boolean selected,boolean danger,int accent) {
        g.fill(x,y,x+w,y+h,selected?accent:0xFF3A4A57);
        g.fillGradient(x+1,y+1,x+w-1,y+h-1,hovered&&active?0xFF334957:0xFF25343F,0xFF1C2832);
        if(selected)g.fill(x+1,y+1,x+3,y+h-1,accent);
        var lines=font.split(label,Math.max(1,w-12));int ty=y+(h-lines.size()*10)/2;
        int color=!active?0xFF71818E:danger?0xFFE49B92:selected?accent:0xFFE5EAF0;
        for(var line:lines){g.drawString(font,line,x+6,ty,color,false);ty+=10;}
    }
}
