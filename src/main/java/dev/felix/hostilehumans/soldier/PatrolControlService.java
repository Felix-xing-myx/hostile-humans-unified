package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.faction.HumanRelations;
import net.minecraft.server.level.ServerPlayer;

/** Creative natural-state authoring uses the same mode/settings entrypoints as monitor commands. */
public final class PatrolControlService {
    private PatrolControlService() {}
    public static boolean mayControl(ServerPlayer p,Human h) {
        return p.isCreative()&&!p.isSpectator()&&p.isAlive()&&h.isAlive()&&p.level()==h.level()
                &&HumanRelations.effectiveOwner(h)==null;
    }
    static PatrolToolNetwork.Control view(Human h,NaturalState.ControllerData data) {
        var settings=GarrisonSettings.read(data.author(),new GarrisonSettings(true,false,OrderDistances.defaults().garrisonRadius()));
        String phase=!data.active()?"paused":h.isNoAi()?"disabled":h.isFleeing?"fleeing":
                dev.felix.hostilehumans.monitor.HumanMonitorControl.controls(h)?"controlled":
                dev.felix.hostilehumans.inventory.HumanInventorySession.isEditing(h)?"inventory":
                (h.guardCombatState.returning()||SoldierOrder.isReturningToHoldPosition(h))?"returning":
                h.getTarget()!=null?"combat":settings.stay()?"holding":data.runtime().getString("status");
        if(phase.length()>64)phase="idle";
        String pattern=data.author().contains("route")?(data.author().contains("route_mode")?data.author().getString("route_mode"):"loop"):"none";
        if(!java.util.List.of("loop","ping_pong","once","none").contains(pattern))pattern="none";
        return new PatrolToolNetwork.Control(data.active()?(settings.stay()?1:2):0,settings.chase(),settings.radius(),
                Math.max(0,Math.min(255,data.runtime().getInt("index"))),phase,pattern);
    }
    static void mode(Human h,int mode) {
        if(mode==0){NaturalState.suspendForController(h);return;}
        if(!NaturalState.hasState(h))NaturalState.restoreForController(h);
        var old=GarrisonSettings.natural(h);NaturalState.set(h,new GarrisonSettings(mode==1,old.chase(),old.radius()));
    }
    static void settings(Human h,Boolean chase,Integer radius) {
        if(!NaturalState.hasState(h))return;
        var old=GarrisonSettings.natural(h);
        NaturalState.set(h,new GarrisonSettings(old.stay(),chase==null?old.chase():chase,radius==null?old.radius():radius));
    }
}
