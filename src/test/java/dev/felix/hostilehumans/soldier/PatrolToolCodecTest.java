package dev.felix.hostilehumans.soldier;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public final class PatrolToolCodecTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static FriendlyByteBuf buffer(){return new FriendlyByteBuf(Unpooled.buffer());}
    public static void main(String[] args){
        var original=new PatrolToolNetwork.Snapshot(UUID.randomUUID(),UUID.randomUUID(),123,44,true,true,"Soldier",
                List.of(new PatrolToolNetwork.Row(new BlockPos(-8,-60,12),0),new PatrolToolNetwork.Row(new BlockPos(8,-59,12),1200)));
        var b=buffer();try{PatrolToolNetwork.encode(original,b);check(original.equals(PatrolToolNetwork.decode(b)),"identity/revision/position/wait roundtrip");}finally{b.release();}
        b=buffer();try{PatrolToolNetwork.encode(original,b);b.writeByte(1);rejects(b,"trailing snapshot bytes");}finally{b.release();}
        for(int count:new int[]{-1,257}){b=buffer();try{header(b,count);rejects(b,"bounded route rows");}finally{b.release();}}
        for(int ticks:new int[]{-1,1201}){b=buffer();try{header(b,1);b.writeBlockPos(BlockPos.ZERO);b.writeVarInt(ticks);rejects(b,"bounded server wait");}finally{b.release();}}
        var mutable=new BlockPos.MutableBlockPos(1,2,3);var row=new PatrolToolNetwork.Row(mutable,20);mutable.set(7,8,9);
        check(row.position().equals(new BlockPos(1,2,3)),"row detaches mutable world positions");
        var controlled=new PatrolToolNetwork.Snapshot(original.token(),original.human(),123,44,true,true,"Natural human",original.rows(),
                new PatrolToolNetwork.Control(2,true,32,1,"combat","ping_pong"));
        b=buffer();try{PatrolToolNetwork.encode(controlled,b);check(controlled.equals(PatrolToolNetwork.decode(b)),"creative state and suspended/active route snapshot roundtrip");}finally{b.release();}
        var preview=new PatrolToolNetwork.DraftPreview(original.human(),"minecraft:overworld",List.of(BlockPos.ZERO,new BlockPos(7,9,11)));
        b=buffer();try{PatrolToolNetwork.encodeDraft(preview,b);check(preview.equals(PatrolToolNetwork.decodeDraft(b)),"server-confirmed preview roundtrip");}finally{b.release();}
        b=buffer();try{PatrolToolNetwork.encodeDraft(preview,b);b.writeByte(1);rejectsDraft(b,"trailing draft bytes");}finally{b.release();}
        for(int count:new int[]{-1,258}){b=buffer();try{b.writeUUID(UUID.randomUUID());b.writeUtf("minecraft:overworld");b.writeVarInt(count);rejectsDraft(b,"bounded draft count");}finally{b.release();}}
        for(int[] values:new int[][]{{-1,32,0},{3,32,0},{1,7,0},{1,97,0},{1,32,-1},{1,32,256}}) {
            try{new PatrolToolNetwork.Control(values[0],true,values[1],values[2],"waiting","loop");throw new AssertionError("invalid controller state");}
            catch(IllegalArgumentException expected){}
        }
        var mutableDraft=new BlockPos.MutableBlockPos(2,3,4);var detached=new PatrolToolNetwork.DraftPreview(original.human(),"minecraft:overworld",List.of(mutableDraft));
        mutableDraft.set(5,6,7);check(detached.points().get(0).equals(new BlockPos(2,3,4)),"preview detaches mutable block positions");
        System.out.println("PatrolToolCodecTest passed");
    }
    private static void rejectsDraft(FriendlyByteBuf b,String why){try{PatrolToolNetwork.decodeDraft(b);throw new AssertionError(why);}catch(IllegalArgumentException expected){}}
    private static void header(FriendlyByteBuf b,int count){b.writeUUID(UUID.randomUUID());b.writeUUID(UUID.randomUUID());b.writeVarInt(1);b.writeInt(1);b.writeBoolean(true);b.writeBoolean(true);b.writeUtf("test");b.writeVarInt(count);}
    private static void rejects(FriendlyByteBuf b,String why){try{PatrolToolNetwork.decode(b);throw new AssertionError(why);}catch(IllegalArgumentException expected){}}
}
