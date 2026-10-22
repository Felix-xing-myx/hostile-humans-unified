package club.someoneice.humangunner;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One layout drives rendering and hit testing; snapshots alone select accepted settings. */
abstract class CommandPanelScreen extends Screen {
    protected static final int PANEL=0xF0101820,SURFACE=0xFF202B36,HOVER=0xFF2A3947,EDGE=0xFF394653,
            TEXT=0xFFE5EAF0,MUTED=0xFF9DADBB,GOLD=0xFFF2C66D,DISABLED=0xFF65717C;
    protected CommandView state;
    protected GrowthSnapshot growth;
    private record Control(Component label,int x,int y,int w,int h,boolean chosen,boolean active,Runnable action) {}
    private final List<Control> controls=new ArrayList<>();
    private record DistanceControl(CommandView.Field field,int value,int x,int y,int w) {}
    private final List<DistanceControl> distances=new ArrayList<>();
    private DistanceControl dragging;
    private int draftDistance;
    private int scroll,maxScroll,contentTop,contentBottom;
    protected CommandPanelScreen(Component title,CommandView state,GrowthSnapshot growth){super(title);this.state=state;this.growth=growth;}
    protected abstract Component heading();
    protected abstract Component status();
    protected abstract void submit(SquadCommands.Update update);
    protected abstract void secondary();
    protected abstract Component secondaryLabel();
    protected abstract void reanchor();
    protected Component closeLabel(){return tr("close");}
    protected Component additionalStatus(){return Component.empty();}
    protected boolean hasContextWarning(){return !List.of("ready","free","empty").contains(state.reason());}
    protected Component contextReason(){return tr("context."+state.reason());}
    protected void details() {}
    protected boolean hasDetails(){return false;}
    protected int panelWidth(){return CommandPanelLayout.width(width);}
    protected int panelHeight(){return CommandPanelLayout.height(height);}
    protected float panelScale(){return 1F;}
    protected int left(){return (width-panelWidth())/2;}
    protected int top(){return (height-panelHeight())/2;}
    protected static Component tr(String suffix){return Component.translatable("screen.hostile_humans_unified.command."+suffix);}
    protected static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    static Component orderName(SoldierOrder order){return Component.translatable("screen.hostile_humans_unified.soldier."+order.name().toLowerCase(Locale.ROOT));}
    static Component combatName(SoldierCombatMode mode){return Component.translatable("screen.hostile_humans_unified.soldier.combat."+mode.name().toLowerCase(Locale.ROOT));}
    protected void button(GuiGraphics g,Component label,int x,int y,int w,int h,int mx,int my,boolean active,boolean chosen,boolean danger){
        g.fill(x,y,x+w,y+h,chosen?GOLD:EDGE);g.fill(x+1,y+1,x+w-1,y+h-1,!active?0xFF242C34:hit(mx,my,x,y,w,h)?HOVER:SURFACE);
        var lines=font.split(label,Math.max(1,w-12));int ty=y+(h-lines.size()*10)/2;
        for(var line:lines){g.drawString(font,line,x+6,ty,!active?DISABLED:danger?0xFFED9B96:chosen?GOLD:TEXT,false);ty+=10;}
    }
    private boolean mixed(CommandView.Field field){return field!=null&&state.mixed().contains(field);}
    private Component fieldLabel(Component label,CommandView.Field field){return mixed(field)?label.copy().append(" · ").append(tr("mixed")):label;}
    private int add(Component label,CommandView.Field field,int x,int y,int w,boolean chosen,boolean active,Runnable action){
        Component shown=fieldLabel(label,field);int h=Math.max(20,font.split(shown,Math.max(1,w-12)).size()*10+8);
        controls.add(new Control(shown,x,y,w,h,chosen&&!mixed(field),active,action));return y+h+6;
    }
    private int text(GuiGraphics g,Component label,int x,int y,int w,int color){for(var line:font.split(label,Math.max(1,w))){g.drawString(font,line,x,y,color,false);y+=11;}return y;}
    private int groupTitle(GuiGraphics g,Component label,int x,int y,int w){return text(g,label,x,y,w,MUTED)+6;}
    private int movement(GuiGraphics g,int x,int y,int w){
        y=groupTitle(g,tr("movement"),x,y,w);
        for(var order:CommandView.ORDERS)y=add(orderName(order),CommandView.Field.ORDER,x,y,w,state.order()==order,true,()->submit(CommandView.order(order)));
        // A mixed squad can edit garrison fields without first overwriting its action modes.
        if(state.order()==SoldierOrder.GARRISON||mixed(CommandView.Field.ORDER)){
            y=add(tr(state.garrison().stay()?"stay_on":"stay_off"),CommandView.Field.STAY,x,y,w,state.garrison().stay(),true,()->submit(CommandView.stay(mixed(CommandView.Field.STAY)||!state.garrison().stay())));
            y=add(tr(state.garrison().chase()?"chase_on":"chase_off"),CommandView.Field.CHASE,x,y,w,state.garrison().chase(),true,()->submit(CommandView.chase(mixed(CommandView.Field.CHASE)||!state.garrison().chase())));
            y=add(tr("reanchor"),CommandView.Field.ORDER,x,y,w,false,true,this::reanchor);
        }
        return y;
    }
    private int combat(GuiGraphics g,int x,int y,int w){
        y=groupTitle(g,tr("combat"),x,y,w);
        for(var mode:CommandView.COMBAT)y=add(combatName(mode),CommandView.Field.COMBAT,x,y,w,state.combat()==mode,true,()->submit(CommandView.combat(mode)));
        y=groupTitle(g,tr("preference"),x,y+2,w);
        for(var preference:CombatPreference.values())y=add(tr("preference."+preference.name().toLowerCase(Locale.ROOT)),CommandView.Field.PREFERENCE,x,y,w,state.preference()==preference,true,()->submit(CommandView.preference(preference)));
        return y;
    }
    private int settings(GuiGraphics g,int x,int y,int w){
        y=groupTitle(g,tr("settings"),x,y,w);
        y=add(tr(state.pickup()?"pickup_on":"pickup_off"),CommandView.Field.PICKUP,x,y,w,state.pickup(),true,()->submit(CommandView.pickup(mixed(CommandView.Field.PICKUP)||!state.pickup())));
        y=distance(g,x,y,w,false);y=distance(g,x,y,w,true);
        if(hasDetails())y=add(tr("attributes"),null,x,y,w,false,true,this::details);
        return y;
    }
    private int distance(GuiGraphics g,int x,int y,int w,boolean garrison){
        CommandView.Field field=garrison?CommandView.Field.RADIUS:CommandView.Field.FOLLOW;
        int value=garrison?state.garrison().radius():state.follow();
        y=text(g,fieldLabel(tr(garrison?"garrison_activity":"owner_activity"),field),x,y,w,MUTED)+6;
        int shown=dragging!=null&&dragging.field()==field?draftDistance:value;
        distances.add(new DistanceControl(field,value,x,y,w));
        g.fill(x,y+8,x+w,y+12,EDGE);
        int knob=x+(int)Math.round((shown-8)/88.0*(w-6));
        g.fill(knob,y+4,knob+6,y+16,GOLD);
        y=text(g,mixed(field)&&dragging==null?tr("mixed"):Component.translatable("screen.hostile_humans_unified.command.distance",shown),x,y+22,w,TEXT);
        return y+8;
    }
    private void dragTo(double mouseX){draftDistance=Math.max(8,Math.min(96,8+(int)Math.round((mouseX-dragging.x())/Math.max(1,dragging.w()-6)*88)));}
    private int growth(GuiGraphics g,int x,int y,int w){
        if(growth==null||!growth.enabled())return y;
        String p="screen.hostile_humans_unified.soldier.growth.";
        y=text(g,Component.translatable(p+"level",growth.level()),x,y,w,GOLD);
        y=text(g,growth.level()>=5?Component.translatable(p+"max_level"):Component.translatable(p+"stage",growth.stagePercent(),amount(growth.stageProgress()),amount(growth.stageRequired())),x,y,w,TEXT);
        y=text(g,Component.translatable(p+"experience",growth.experiencePercent(),growth.experienceMinPercent(),growth.experienceCapPercent()),x,y,w,TEXT);
        return text(g,growth.experiencePercent()>=growth.experienceCapPercent()?Component.translatable(p+"experience_capped"):Component.translatable(p+"next_point",amount(growth.experienceProgress()),amount(growth.experienceRequired())),x,y,w,MUTED)+8;
    }
    private static String amount(double value){return String.format(Locale.ROOT,"%.2f",value);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);int x=left(),y=top(),w=panelWidth(),h=panelHeight(),inner=w-24;
        g.fill(x,y,x+w,y+h,PANEL);
        // Title area is 28; long target names wrap at their natural height.
        g.drawString(font,title,x+12,y+8,TEXT,false);
        contentTop=y+28;contentBottom=y+h-46;
        controls.clear();distances.clear();g.enableScissor(x+12,contentTop,x+w-12,contentBottom);
        int cy=contentTop-scroll;
        cy=text(g,heading(),x+12,cy,inner,GOLD)+6;
        cy=text(g,status(),x+12,cy,inner,MUTED)+6;
        if(!additionalStatus().getString().isEmpty())cy=text(g,additionalStatus(),x+12,cy,inner,MUTED)+6;
        cy=growth(g,x+12,cy,inner);
        int col=CommandPanelLayout.columnWidth(inner);
        if(CommandPanelLayout.twoColumns(inner)){int a=movement(g,x+12,cy,col),b=combat(g,x+20+col,cy,col);cy=Math.max(a,b)+8;}
        else {cy=movement(g,x+12,cy,inner)+8;cy=combat(g,x+12,cy,inner)+8;}
        cy=settings(g,x+12,cy,inner);
        maxScroll=Math.max(0,cy+scroll-contentBottom);scroll=Math.min(scroll,maxScroll);
        for(var c:controls)button(g,c.label(),c.x(),c.y(),c.w(),c.h(),mx,my,c.active(),c.chosen(),false);
        g.disableScissor();
        if(maxScroll>0){int travel=Math.max(0,contentBottom-contentTop-20);int bar=contentTop+travel*scroll/maxScroll;g.fill(x+w-7,bar,x+w-4,bar+20,GOLD);}
        int bw=(inner-6)/2,fy=y+h-32;
        button(g,closeLabel(),x+12,fy,bw,20,mx,my,true,false,false);
        button(g,secondaryLabel(),x+18+bw,fy,inner-bw-6,20,mx,my,true,false,true);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        int x=left(),inner=panelWidth()-24,bw=(inner-6)/2,fy=top()+panelHeight()-32;
        if(hit(mx,my,x+12,fy,bw,20)){onClose();return true;}
        if(hit(mx,my,x+18+bw,fy,inner-bw-6,20)){secondary();return true;}
        if(my>=contentTop&&my<contentBottom)for(var distance:distances)if(hit(mx,my,distance.x(),distance.y(),distance.w(),20)){
            dragging=distance;dragTo(mx);return true;
        }
        if(my>=contentTop&&my<contentBottom)for(var c:controls)if(c.active()&&hit(mx,my,c.x(),c.y(),c.w(),c.h())){c.action().run();return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){
        if(dragging!=null&&button==0){dragTo(mx);return true;}return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){
        if(dragging!=null&&button==0){
            dragTo(mx);var field=dragging.field();int previous=dragging.value();dragging=null;
            if(mixed(field)||draftDistance!=previous)submit(field==CommandView.Field.RADIUS?CommandView.radius(draftDistance):CommandView.follow(draftDistance));
            return true;
        }
        return super.mouseReleased(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){
        if(dragging!=null)return true;
        if(hit(mx,my,left(),contentTop,panelWidth(),Math.max(0,contentBottom-contentTop))){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(delta*26)));return true;}
        return super.mouseScrolled(mx,my,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
}
