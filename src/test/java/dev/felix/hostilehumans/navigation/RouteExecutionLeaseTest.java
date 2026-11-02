package dev.felix.hostilehumans.navigation;

import java.util.HashSet;
import java.util.Set;

/** Pure ownership/lifecycle checks; not executed until Java compilation is authorized. */
public final class RouteExecutionLeaseTest {
    public static void main(String[] args) {
        var state = new RouteExecutionLease<Object, String>();
        Object oldPath = new Object(), newPath = new Object(), repaired = new Object();
        Object[] current = {null}; Set<String> live = new HashSet<>(Set.of("old"));
        int[] calls = {0};
        check(!state.install(null, true, live::contains, () -> current[0], () -> { calls[0]++; return true; }, () -> current[0]=null), "missing source rejects before callback");
        check(calls[0] == 0, "rejected source has no native side effects");
        check(state.install("old", true, live::contains, () -> current[0], () -> {current[0]=oldPath;return true;}, () -> current[0]=null), "original installs");
        check(state.owns(oldPath,live::contains) && !state.owns(newPath,live::contains), "ownership follows exact installed reference");
        check(!state.mayStop(oldPath,null,true,live::contains), "unscoped callback cannot clear live route");
        check(state.mayStop(oldPath,"old",true,live::contains), "original cleanup can clear its route");
        live.remove("old"); live.add("new");
        check(state.install("new",true,live::contains,()->current[0],()->{current[0]=newPath;return true;},()->current[0]=null), "new execution installs");
        check(!state.mayStop(newPath,"old",true,live::contains), "late old cleanup cannot clear replacement");
        state.recomputed(newPath,repaired,"old",live::contains);
        check(state.owns(newPath,live::contains), "stale recompute cannot transfer ownership");
        current[0]=repaired;state.recomputed(newPath,repaired,"new",live::contains);
        check(state.owns(repaired,live::contains), "native direct-field recompute preserves original source");
        check(!state.install("new",true,live::contains,()->current[0],()->false,()->current[0]=null), "failed isolated install rejected");
        check(state.owns(repaired,live::contains), "failed isolated trial preserves live route");
        live.remove("new");check(!state.owns(repaired,live::contains), "revoked source cannot keep moving");
        check(state.mayStop(repaired,null,true,live::contains), "expired route can be discarded");
        live.add("old");live.add("new");
        check(!state.install("old",true,live::contains,()->current[0],()->{
            state.install("new",true,live::contains,()->current[0],()->{current[0]=newPath;return true;},()->current[0]=null);
            return true;
        },()->current[0]=null), "outer callback cannot overwrite a nested replacement stamp");
        check(state.owns(newPath,live::contains) && "new".equals(state.owner()), "nested replacement retains owner");
        check(!state.install("old",true,live::contains,()->current[0],()->{current[0]=oldPath;live.remove("old");return true;},()->current[0]=null), "revocation inside native callback rejects commit");
        check(current[0]==null && state.owner()==null, "rejected own result is discarded");
        state.stopped();check(!state.owns(newPath,live::contains), "stop releases original stamp");
        check(state.install(null,false,live::contains,()->current[0],()->{current[0]=oldPath;return true;},()->current[0]=null), "unbound migration retains native behavior");
        check(state.mayStop(oldPath,null,false,live::contains), "unbound migration permits native stop");
    }
    private static void check(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
}
