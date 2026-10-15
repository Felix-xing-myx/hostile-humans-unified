package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.PerceptionPolicy;
import dev.felix.hostilehumans.core.RecognitionEvidence;
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
            double angle=PerceptionPolicy.halfAngle(d,false,1.2,90);
            check(Double.isFinite(angle)&&angle<=previous,"farther cone narrows");previous=angle;
            double known=PerceptionPolicy.halfAngle(d,true,1.2,90);
            check(known>=angle&&known<=Math.toRadians(90),"bounded familiar cone never narrows base vision");
            if(d>1)check(recognition(d,false,false)>=recognition(d-1,false,false),"distance recognition monotonic");
        }
        check(Double.isFinite(PerceptionPolicy.halfAngle(0,false,2,60)),"coincident eyes finite");
        check(Math.abs(Math.toDegrees(PerceptionPolicy.halfAngle(8,false,1.2,90))*2-160)<1.e-8,"near full angle 160 degrees");
        check(Math.abs(Math.toDegrees(PerceptionPolicy.halfAngle(32,false,1.2,90))*2-120)<1.e-8,"middle full angle 120 degrees");
        check(Math.abs(Math.toDegrees(PerceptionPolicy.halfAngle(64,false,1.2,90))*2-90)<1.e-8,"far full angle 90 degrees");
        check(Math.abs(Math.toDegrees(PerceptionPolicy.halfAngle(48,false,1.2,90))*2-105)<1.e-8,"smooth intermediate field of view");
        check(PerceptionPolicy.halfAngle(4,true,2,60)>=PerceptionPolicy.halfAngle(4,false,2,60),"old cap cannot narrow newly enlarged field");
        check(Double.isFinite(PerceptionPolicy.halfAngle(33,false,1.2,90,32,8,4,160,120,90)),"misordered configurable anchors remain finite");
        RecognitionEvidence firstEvidence=new RecognitionEvidence(),secondEvidence=new RecognitionEvidence();
        firstEvidence.begin(0);secondEvidence.begin(0);
        for(int tick=0;tick<=90;tick+=10){
            check(!firstEvidence.interrupted(tick,10),"ten-tick sampling is not ten-tick occlusion");
            firstEvidence.visible(tick,10,90,10);secondEvidence.visible(tick,10,90,10);
            check(firstEvidence.progress()==secondEvidence.progress(),"two targets independently advance in one sample round");
        }
        check(firstEvidence.progress()==1&&secondEvidence.progress()==1,"both far targets can finish");
        RecognitionEvidence gap=new RecognitionEvidence();gap.visible(0,4,90,10);
        check(!gap.interrupted(100,10),"budget silence is never a negative observation");
        double before=gap.progress();gap.visible(100,4,90,10);
        check(Math.abs(gap.progress()-before-1.0/90)<1.e-8,"budget gap adds only actual sample credit");
        gap.miss(104);check(!gap.interrupted(113,10)&&gap.interrupted(114,10),"confirmed loss requires ten ticks after actual negative sample");
        gap.visible(114,4,90,10);check(Math.abs(gap.progress()-1.0/90)<1.e-8,"reacquire after sustained loss starts from zero");
        gap.miss(115);before=gap.progress();gap.visible(118,4,90,10);
        check(Math.abs(gap.progress()-before-1.0/90)<1.e-8,"brief occlusion retains evidence without crediting the occluded period");
        firstEvidence.finish();check(!firstEvidence.active()&&secondEvidence.active(),"one completion does not release another target's slot");
        firstEvidence.begin(200);check(firstEvidence.progress()==0,"a new recognition cannot reuse completed evidence");
        check(RecognitionEvidence.MAX_CONCURRENT==2,"two visual recognition slots, independent of head focus");
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
