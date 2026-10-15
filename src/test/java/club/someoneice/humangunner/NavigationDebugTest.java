package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.NavigationObserverSelection;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** No game launch: focus lifetime, independent membership, bounded frame round trips and invalid input. */
public final class NavigationDebugTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var selection = new NavigationObserverSelection<Integer>(8);
        for (int i=0;i<8;i++) check(selection.add(i), "eight observers fit");
        var before = selection.entries();
        check(!selection.add(9) && selection.focused()==7 && selection.entries().equals(before), "overflow preserves focus and members");
        for (int i=0;i<1000;i++) {
            selection.cycle();
            check(selection.entries().equals(before), "cycling cannot cancel members or change colors");
        }
        check(selection.add(3) && selection.focused()==3 && selection.entries().equals(before), "reselect only focuses");
        selection.remove(3);
        check(selection.contains(2) && selection.contains(4), "removing one leaves other potential tasks");
        check(selection.add(9), "replacement fits");
        for (var entry : selection.entries()) check(entry.slot()==(entry.key()==9 ? 3 : entry.key()), "stable colors and vacant slot reuse");
        for (var entry : selection.entries()) selection.remove(entry.key());
        check(selection.isEmpty() && selection.focused()==null && selection.cycle()==null, "clear and empty cycle");

        var list = new ArrayList<NavigationDebugFrame.Entry>();
        for (int slot=0;slot<8;slot++) list.add(new NavigationDebugFrame.Entry(slot, sample(slot, 256, 16, false)));
        var frame = new NavigationDebugFrame(List.copyOf(list), list.get(4).snapshot().entityUuid());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NavigationDebugFrameCodec.write(frame, buffer);
            check(buffer.readableBytes()<128*1024, "worst standard frame stays below 128 KiB");
            check(frame.equals(NavigationDebugFrameCodec.read(buffer)), "eight full paths, slot colors, focus and NaN score round trip");
            check(buffer.readableBytes()==0, "decoder consumes exactly one frame");
        } finally { buffer.release(); }
        invalidCount(9); invalidCount(-1);
        rejects(new NavigationDebugFrame(List.of(list.get(0), list.get(0)), null), "duplicate slot");
        rejects(new NavigationDebugFrame(List.of(list.get(0), new NavigationDebugFrame.Entry(1, list.get(0).snapshot())), null), "duplicate UUID");
        rejects(new NavigationDebugFrame(List.of(new NavigationDebugFrame.Entry(0, sample(0,1,1,true))), null), "nonfinite world geometry");
        rejects(new NavigationDebugFrame(List.of(new NavigationDebugFrame.Entry(0, sample(0,257,1,false))), null), "oversized path");
        rejects(new NavigationDebugFrame(List.of(new NavigationDebugFrame.Entry(0, sample(0,1,49,false))), null), "oversized candidates");
        System.out.println("NavigationDebugTest: " + checks + " checks passed; long-running native/render gameplay still requires acceptance");
    }
    private static void invalidCount(int count) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(count);
            try { NavigationDebugFrameCodec.read(buffer); throw new AssertionError("invalid count accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
        } finally { buffer.release(); }
    }
    private static void rejects(NavigationDebugFrame frame, String message) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            try {
                NavigationDebugFrameCodec.write(frame, buffer); NavigationDebugFrameCodec.read(buffer);
                throw new AssertionError(message + " accepted");
            } catch (IllegalArgumentException expected) { checks++; }
        } finally { buffer.release(); }
    }
    private static NavigationDebugSnapshot sample(int slot, int nodes, int samples, boolean invalid) {
        var path = new ArrayList<Vec3>(); var candidates = new ArrayList<NavigationDebugSnapshot.Sample>();
        for (int i=0;i<nodes;i++) path.add(new Vec3(29_000_000 + i,64,-29_000_000));
        for (int i=0;i<samples;i++) candidates.add(new NavigationDebugSnapshot.Sample(new Vec3(i,64,i),"terrain","candidate",Double.NaN));
        return new NavigationDebugSnapshot(new ResourceLocation("minecraft","overworld"), slot,
                new UUID(0,slot), "人类"+slot,"test","complete","HumanNavigation","basic:1/1","SAFE",
                60,6,9,40,new Vec3(invalid ? Double.NaN : 29_000_000,64,-29_000_000),
                new BlockPos(29_000_100,64,-29_000_000),null,path.get(path.size()-1),List.copyOf(path),
                0,nodes,true,false,.25,List.copyOf(candidates));
    }
}
