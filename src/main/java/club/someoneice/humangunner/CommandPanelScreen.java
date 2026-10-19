package club.someoneice.humangunner;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared display/interaction layout. Only accepted server snapshots change the selected settings. */
abstract class CommandPanelScreen extends Screen {
    protected static final int PANEL_WIDTH=350,PANEL_HEIGHT=227;
    protected static final int PANEL=0xF0101820,SURFACE=0xFF202B36,HOVER=0xFF2A3947,EDGE=0xFF394653,
            TEXT=0xFFE5EAF0,MUTED=0xFF9DADBB,GOLD=0xFFF2C66D,DISABLED=0xFF65717C;
    protected CommandView state;
    protected GrowthSnapshot growth;
    private boolean dragging;
    private int draftDistance;
    protected CommandPanelScreen(Component title,CommandView state,GrowthSnapshot growth){super(title);this.state=state;this.growth=growth;}
    protected abstract Component heading();
    protected abstract Component status();
    protected abstract void submit(SquadCommands.Update update);
    protected abstract void secondary();
    protected abstract Component secondaryLabel();
    protected Component closeLabel(){return tr("close");}
    protected abstract void reanchor();
    protected boolean hasContextWarning(){return !java.util.List.of("ready","free","empty").contains(state.reason());}
    protected Component contextReason(){return tr("context."+state.reason());}
    protected void details() {}
    protected boolean hasDetails(){return false;}
    protected int growthHeight(){return growth!=null&&growth.enabled()?72:0;}
    protected int extraHeight(){return 0;}
    protected void renderExtra(GuiGraphics graphics,int x,int y) {}
    protected int panelHeight(){return PANEL_HEIGHT+growthHeight()+extraHeight();}
    protected float panelScale(){return Math.min(1F,Math.min(width/(PANEL_WIDTH+12F),height/(panelHeight()+12F)));}
    protected int left(){return (int)(width/panelScale()/2)-PANEL_WIDTH/2;}
    protected int top(){return (int)(height/panelScale()/2)-panelHeight()/2;}
    protected static Component tr(String suffix){return Component.translatable("screen.hostile_humans_unified.command."+suffix);}
    protected static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    static Component orderName(SoldierOrder order){return Component.translatable("screen.hostile_humans_unified.soldier."+order.name().toLowerCase(Locale.ROOT));}
    static Component combatName(SoldierCombatMode mode){return Component.translatable("screen.hostile_humans_unified.soldier.combat."+mode.name().toLowerCase(Locale.ROOT));}
    protected void button(GuiGraphics g,Component label,int x,int y,int w,int h,int mx,int my,boolean active,boolean chosen,boolean danger){
        g.fill(x,y,x+w,y+h,chosen?GOLD:EDGE);g.fill(x+1,y+1,x+w-1,y+h-1,!active?0xFF242C34:hit(mx,my,x,y,w,h)?HOVER:SURFACE);
        g.drawCenteredString(font,font.plainSubstrByWidth(label.getString(),w-6),x+w/2,y+(h-8)/2,!active?DISABLED:danger?0xFFED9B96:chosen?GOLD:TEXT);
    }
    private boolean garrison(){return state.order()==SoldierOrder.GARRISON;}
    private boolean distanceEnabled(){return !garrison()||!state.garrison().stay()||state.garrison().chase();}
    private int distance(){return garrison()?state.garrison().radius():state.follow();}
    private static String amount(double value){return String.format(Locale.ROOT,"%.2f",Double.isFinite(value)?Math.max(0,value):0);}
    private void drawProgress(GuiGraphics g,int x,int y,int w,double fraction){g.fill(x,y,x+w,y+3,SURFACE);g.fill(x,y,x+(int)(w*Math.max(0,Math.min(1,fraction))),y+3,GOLD);}
    private void renderGrowth(GuiGraphics g,int x,int y){
        if(growthHeight()==0)return;String p="screen.hostile_humans_unified.soldier.growth.";
        g.drawString(font,Component.translatable(p+"level",growth.level()),x+13,y+51,GOLD,false);
        g.drawString(font,growth.level()>=5?Component.translatable(p+"max_level"):
                Component.translatable(p+"stage",growth.stagePercent(),amount(growth.stageProgress()),amount(growth.stageRequired())),x+13,y+63,TEXT,false);
        drawProgress(g,x+13,y+74,324,growth.level()>=5?1:growth.stageRequired()>0?growth.stageProgress()/growth.stageRequired():0);
        g.drawString(font,Component.translatable(p+"experience",growth.experiencePercent(),growth.experienceMinPercent(),growth.experienceCapPercent()),x+13,y+83,TEXT,false);
        g.drawString(font,growth.experiencePercent()>=growth.experienceCapPercent()?Component.translatable(p+"experience_capped"):
                Component.translatable(p+"next_point",amount(growth.experienceProgress()),amount(growth.experienceRequired())),x+13,y+99,MUTED,false);
        g.fill(x+9,y+115,x+341,y+116,EDGE);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);float scale=panelScale();g.pose().pushPose();g.pose().scale(scale,scale,1);
        int mx=(int)(mouseX/scale),my=(int)(mouseY/scale),x=left(),y=top();
        g.fill(x,y,x+350,y+panelHeight(),EDGE);g.fill(x+2,y+2,x+348,y+panelHeight()-2,PANEL);
        g.drawString(font,title,x+10,y+9,TEXT,false);
        g.drawString(font,font.plainSubstrByWidth(heading().getString(),330),x+10,y+24,GOLD,false);
        g.drawString(font,font.plainSubstrByWidth(status().getString(),330),x+10,y+37,MUTED,false);
        g.fill(x+9,y+48,x+341,y+49,EDGE);renderGrowth(g,x,y);renderExtra(g,x,y+growthHeight());y+=growthHeight()+extraHeight();
        int lx=x+10,cx=x+122,rx=x+234;
        g.drawString(font,tr("movement"),lx,y+57,MUTED,false);g.drawString(font,tr("combat"),cx,y+57,MUTED,false);
        g.drawString(font,tr("settings"),rx,y+57,MUTED,false);
        for(int i=0;i<CommandView.ORDERS.size();i++){var o=CommandView.ORDERS.get(i);button(g,orderName(o),lx,y+71+i*23,106,20,mx,my,true,state.order()==o,false);}
        if(garrison()){
            button(g,tr(state.garrison().stay()?"stay_on":"stay_off"),lx,y+145,106,18,mx,my,true,state.garrison().stay(),false);
            button(g,tr(state.garrison().chase()?"chase_on":"chase_off"),lx,y+166,106,18,mx,my,true,state.garrison().chase(),false);
            button(g,tr("reanchor"),lx,y+187,106,12,mx,my,true,false,false);
        }
        for(int i=0;i<CommandView.COMBAT.size();i++){var m=CommandView.COMBAT.get(i);button(g,combatName(m),cx,y+71+i*28,106,23,mx,my,true,state.combat()==m,false);}
        button(g,tr(state.pickup()?"pickup_on":"pickup_off"),rx,y+71,106,20,mx,my,true,state.pickup(),false);
        g.drawString(font,tr(garrison()?"garrison_activity":"owner_activity"),rx,y+102,MUTED,false);
        int shown=dragging?draftDistance:distance();boolean enabled=distanceEnabled();
        g.fill(rx,y+122,rx+106,y+126,enabled?EDGE:0xFF343B40);
        int knob=rx+(int)Math.round((shown-8)/88.0*100);g.fill(knob,y+118,knob+6,y+130,enabled?GOLD:DISABLED);
        g.drawString(font,enabled?Component.translatable("screen.hostile_humans_unified.command.distance",shown):tr("not_applicable"),rx,y+138,enabled?TEXT:DISABLED,false);
        if(garrison()&&state.garrison().chase()){
            g.drawString(font,Component.translatable("screen.hostile_humans_unified.command.outer",state.outer()),rx,y+153,MUTED,false);
            g.drawString(font,Component.translatable("screen.hostile_humans_unified.command.duration",state.chaseTicks()/20),rx,y+166,MUTED,false);
        }
        if(hasDetails())button(g,tr("attributes"),rx,y+183,106,16,mx,my,true,false,false);
        button(g,closeLabel(),lx,y+202,90,18,mx,my,true,false,false);
        button(g,secondaryLabel(),x+230,y+202,110,18,mx,my,true,false,true);
        if(hit(mx,my,cx,y+71,106,107)){
            int row=(my-y-71)/28;if(row<CommandView.COMBAT.size())g.renderTooltip(font,
                    tr("combat_hint."+CommandView.COMBAT.get(row).name().toLowerCase(Locale.ROOT)),mx,my);
        }
        g.pose().popPose();
    }
    private void dragTo(double mouseX){draftDistance=Math.max(8,Math.min(96,8+(int)Math.round((mouseX-(left()+234))/100*88)));}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return super.mouseClicked(mx,my,button);mx/=panelScale();my/=panelScale();
        int x=left(),y=top()+growthHeight()+extraHeight();
        for(int i=0;i<CommandView.ORDERS.size();i++)if(hit(mx,my,x+10,y+71+i*23,106,20)){submit(CommandView.order(CommandView.ORDERS.get(i)));return true;}
        if(garrison()&&hit(mx,my,x+10,y+145,106,18)){submit(CommandView.settings(new GarrisonSettings(!state.garrison().stay(),state.garrison().chase(),state.garrison().radius())));return true;}
        if(garrison()&&hit(mx,my,x+10,y+166,106,18)){submit(CommandView.settings(new GarrisonSettings(state.garrison().stay(),!state.garrison().chase(),state.garrison().radius())));return true;}
        if(garrison()&&hit(mx,my,x+10,y+187,106,12)){reanchor();return true;}
        for(int i=0;i<CommandView.COMBAT.size();i++)if(hit(mx,my,x+122,y+71+i*28,106,23)){submit(CommandView.combat(CommandView.COMBAT.get(i)));return true;}
        if(hit(mx,my,x+234,y+71,106,20)){submit(CommandView.pickup(!state.pickup()));return true;}
        if(distanceEnabled()&&hit(mx,my,x+234,y+115,106,21)){dragging=true;dragTo(mx);return true;}
        if(hasDetails()&&hit(mx,my,x+234,y+183,106,16)){details();return true;}
        if(hit(mx,my,x+10,y+202,90,18)){onClose();return true;}
        if(hit(mx,my,x+230,y+202,110,18)){secondary();return true;}
        return super.mouseClicked(mx*panelScale(),my*panelScale(),button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(dragging&&button==0){dragTo(mx/panelScale());return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(dragging&&button==0){dragTo(mx/panelScale());dragging=false;
            if(draftDistance!=distance())submit(garrison()?CommandView.settings(new GarrisonSettings(state.garrison().stay(),state.garrison().chase(),draftDistance)):CommandView.follow(draftDistance));return true;}
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean isPauseScreen(){return false;}
}
