package dev.felix.hostilehumans.monitor;

import dev.felix.hostilehumans.behavior.combat.gun.GunHudData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public final class MonitorWeaponSnapshotTest {
    public static void main(String[] args){
        var expected=new MonitorWeaponSnapshot(UUID.randomUUID(),UUID.randomUUID(),12,7,
                new GunHudData("tacz",new ResourceLocation("tacz","ak47"),31,30,"AUTO",false),9,false,true);
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            MonitorWeaponSnapshot.encode(expected,buffer);
            if(!expected.equals(MonitorWeaponSnapshot.decode(buffer)))throw new AssertionError("snapshot roundtrip");
            buffer.clear();MonitorWeaponSnapshot.encode(expected,buffer);buffer.writeByte(1);
            try {MonitorWeaponSnapshot.decode(buffer);throw new AssertionError("trailing bytes accepted");}
            catch(IllegalArgumentException good){}
        }finally{buffer.release();}
        try {new GunHudData("tacz",new ResourceLocation("tacz","ak47"),-1,30,"AUTO",false);throw new AssertionError("negative ammo");}
        catch(IllegalArgumentException good){}
        if(GunHudData.EMPTY.present())throw new AssertionError("empty backend is visible");
        System.out.println("Monitor weapon snapshot codec and numeric guards passed");
    }
}
