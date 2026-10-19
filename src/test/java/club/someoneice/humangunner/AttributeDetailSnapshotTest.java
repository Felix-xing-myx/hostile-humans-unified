package club.someoneice.humangunner;

import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** Bounded immutable wire snapshot; requires Minecraft test runtime but no live entity. */
public final class AttributeDetailSnapshotTest {
    public static void main(String[] args) {
        List<AttributeDetailSnapshot.Row> rows=new ArrayList<>();
        for(var dimension:SpecialtyAttributes.Dimension.values())rows.add(new AttributeDetailSnapshot.Row(dimension.key(),1,1.05,1.05,1.05,true,1,"factor",
                List.of(new AttributeDetailSnapshot.Layer("specialty","multiply",1.05)),List.of()));
        var original=new AttributeDetailSnapshot(UUID.randomUUID(),7,"测试人类","hostile_humans_unified:crimson",false,true,1.05F,1F,200,List.of(),rows);
        rows.clear();check(original.rows().size()==13,"defensive list copy");
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{original.write(buffer);var decoded=AttributeDetailSnapshot.read(buffer);check(decoded.equals(original),"roundtrip");check(decoded.currentHealth()==1.05F,"float health preserved");}finally{buffer.release();}
        try{original.rows().clear();throw new AssertionError("mutable rows");}catch(UnsupportedOperationException expected){}
        buffer=new FriendlyByteBuf(Unpooled.buffer());
        try{
            buffer.writeUUID(UUID.randomUUID());buffer.writeVarInt(1);buffer.writeUtf("test");buffer.writeUtf("");buffer.writeBoolean(false);buffer.writeBoolean(true);
            buffer.writeFloat(Float.NaN);buffer.writeFloat(1);buffer.writeLong(1);
            try{AttributeDetailSnapshot.read(buffer);throw new AssertionError("NaN header accepted");}catch(IllegalArgumentException expected){}
        }finally{buffer.release();}
        buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());buffer.writeVarInt(1);buffer.writeUtf("test");buffer.writeUtf("");
            buffer.writeBoolean(false);buffer.writeBoolean(true);buffer.writeFloat(10);buffer.writeFloat(1);buffer.writeLong(1);
            buffer.writeVarInt(0);buffer.writeVarInt(11);
            try{AttributeDetailSnapshot.read(buffer);throw new AssertionError("old eleven-row wire shape accepted");}catch(IllegalArgumentException expected){}
        }finally{buffer.release();}
        System.out.println("AttributeDetailSnapshotTest: all checks passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
