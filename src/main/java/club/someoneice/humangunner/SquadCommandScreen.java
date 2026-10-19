package club.someoneice.humangunner;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Same controls as one soldier; mixed modes and batch outcomes remain visible. */
public final class SquadCommandScreen extends CommandPanelScreen {
    private SquadCommandNetwork.View view;private String result="";private boolean members,suppressReturn;private int memberPage;
    private SquadCommandScreen(SquadCommandNetwork.View view){super(Component.translatable("screen.hostile_humans_unified.command.squad_title"),view.commands(),null);this.view=view;result=view.result().getString();}
    static void show(boolean opening,SquadCommandNetwork.View view){
        Minecraft mc=Minecraft.getInstance();if(mc.screen instanceof SquadCommandScreen s&&s.view.token().equals(view.token())){
            s.view=view;s.state=view.commands();if(!view.result().getString().isEmpty())s.result=view.result().getString();
        }else if(opening)mc.setScreen(new SquadCommandScreen(view));
    }
    static void invalidate(UUID token){if(Minecraft.getInstance().screen instanceof SquadCommandScreen s&&s.view.token().equals(token)){s.suppressReturn=true;s.onClose();}}
    @Override protected Component heading(){return Component.translatable("screen.hostile_humans_unified.command.squad_heading",view.number(),view.name(),view.total());}
    @Override protected Component status(){return Component.translatable("screen.hostile_humans_unified.command.squad_modes",view.modes().getOrDefault(SoldierOrder.FOLLOW,0),view.modes().getOrDefault(SoldierOrder.BODYGUARD,0),view.modes().getOrDefault(SoldierOrder.GARRISON,0),view.available(),view.unloaded());}
    @Override protected int extraHeight(){return 14;}
    @Override protected void renderExtra(GuiGraphics g,int x,int y){g.drawString(font,font.plainSubstrByWidth(!result.isEmpty()?result:hasContextWarning()?contextReason().getString():tr("squad_representative").getString(),330),x+10,y+53,MUTED,false);}
    @Override protected void reanchor(){SquadCommandNetwork.reanchor(view.token(),view.number());}
    @Override protected void submit(SquadCommands.Update update){SquadCommandNetwork.submit(view.token(),view.number(),update);}
    @Override protected Component secondaryLabel(){return tr("squad_members");}
    @Override protected Component closeLabel(){return view.fromRoster()?tr("back_to_roster"):super.closeLabel();}
    @Override protected void secondary(){members=true;memberPage=0;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);if(!members)return;
        float scale=panelScale();g.pose().pushPose();g.pose().scale(scale,scale,1);int x=left(),y=top()+49;
        int sx=(int)(mx/scale),sy=(int)(my/scale);g.fill(x+5,y,x+345,top()+panelHeight()-5,PANEL);
        g.drawString(font,tr("squad_members"),x+12,y+5,GOLD,false);
        int pages=Math.max(1,(view.members().size()+7)/8);memberPage=Math.min(memberPage,pages-1);
        for(int i=0;i<8&&memberPage*8+i<view.members().size();i++){
            var m=view.members().get(memberPage*8+i);String id=m.id().toString().substring(0,8);
            Component text=Component.translatable(m.canSubmit()?"screen.hostile_humans_unified.command.member_loaded":"screen.hostile_humans_unified.command.member_unloaded",id);
            g.drawString(font,text,x+14,y+20+i*17,m.canSubmit()?TEXT:MUTED,false);
        }
        int by=top()+panelHeight()-25;
        button(g,Component.literal("◀"),x+12,by,24,18,sx,sy,memberPage>0,false,false);
        g.drawString(font,(memberPage+1)+" / "+pages,x+45,by+5,MUTED,false);
        button(g,Component.literal("▶"),x+93,by,24,18,sx,sy,memberPage<pages-1,false,false);
        button(g,tr("back"),x+235,by,103,18,sx,sy,true,false,false);g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(!members)return super.mouseClicked(mx,my,button);mx/=panelScale();my/=panelScale();
        int x=left(),by=top()+panelHeight()-25,pages=Math.max(1,(view.members().size()+7)/8);
        if(hit(mx,my,x+12,by,24,18)&&memberPage>0)memberPage--;
        else if(hit(mx,my,x+93,by,24,18)&&memberPage<pages-1)memberPage++;
        else if(hit(mx,my,x+235,by,103,18))members=false;return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(members&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){members=false;return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){
        if(view.fromRoster()&&!suppressReturn&&Minecraft.getInstance().getConnection()!=null)SquadCommandNetwork.returnToRoster(view.token());
        super.onClose();
    }
    @Override public void removed(){if(Minecraft.getInstance().getConnection()!=null)SquadCommandNetwork.close(view.token());super.removed();}
}
