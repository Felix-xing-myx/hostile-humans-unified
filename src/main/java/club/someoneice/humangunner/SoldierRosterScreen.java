package club.someoneice.humangunner;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Server-paged roster. Shared control rectangles survive wrapping, GUI resize and scrolling. */
public final class SoldierRosterScreen extends Screen {
    private static final UUID NONE=new UUID(0,0);
    private record Control(Component label,int x,int y,int w,int h,boolean active,boolean selected,Runnable action) {}
    private final List<Control> controls=new ArrayList<>();
    private final UUID token;
    private List<SoldierRosterNetwork.Entry> entries;
    private List<SoldierRosterNetwork.SquadEntry> squads=List.of();
    private int[] counts,limits;
    private int tier,page,total,squadFilter=-1,editingSquad,scroll,maxScroll,refreshTicks,requestedPage=-1,retryTicks,retries;
    private UUID selected;
    private boolean squadPanel;
    private final Set<UUID> marked=new LinkedHashSet<>();
    private String result="";
    private long dismissConfirmUntil;
    private EditBox squadName;
    private int editTop;
    private SoldierRosterScreen(UUID token,int tier,int page,int total,List<SoldierRosterNetwork.Entry> entries,int[] counts,int[] limits){
        super(tr("screen.hostile_humans_unified.roster.title"));this.token=token;this.tier=tier;this.page=page;this.total=total;this.entries=entries;this.counts=counts;this.limits=limits;
    }
    static void show(UUID token,boolean opening,int tier,int page,int total,List<SoldierRosterNetwork.Entry> entries,int[] counts,int[] limits,int squadFilter,List<SoldierRosterNetwork.SquadEntry> squads,String result){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof SoldierRosterScreen s&&s.token.equals(token)){
            if(s.tier!=tier||s.squadFilter!=squadFilter){s.scroll=0;s.requestedPage=-1;}
            s.entries=entries;s.tier=tier;s.page=page;s.total=total;s.counts=counts;s.limits=limits;s.squadFilter=squadFilter;s.squads=squads;
            if(!result.isEmpty())s.result=result;
            if(s.requestedPage==page)s.requestedPage=-1;
            if(s.selected!=null&&entries.stream().noneMatch(e->e.id().equals(s.selected))){s.selected=null;s.scroll=0;}
        }else if(opening&&(mc.screen==null||mc.screen instanceof SoldierRosterScreen)){
            var s=new SoldierRosterScreen(token,tier,page,total,entries,counts,limits);s.squadFilter=squadFilter;s.squads=squads;s.result=result;mc.setScreen(s);
        }
    }
    static void invalidate(UUID token){if(Minecraft.getInstance().screen instanceof SoldierRosterScreen s&&s.token.equals(token)){
        if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(tr("message.hostile_humans_unified.command.failed"),false);s.onClose();
    }}
    private static Component tr(String key,Object...args){return Component.translatable(key,args);}
    private static Component cmd(String key,Object...args){return tr("screen.hostile_humans_unified.command."+key,args);}
    private static Component roster(String key,Object...args){return tr("screen.hostile_humans_unified.roster."+key,args);}
    private int panelWidth(){return CommandPanelLayout.width(width);}
    private int left(){return (width-panelWidth())/2;}
    private int bodyTop(){return 40;}
    private int bodyBottom(){return height-58;}
    private static boolean hit(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    @Override protected void init(){
        String name=squadName==null?"":squadName.getValue();
        squadName=new EditBox(font,left()+12,bodyTop(),panelWidth()-24,20,cmd("squad_name"));squadName.setMaxLength(64);squadName.setValue(name);squadName.setVisible(false);addWidget(squadName);
    }
    private int text(GuiGraphics g,Component label,int x,int y,int w,int color){
        for(var line:font.split(label,Math.max(1,w))){g.drawString(font,line,x,y,color,false);y+=11;}return y;
    }
    private int add(Component label,int x,int y,int w,boolean active,boolean chosen,Runnable action){
        int h=Math.max(20,font.split(label,Math.max(1,w-12)).size()*10+8);controls.add(new Control(label,x,y,w,h,active,chosen,action));return y+h+6;
    }
    private int pair(Component a,Component b,int x,int y,int w,boolean aa,boolean ab,Runnable ra,Runnable rb){
        int col=(w-6)/2;if(col<120){y=add(a,x,y,w,aa,false,ra);return add(b,x,y,w,ab,false,rb);}
        return Math.max(add(a,x,y,col,aa,false,ra),add(b,x+col+6,y,w-col-6,ab,false,rb));
    }
    private void drawControl(GuiGraphics g,Control c,int mx,int my){
        g.fill(c.x,c.y,c.x+c.w,c.y+c.h,c.selected?CommandPanelScreen.GOLD:CommandPanelScreen.EDGE);
        g.fill(c.x+1,c.y+1,c.x+c.w-1,c.y+c.h-1,hit(mx,my,c.x,c.y,c.w,c.h)?CommandPanelScreen.HOVER:CommandPanelScreen.SURFACE);
        int y=c.y+(c.h-font.split(c.label,Math.max(1,c.w-12)).size()*10)/2;
        for(var line:font.split(c.label,Math.max(1,c.w-12))){g.drawString(font,line,c.x+6,y,c.active?CommandPanelScreen.TEXT:CommandPanelScreen.DISABLED,false);y+=10;}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);int x=left(),w=panelWidth(),inner=w-24;
        g.fill(x,12,x+w,height-12,CommandPanelScreen.PANEL);g.drawString(font,title,x+12,20,CommandPanelScreen.GOLD,false);
        controls.clear();g.enableScissor(x+12,bodyTop(),x+w-12,bodyBottom());int y=bodyTop()-scroll;
        squadName.setVisible(false);
        if(squadPanel)y=renderSquads(g,x+12,y,inner);
        else if(selected!=null)y=renderDetail(g,x+12,y,inner);
        else y=renderList(g,x+12,y,inner);
        maxScroll=Math.max(0,y+scroll-bodyBottom());scroll=Math.min(scroll,maxScroll);
        for(var c:controls)drawControl(g,c,mx,my);
        if(squadName.isVisible())squadName.render(g,mx,my,partial);
        g.disableScissor();
        if(maxScroll>0){int bar=bodyTop()+Math.max(0,bodyBottom()-bodyTop()-20)*scroll/maxScroll;g.fill(x+w-7,bar,x+w-4,bar+20,CommandPanelScreen.GOLD);}
        int bw=(inner-6)/2,fy=height-44;
        drawControl(g,new Control(selected!=null||squadPanel?cmd("back"):cmd("close"),x+12,fy,bw,20,true,false,this::back),mx,my);
        drawControl(g,new Control(cmd("squad_manage",marked.size()),x+18+bw,fy,inner-bw-6,20,true,false,this::openSquads),mx,my);
    }
    private int renderList(GuiGraphics g,int x,int y,int w){
        for(int i=0;i<4;i++)y=text(g,tr("message.hostile_humans_unified.roster.tier."+i,counts[i],limits[i]),x,y,w,CommandPanelScreen.MUTED);
        y+=8;
        for(int filter=-1;filter<4;filter++){final int f=filter;y=add(f<0?roster("all"):roster("tier."+f),x,y,w,true,tier==f,()->SoldierRosterNetwork.request(token,7,NONE,f));}
        y=text(g,cmd("squad_filter",squadFilter<0?"*":squadFilter),x,y,w,CommandPanelScreen.MUTED)+8;
        for(var e:entries){
            Component name=Component.literal((marked.contains(e.id())?"☑ ":"☐ ")+(e.name().isBlank()?roster("unnamed").getString():e.name()));
            y=add(name,x,y,w,true,e.id().equals(selected),()->{selected=e.id();scroll=0;dismissConfirmUntil=0;});
            y=text(g,roster("status_short."+e.status()),x,y,w,CommandPanelScreen.MUTED)+6;
            y=pair(cmd("mark"),roster("recall_one"),x,y,w,true,true,()->{if(!marked.remove(e.id())&&marked.size()<64)marked.add(e.id());},()->SoldierRosterNetwork.request(token,3,e.id()));
            y=pair(roster("move_earlier"),roster("move_later"),x,y,w,e.canMoveEarlier(),e.canMoveLater(),()->SoldierRosterNetwork.request(token,11,e.id()),()->SoldierRosterNetwork.request(token,12,e.id()));y+=8;
        }
        int pages=Math.max(1,(total+SoldierRosterNetwork.PAGE_SIZE-1)/SoldierRosterNetwork.PAGE_SIZE);
        y=text(g,Component.literal((page+1)+" / "+pages),x,y,w,CommandPanelScreen.MUTED)+6;
        y=pair(Component.literal("◀"),Component.literal("▶"),x,y,w,page>0,page<pages-1,()->requestPage(page-1),()->requestPage(page+1));
        y=pair(roster("refresh"),roster("recall"),x,y,w,true,true,()->SoldierRosterNetwork.request(token,0,NONE),()->SoldierRosterNetwork.request(token,2,NONE));return y;
    }
    private int renderDetail(GuiGraphics g,int x,int y,int w){
        var e=entries.stream().filter(entry->entry.id().equals(selected)).findFirst().orElse(null);if(e==null)return y;
        y=text(g,Component.literal(e.name()),x,y,w,CommandPanelScreen.GOLD)+6;
        y=text(g,Component.literal(e.faction()+" · "+e.dimension()+" · "+e.x()+", "+e.y()+", "+e.z()),x,y,w,CommandPanelScreen.MUTED);
        y=text(g,roster("status_short."+e.status()),x,y,w,CommandPanelScreen.MUTED);
        y=text(g,e.health()>=0?roster("health",e.health(),e.maxHealth()):roster("last_known"),x,y,w,CommandPanelScreen.TEXT)+8;
        if(e.growth()!=null&&e.growth().enabled()){
            var growth=e.growth();String p="screen.hostile_humans_unified.soldier.growth.";
            y=text(g,tr(p+"level",growth.level()),x,y,w,CommandPanelScreen.GOLD);
            y=text(g,tr(p+"experience",growth.experiencePercent(),growth.experienceMinPercent(),growth.experienceCapPercent()),x,y,w,CommandPanelScreen.TEXT);
            y=text(g,growth.level()>=5?tr(p+"max_level"):tr(p+"stage",growth.stagePercent(),growth.stageProgress(),growth.stageRequired()),x,y,w,CommandPanelScreen.MUTED);
            y=text(g,growth.experiencePercent()>=growth.experienceCapPercent()?tr(p+"experience_capped"):tr(p+"next_point",growth.experienceProgress(),growth.experienceRequired()),x,y,w,CommandPanelScreen.MUTED)+8;
        }
        boolean loaded="online".equals(e.status());
        y=text(g,cmd("movement"),x,y,w,CommandPanelScreen.MUTED)+6;
        for(int i=0;i<CommandView.ORDERS.size();i++){var order=CommandView.ORDERS.get(i);final int index=i;y=add(CommandPanelScreen.orderName(order),x,y,w,loaded,e.order().equals(order.name()),()->SoldierRosterNetwork.request(token,4,e.id(),index));}
        y+=8;y=text(g,cmd("combat"),x,y,w,CommandPanelScreen.MUTED)+6;
        for(var mode:CommandView.COMBAT)y=add(CommandPanelScreen.combatName(mode),x,y,w,loaded,e.combat().equals(mode.name()),()->SoldierRosterNetwork.request(token,5,e.id(),mode.ordinal()));
        y+=8;y=text(g,cmd("settings"),x,y,w,CommandPanelScreen.MUTED)+6;
        y=add(cmd(e.pickup()?"pickup_on":"pickup_off"),x,y,w,loaded,e.pickup(),()->SoldierRosterNetwork.request(token,6,e.id(),e.pickup()?0:1));
        y=pair(cmd("full_panel"),cmd("attributes"),x,y,w,loaded,loaded&&e.entityId()>=0,()->SoldierRosterNetwork.request(token,13,e.id()),()->AttributeDetailNetwork.requestOwned(e.entityId(),e.id()));
        y=pair(roster("recall_one"),roster("refresh"),x,y,w,true,true,()->SoldierRosterNetwork.request(token,3,e.id()),()->SoldierRosterNetwork.request(token,0,NONE));
        return add(System.currentTimeMillis()<dismissConfirmUntil?roster("confirm"):tr("screen.hostile_humans_unified.soldier.dismiss"),x,y,w,true,false,()->{
            if(System.currentTimeMillis()<dismissConfirmUntil){SoldierRosterNetwork.request(token,1,e.id());dismissConfirmUntil=0;}else dismissConfirmUntil=System.currentTimeMillis()+4000;
        });
    }
    private int renderSquads(GuiGraphics g,int x,int y,int w){
        y=text(g,cmd("squad_select",marked.size()),x,y,w,CommandPanelScreen.GOLD)+6;
        if(!result.isEmpty())y=text(g,Component.literal(result),x,y,w,CommandPanelScreen.MUTED)+8;
        for(var squad:squads){
            y=add(Component.literal(squad.number()+" · "+squad.name()+" ("+squad.members()+")"),x,y,w,true,editingSquad==squad.number(),()->{
                editingSquad=squad.number();squadName.setValue(squad.name());setFocused(squadName);squadName.setFocused(true);
            });
            if(editingSquad==squad.number()){
                editTop=y;squadName.setX(x);squadName.setY(y);squadName.setWidth(w);squadName.setVisible(true);y+=26;
                y=add(cmd("rename"),x,y,w,true,false,this::saveSquadName);
            }
            y=pair(cmd("assign"),cmd("filter"),x,y,w,!marked.isEmpty(),true,()->SoldierRosterNetwork.assign(token,squad.number(),List.copyOf(marked)),()->{SoldierRosterNetwork.request(token,14,NONE,squad.number());back();});
            y=pair(cmd("bind"),cmd("squad_quick"),x,y,w,true,true,()->SoldierRosterNetwork.bind(token,squad.number()),()->SoldierRosterNetwork.request(token,15,NONE,squad.number()));y+=8;
        }
        y=add(cmd("remove_squad"),x,y,w,!marked.isEmpty(),false,()->SoldierRosterNetwork.assign(token,0,List.copyOf(marked)));
        y=pair(cmd("all_squads"),cmd("no_squad"),x,y,w,true,true,()->{SoldierRosterNetwork.request(token,14,NONE,-1);back();},()->{SoldierRosterNetwork.request(token,14,NONE,0);back();});return y;
    }
    private void saveSquadName(){if(editingSquad>0)SoldierRosterNetwork.rename(token,editingSquad,squadName.getValue());editingSquad=0;squadName.setVisible(false);setFocused(null);}
    private void openSquads(){if(selected!=null)marked.add(selected);selected=null;squadPanel=true;editingSquad=0;scroll=0;}
    private void back(){if(squadPanel){squadPanel=false;editingSquad=0;squadName.setVisible(false);setFocused(null);scroll=0;}else if(selected!=null){selected=null;scroll=0;}else onClose();}
    private void requestPage(int target){target=Math.max(0,Math.min(Math.max(0,(total-1)/SoldierRosterNetwork.PAGE_SIZE),target));if(target==(requestedPage>=0?requestedPage:page))return;requestedPage=target;retryTicks=0;retries=0;SoldierRosterNetwork.request(token,8,NONE,target);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);int x=left(),inner=panelWidth()-24,bw=(inner-6)/2,fy=height-44;
        if(hit(mx,my,x+12,fy,bw,20)){back();return true;}if(hit(mx,my,x+18+bw,fy,inner-bw-6,20)){openSquads();return true;}
        if(my>=bodyTop()&&my<bodyBottom()){
            if(squadName.isVisible()&&hit(mx,my,squadName.getX(),editTop,squadName.getWidth(),20))return squadName.mouseClicked(mx,my,button);
            for(var c:controls)if(c.active&&hit(mx,my,c.x,c.y,c.w,c.h)){c.action.run();return true;}
        }
        return false;
    }
    @Override public boolean mouseScrolled(double mx,double my,double delta){if(hit(mx,my,left(),bodyTop(),panelWidth(),bodyBottom()-bodyTop())){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(delta*26)));return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_ESCAPE){back();return true;}
        if(squadPanel&&editingSquad>0&&key==GLFW.GLFW_KEY_ENTER){saveSquadName();return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void tick(){
        super.tick();if(squadName!=null&&squadName.isVisible())squadName.tick();
        if(requestedPage>=0&&page!=requestedPage&&retries<3&&++retryTicks>=4){retryTicks=0;retries++;SoldierRosterNetwork.request(token,8,NONE,requestedPage);}
        if(++refreshTicks>=100){refreshTicks=0;SoldierRosterNetwork.request(token,10,NONE);}
    }
    @Override public void removed(){if(Minecraft.getInstance().getConnection()!=null)SoldierRosterNetwork.request(token,9,NONE);super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
