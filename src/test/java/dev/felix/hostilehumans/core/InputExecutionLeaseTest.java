package dev.felix.hostilehumans.core;

import java.util.HashSet;
import java.util.Set;

/** Delayed-input isolation and cleanup checks; Java execution requires build authorization. */
public final class InputExecutionLeaseTest {
    private enum Channel { WANTED, STRAFE, HOLD, SHORE, SHORE_SEARCH }
    public static void main(String[] args) {
        var inputs = new InputExecutionLease<Channel,String>(Channel.class);
        Set<String> live = new HashSet<>(Set.of("old"));
        check(!inputs.request(Channel.STRAFE,null,true,live::contains), "anonymous input rejected");
        check(inputs.request(Channel.STRAFE,"old",true,live::contains), "original strafe admitted");
        check(inputs.request(Channel.WANTED,"old",true,live::contains), "same execution can own path input too");
        live.clear();live.add("new");
        check(!inputs.live(Channel.STRAFE,true,live::contains), "revoked strafe cannot execute");
        check(inputs.request(Channel.STRAFE,"new",true,live::contains), "replacement strafe admitted");
        check(!inputs.clear(Channel.STRAFE,"old",true,live::contains), "late stop cannot erase replacement strafe");
        check(!inputs.clear(Channel.STRAFE,null,true,live::contains), "unscoped cleanup cannot erase live strafe");
        check(inputs.clear(Channel.WANTED,"old",true,live::contains), "original cleanup may clear its expired input");
        check(inputs.live(Channel.STRAFE,true,live::contains), "clearing wanted input does not erase independent strafe");
        check(!inputs.clear(Channel.HOLD,null,true,live::contains), "anonymous empty-channel clear does not authorize a native write");
        check(inputs.clear(Channel.HOLD,"new",true,live::contains), "live admitted movement may clear empty channel");
        inputs.request(Channel.SHORE,"new",true,live::contains);
        inputs.request(Channel.SHORE_SEARCH,"new",true,live::contains);
        check(!inputs.clear(Channel.SHORE_SEARCH,"old",true,live::contains), "late search cancellation cannot clear replacement request");
        check(inputs.clear(Channel.SHORE_SEARCH,"new",true,live::contains) && inputs.live(Channel.SHORE,true,live::contains), "finished search does not release real shore movement");
        inputs.discard(Channel.STRAFE);check(!inputs.live(Channel.STRAFE,true,live::contains), "execution-side discard retires source");
        inputs.request(Channel.HOLD,"new",true,live::contains);inputs.cancel();
        check(inputs.source(Channel.HOLD)==null && inputs.source(Channel.STRAFE)==null, "root cancellation clears every channel");
        check(inputs.request(Channel.WANTED,null,false,live::contains) && inputs.clear(Channel.WANTED,null,false,live::contains), "unbound migration preserves native writes");
    }
    private static void check(boolean value,String reason) { if(!value)throw new AssertionError(reason); }
}
