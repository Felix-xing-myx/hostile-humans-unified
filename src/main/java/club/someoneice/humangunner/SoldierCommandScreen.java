package club.someoneice.humangunner;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Single-soldier specialization of the common server-backed command panel. */
public final class SoldierCommandScreen extends CommandPanelScreen {
    private final UUID sessionToken,humanId;private final int entityId;private Component name;private String faction;
    private long dismissConfirmUntil;
    private SoldierCommandScreen(UUID token,UUID human,int entityId,Component name,CommandView state,GrowthSnapshot growth,String faction){
        super(Component.translatable("screen.hostile_humans_unified.soldier.title"),state,growth);
        sessionToken=token;humanId=human;this.entityId=entityId;this.name=name;this.faction=faction;
    }
    static void show(UUID token,UUID human,int entityId,boolean opening,Component name,CommandView view,GrowthSnapshot growth,String faction){
        Minecraft mc=Minecraft.getInstance();
        if(mc.screen instanceof SoldierCommandScreen s&&s.sessionToken.equals(token)&&s.humanId.equals(human)&&s.entityId==entityId){s.state=view;s.growth=growth;s.name=name;s.faction=faction;}
        else if(opening)mc.setScreen(new SoldierCommandScreen(token,human,entityId,name,view,growth,faction));
    }
    static void invalidate(UUID token){if(Minecraft.getInstance().screen instanceof SoldierCommandScreen s&&s.sessionToken.equals(token))s.onClose();}
    @Override protected Component heading(){return name;}
    @Override protected Component status(){return hasContextWarning()?contextReason():Component.translatable("screen.hostile_humans_unified.command.faction",faction);}
    @Override protected void reanchor(){HumanCommandNetwork.reanchor(sessionToken,humanId,entityId);}
    @Override protected void submit(SquadCommands.Update update){HumanCommandNetwork.submit(sessionToken,humanId,entityId,update);}
    @Override protected boolean hasDetails(){return true;}
    @Override protected void details(){AttributeDetailNetwork.requestOwned(entityId,humanId);}
    @Override protected Component secondaryLabel(){return tr(System.currentTimeMillis()<dismissConfirmUntil?"dismiss_confirm":"dismiss");}
    @Override protected void secondary(){if(System.currentTimeMillis()<dismissConfirmUntil)HumanCommandNetwork.sendDismiss(entityId,humanId,sessionToken);else dismissConfirmUntil=System.currentTimeMillis()+3000;}
    @Override public void removed(){if(Minecraft.getInstance().getConnection()!=null)HumanCommandNetwork.closeScreen(sessionToken);super.removed();}
}
