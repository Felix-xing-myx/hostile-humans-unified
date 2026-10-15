package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.PerceptionPolicy;
import com.google.gson.JsonObject;

/** Behavioral policies, configuration provenance and perception/beacon temporal boundary cases. */
public final class PerceptionPolicyTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static int recognition(double d,boolean alert,boolean known){return PerceptionPolicy.recognition(d,alert,known,10,1.25,90,4,.5,.5,.4);}
    public static void main(String[] args){
        check(PerceptionPolicy.live(259,260)&&!PerceptionPolicy.live(260,260),"expiry tick is already expired");
        check(PerceptionPolicy.hitDeadline(true,259,260,160)==419,"live hit resets to eight seconds");
        check(PerceptionPolicy.hitDeadline(true,260,260,160)==260,"expired hit cannot resurrect location");
        check(PerceptionPolicy.hitDeadline(false,200,260,160)==260,"unmarked identity never grants a lease");
        long first=PerceptionPolicy.hitDeadline(true,200,260,160);
        check(PerceptionPolicy.hitDeadline(true,201,first,160)==361,"repeated hits reset rather than add duration");
        check(recognition(4,false,false)==15&&recognition(16,false,false)==30&&recognition(64,false,false)==90,"distance baseline");
        check(recognition(4,true,true)==6&&recognition(16,true,true)==12&&recognition(64,true,true)==36,"moderate combined recognition");
        check(recognition(16,true,false)==15&&recognition(16,false,true)==15,"either single acceleration");
        check(recognition(128,true,false)==45,"far range capped before factor");
        check(PerceptionPolicy.recognition(16,true,true,10,1.25,90,4,.5,.5,.01)>=11,"configuration cannot multiply into extreme speed");
        double previous=Double.MAX_VALUE;
        for(int d=1;d<=256;d++){
            double angle=PerceptionPolicy.halfAngle(d,false,2,60);
            check(Double.isFinite(angle)&&angle<=previous,"farther cone narrows");previous=angle;
            double known=PerceptionPolicy.halfAngle(d,true,2,60);
            check(known>=Math.min(angle,Math.toRadians(60))&&known<=Math.toRadians(60),"bounded familiar cone");
            if(d>1)check(recognition(d,false,false)>=recognition(d-1,false,false),"distance recognition monotonic");
        }
        check(Double.isFinite(PerceptionPolicy.halfAngle(0,false,2,60)),"coincident eyes finite");
        JsonObject configured=new JsonObject(),memory=new JsonObject();memory.addProperty("dynamic_ticks",321);configured.add("memory",memory);
        PerceptionConfig c=new PerceptionConfig(configured);
        check(c.integer("memory.dynamic_ticks")==321,"custom setting preserved");
        check(c.integer("beacon.duration_ticks")==6000,"five minute shared default");
        memory.addProperty("dynamic_ticks",999999);check(c.integer("memory.dynamic_ticks")==321,"immutable config snapshot");
        JsonObject defaults=UnifiedConfig.defaults().getAsJsonObject("perception");
        for(var entry:PerceptionConfig.SETTINGS.entrySet()){
            String[] path=entry.getKey().split("\\.");
            check(defaults.getAsJsonObject(path[0]).get(path[1]).getAsDouble()==entry.getValue().fallback(),"shipped catalog matches registry "+entry.getKey());
        }
        BeaconReportPolicyTest.main(args);
        ScopedWorkBudgetTest.main(args);
        System.out.println("PerceptionPolicyTest: "+checks+" checks passed");
    }
}
