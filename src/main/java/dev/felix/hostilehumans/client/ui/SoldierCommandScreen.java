package dev.felix.hostilehumans.client.ui;

import dev.felix.hostilehumans.progression.GrowthSnapshot;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.CommandHealthSnapshot;
import dev.felix.hostilehumans.soldier.SquadCommands;

import dev.felix.hostilehumans.observer.AttributeDetailNetwork;
import dev.felix.hostilehumans.soldier.HumanCommandNetwork;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Single-soldier specialization of the common server-backed command panel. */
public final class SoldierCommandScreen extends CommandPanelScreen {
    private final UUID sessionToken,humanId;private final int entityId;private Component name;private String faction;
    private long dismissConfirmUntil;
    private CommandHealthSnapshot health;
    private SoldierCommandScreen(UUID token,UUID human,int entityId,Component name,CommandView state,GrowthSnapshot growth,String faction,CommandHealthSnapshot health){
        super(Component.translatable("screen.hostile_humans_unified.soldier.title"),state,growth);
        sessionToken=token;humanId=human;this.entityId=entityId;this.name=name;this.faction=faction;this.health=health;
    }
    public static void show(UUID token,UUID human,int entityId,boolean opening,Component name,CommandView view,GrowthSnapshot growth,String faction,CommandHealthSnapshot health){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof SoldierCommandScreen s&&s.sessionToken.equals(token)&&s.humanId.equals(human)&&s.entityId==entityId){s.state=view;s.growth=growth;s.name=name;s.faction=faction;s.health=health;s.refreshSnapshot();}
        else if(opening)mc.setScreen(new SoldierCommandScreen(token,human,entityId,name,view,growth,faction,health));
    }
    public static void invalidate(UUID token){if(Minecraft.getInstance().screen instanceof SoldierCommandScreen s&&s.sessionToken.equals(token)){if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.displayClientMessage(Component.translatable("message.hostile_humans_unified.command.failed"),false);s.onClose();}}
    @Override protected CommandHealthSnapshot healthSnapshot(){return health;}
    @Override protected int accent(){return CommandPanelChrome.accent(faction);}
    @Override protected String headerFaction(){return faction;}
    @Override protected Component heading(){return name;}
    @Override protected Component status(){return hasContextWarning()?contextReason():Component.translatable("screen.hostile_humans_unified.command.faction",CommandPanelChrome.factionName(faction));}
    @Override protected void reanchor(){HumanCommandNetwork.reanchor(sessionToken,humanId,entityId);}
    @Override protected void submit(SquadCommands.Update update){HumanCommandNetwork.submit(sessionToken,humanId,entityId,update);}
    @Override protected boolean hasDetails(){return true;}
    @Override protected boolean hasPatrolSettings(){return state.order()==dev.felix.hostilehumans.soldier.SoldierOrder.GARRISON;}
    @Override protected void patrolSettings(){dev.felix.hostilehumans.soldier.PatrolToolNetwork.requestOpen(entityId,humanId);}
    @Override protected void details(){AttributeDetailNetwork.requestOwned(entityId,humanId);}
    @Override protected Component secondaryLabel(){return tr(System.currentTimeMillis()<dismissConfirmUntil?"dismiss_confirm":"dismiss");}
    @Override protected boolean secondaryDanger(){return true;}
    @Override protected void secondary(){if(System.currentTimeMillis()<dismissConfirmUntil)HumanCommandNetwork.sendDismiss(entityId,humanId,sessionToken);else dismissConfirmUntil=System.currentTimeMillis()+3000;}
    @Override public void removed(){if(Minecraft.getInstance().getConnection()!=null)HumanCommandNetwork.closeScreen(sessionToken);super.removed();}
}
