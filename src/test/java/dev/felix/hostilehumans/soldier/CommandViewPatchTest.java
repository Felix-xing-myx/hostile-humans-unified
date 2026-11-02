package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.behavior.combat.CombatPreference;
import dev.felix.hostilehumans.client.ui.CommandPanelLayout;
import dev.felix.hostilehumans.soldier.CommandView;
import dev.felix.hostilehumans.soldier.GarrisonSettings;
import dev.felix.hostilehumans.soldier.SoldierCombatMode;
import dev.felix.hostilehumans.soldier.SoldierOrder;
import dev.felix.hostilehumans.soldier.SquadCommands;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import java.util.Set;

/** Network round trips and per-member patch preservation; no world or live entities required. */
public final class CommandViewPatchTest {
    public static void main(String[] args) {
        var a=new GarrisonSettings(true,false,24);var b=new GarrisonSettings(false,true,80);
        var patch=CommandView.radius(48);
        check(CommandView.patchedSettings(a,patch).equals(new GarrisonSettings(true,false,48)),"member A flags preserved");
        check(CommandView.patchedSettings(b,patch).equals(new GarrisonSettings(false,true,48)),"member B flags preserved");
        for(var update:java.util.List.of(patch,CommandView.stay(false),CommandView.chase(true),CommandView.follow(40),CommandView.preference(CombatPreference.TRIDENT))) {
            var buffer=new FriendlyByteBuf(Unpooled.buffer());
            try { CommandView.writeUpdate(buffer,update);check(CommandView.readUpdate(buffer).equals(update),"nullable patch round trip");check(!buffer.isReadable(),"patch fully consumed"); }
            finally { buffer.release(); }
        }
        var original=new CommandView(SoldierOrder.GARRISON,SoldierCombatMode.ACTIVE,true,a,32,48,200,"ready",CombatPreference.CROSSBOW,Set.of(CommandView.Field.STAY,CommandView.Field.PREFERENCE));
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try { original.encode(buffer);check(CommandView.decode(buffer).equals(original),"preference and mixed snapshot round trip"); }
        finally { buffer.release(); }
        check(!CommandView.valid(CommandView.radius(7)),"low distance rejected");
        check(!CommandView.valid(CommandView.radius(97)),"high distance rejected");
        check(!CommandView.valid(new SquadCommands.Update(null,a,null,null,null,true,null,null,null)),"ambiguous whole and partial settings rejected");
        check(CommandPanelLayout.width(320)==296&&CommandPanelLayout.height(240)==216,"320x240 margins");
        check(CommandPanelLayout.twoColumns(272)&&!CommandPanelLayout.twoColumns(232),"minimum column width");
        check(CommandPanelLayout.width(1920)==400,"wide screen cap");
        check(CommandPanelLayout.height(1080)==420,"tall screen keeps compact centred panel");
        System.out.println("CommandViewPatchTest: all checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
