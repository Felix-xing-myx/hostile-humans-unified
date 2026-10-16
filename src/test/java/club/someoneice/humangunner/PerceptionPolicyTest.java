package club.someoneice.humangunner;

import dev.felix.hostilehumans.core.PerceptionPolicy;
import dev.felix.hostilehumans.core.RecognitionEvidence;
import dev.felix.hostilehumans.core.BodyVisibilitySamples;
import dev.felix.hostilehumans.core.ScopedWorkBudget;
import dev.felix.hostilehumans.core.AttentionFacingPolicy;
import dev.felix.hostilehumans.core.NearVisionPolicy;
import dev.felix.hostilehumans.core.CachedOcclusionProof;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import com.google.gson.JsonObject;

/** Behavioral policies, configuration provenance and perception/beacon temporal boundary cases. */
public final class PerceptionPolicyTest {
    private static int checks;
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static final PerceptionConfig recognitionDefaults=new PerceptionConfig(new JsonObject());
    private static int recognition(double d,boolean alert,boolean known){
        return PerceptionPolicy.recognition(d,alert,known,
                recognitionDefaults.number("recognition.base_ticks"),recognitionDefaults.number("recognition.ticks_per_block"),
                recognitionDefaults.integer("recognition.maximum_ticks"),recognitionDefaults.integer("recognition.minimum_ticks"),
                recognitionDefaults.number("recognition.alert_factor"),recognitionDefaults.number("recognition.familiar_factor"),
                recognitionDefaults.number("recognition.combined_factor"));
    }
    private static void occlusionProofCases(){
        Vec3 eye=new Vec3(0,1.6,.5);
        var body=BodyVisibilitySamples.of(9.7,0,.2,10.3,1.8,.8).stream().map(p->new Vec3(p.x(),p.y(),p.z())).toList();
        var wall=List.of(new AABB(4,0,0,5,1,1),new AABB(4,1,0,5,2,1));
        check(CachedOcclusionProof.covered(eye,body,wall),"real full wall proves every current body sample blocked");
        var withFastPoint=new java.util.ArrayList<>(body);withFastPoint.add(new Vec3(10,1.6,3));
        check(!CachedOcclusionProof.covered(eye,withFastPoint,wall),"extra fast observation point must be proven, not just the 27 body points");
        var exposed=body.stream().map(p->p.add(0,0,2)).toList();
        check(!CachedOcclusionProof.covered(eye,exposed,wall),"walking beyond wall invalidates proof immediately");
        check(!CachedOcclusionProof.covered(eye,body,List.of(wall.get(0))),"missing upper blocker exposes body samples");
        check(!CachedOcclusionProof.covered(eye,body,List.of(new AABB(11,0,0,12,2,1))),"block behind target cannot prove invisibility");
        Vec3 lowEye=new Vec3(0,.5,.5);
        var cube=List.of(new AABB(4,0,0,5,1,1));
        check(CachedOcclusionProof.covered(lowEye,List.of(new Vec3(10,.5,1.749)),cube),"just inside a wall corner remains occluded");
        check(!CachedOcclusionProof.covered(lowEye,List.of(new Vec3(10,.5,1.751)),cube),"two-thousandths exposure invalidates proof without movement threshold");
        check(!CachedOcclusionProof.covered(lowEye,List.of(new Vec3(2,0,.5)),List.of(new AABB(1,-1,0,2,0,1))),
                "touching floor endpoint does not falsely hide a visible foot");
        check(!CachedOcclusionProof.covered(lowEye,List.of(),cube)
                &&!CachedOcclusionProof.covered(lowEye,List.of(new Vec3(Double.NaN,0,0)),cube),"empty or invalid samples fail open to real LOS");
        check(CachedOcclusionProof.covers(7,3)&&!CachedOcclusionProof.covers(3,7)
                &&!CachedOcclusionProof.covers(7,0),"turning within old coverage retains cache; newly exposed FOV bits do not");
    }
    public static void main(String[] args){
        occlusionProofCases();
        for(int batch:new int[]{9,5,3}){
            var reduced=new dev.felix.hostilehumans.core.NegativeObservationSweep();
            for(int offset=0;offset<27;offset+=batch){
                boolean complete=reduced.negative(27,offset,Math.min(batch,27-offset),(1L<<27)-1);
                check(complete==(offset+batch>=27),"reduced batch "+batch+" must cover every valid point before declaring occlusion");
            }
        }

        check(!PerceptionPolicy.defensiveCreature(0,-1,true,600,2400),"ordinary marked animal cannot invent defense provenance");
        check(PerceptionPolicy.defensiveCreature(2399,2400,false,-1,-1),"unconfirmed actual attacker remains eligible within bounded defense history");
        check(!PerceptionPolicy.defensiveCreature(2400,2400,false,-1,-1),"unconfirmed animal defense expires exactly at deadline");
        check(PerceptionPolicy.defensiveCreature(2400,2400,true,2500,3000),"confirmed attacker keeps its existing lawful memory after initial defense lease");
        check(!PerceptionPolicy.defensiveCreature(3000,2400,true,2500,3000),"all animal defense and familiar memory expires without a permanent blacklist");
        check(PerceptionPolicy.defensiveCreature(3000,5400,false,-1,-1),"new actual defense event can reopen recognition without marking target");
        check(new PerceptionConfig(new JsonObject()).integer("memory.dynamic_ticks")==600,"default identified position lease is thirty seconds");
        check(new PerceptionConfig(new JsonObject()).integer("recognition.break_ticks")==40,"brief lost view allows two seconds before resetting evidence");
        check(!dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(19,0,20,false,false),"ordinary proposal cannot steal minimum hold");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(20,0,20,false,false),"hold releases exactly at deadline");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(1,0,20,false,true),"validated crowd/pressure proposal can interrupt hold");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(1,0,20,true,false),"close emergency can interrupt distant target");
        check(!dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.switchAllowed(0,100,20,false,false),"clock rollback cannot falsely satisfy a hold");
        int[] turns=new int[2];
        for(int round=0;round<100;round++)turns[dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.nextIndex(round,2)]++;
        check(turns[0]==50&&turns[1]==50,"independent recognition cursor serves both targets with one slot");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.evidenceInterval(4,2,1,true)==16,"one-ray main refresh plus two recognizers has a bounded four-round cadence");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.evidenceInterval(4,2,2,true)==8,"two rays maintain known target and both recognizers");
        check(dev.felix.hostilehumans.core.PerceptionCoordinationPolicy.evidenceInterval(4,2,2,false)==4,"two rays recognize both targets in one round when no main refresh is needed");
        RecognitionEvidence paused=new RecognitionEvidence();paused.visible(0,4,20,40);double preserved=paused.progress();paused.pause();
        check(!paused.active()&&paused.hasEvidence(),"hit response can free one slot without deleting old evidence");
        paused.resume(200);check(paused.active()&&paused.progress()==preserved,"resuming an unsampled target does not invent negative observations");
        paused.visible(200,4,20,40);check(Math.abs(paused.progress()-preserved-.05)<1.e-8,"pause does not grant long unseen evidence");
        paused.miss(201);paused.pause();check(paused.interrupted(241,40),"real sustained negative remains effective while a slot is paused");
        var travelFacing=AttentionFacingPolicy.step(0,0,0,180,0,60,false);
        check(travelFacing.body()==0&&Math.abs(travelFacing.head())<=60,"ordinary moving observation cannot rotate the body or exceed sixty-degree head limit");
        // Sparse grants must eventually identify a visible target beyond the LOOK lease.
        RecognitionEvidence sparse=new RecognitionEvidence();sparse.begin(0);
        boolean identified=false;
        for(int t=0;t<800;t+=8)if(sparse.visible(t,4,90,10)){identified=true;break;}
        check(identified,"ninety actual sparse observations survive a 120-tick look lease");
        var sweep=new dev.felix.hostilehumans.core.NegativeObservationSweep();
        for(int i=0;i<26;i++)check(!sweep.negative(27,i,1),"partial negative body scan is not yet complete");
        check(sweep.negative(27,26,1),"one-ray grants eventually complete a negative body sweep");
        check(!sweep.negative(27,0,1),"completed sweep starts a new cycle");
        sweep.reset();check(!sweep.negative(27,26,1),"a positive observation clears old negative coverage");
        sweep.reset();check(!sweep.negative(2,0,1,3),"partial coverage in first view subset");
        check(!sweep.negative(2,1,1,6),"equal-size changed view subset cannot reuse prior coverage");
        check(sweep.negative(2,0,1,6),"stable subset eventually completes");
        var nearest=new dev.felix.hostilehumans.core.BoundedSelection<Integer>(8,java.util.Comparator.naturalOrder());
        for(int i=100;i>=0;i--)nearest.offer(i);
        check(nearest.values().equals(java.util.List.of(0,1,2,3,4,5,6,7)),"late spatial results replace farther candidates");
        ScopedWorkBudget<String,String> lanes=new ScopedWorkBudget<>(2,2,1,40);
        check(lanes.claimFull(0,"npc:events","world",1)==1,"event query granted");lanes.cancel("npc:events");
        check(lanes.claimFull(0,"npc:candidates","world",1)==1,"candidate query uses independent work key");lanes.cancel("npc:candidates");
        check(lanes.claimFull(0,"npc:target-cap","world",1)==0,"lanes still share the hard global cap");lanes.cancel("npc:target-cap");
        check(lanes.claimFull(1,"other:candidates","world",1)==1,"served one-shot queries leave no ghost reservation");
        check(PerceptionPolicy.live(259,260)&&!PerceptionPolicy.live(260,260),"expiry tick is already expired");
        check(PerceptionPolicy.hitDeadline(true,259,260,160)==419,"live hit resets to eight seconds");
        check(PerceptionPolicy.hitDeadline(true,260,260,160)==260,"expired hit cannot resurrect location");
        check(PerceptionPolicy.hitDeadline(false,200,260,160)==260,"unmarked identity never grants a lease");
        long first=PerceptionPolicy.hitDeadline(true,200,260,160);
        check(PerceptionPolicy.hitDeadline(true,201,first,160)==361,"repeated hits reset rather than add duration");
        check(recognition(4,false,false)==8&&recognition(16,false,false)==15&&recognition(64,false,false)==45,"distance baseline");
        check(recognition(4,true,true)==3&&recognition(16,true,true)==6&&recognition(64,true,true)==18,"moderate combined recognition");
        check(recognition(16,true,false)==8&&recognition(16,false,true)==8,"either single acceleration");
        check(recognition(128,true,false)==23,"far range capped before factor");
        double nearHalf=Math.toRadians(80);
        check(NearVisionPolicy.owns(0,-.8,0,8,-1,2.8)
                &&NearVisionPolicy.inSector(0,0,0,1,nearHalf),"directly below feet has no pitch-cone blind spot");
        check(NearVisionPolicy.owns(.2,0,.2,8,-1,2.8)
                &&NearVisionPolicy.inSector(.2,.2,0,1,nearHalf),"small creature near feet is inside near volume");
        check(NearVisionPolicy.owns(0,2.8,1,8,-1,2.8)
                &&NearVisionPolicy.inSector(0,1,0,1,nearHalf),"top extension is inclusive and independent of eye pitch");
        check(!NearVisionPolicy.inSector(0,-1,0,1,nearHalf),"near field does not become rear omniscience");
        check(!NearVisionPolicy.inSector(1,0,0,1,nearHalf),"ninety-degree side lies outside stranger 160-degree field");
        check(NearVisionPolicy.inSector(1,0,0,1,Math.PI/2),"familiar 180-degree field includes side boundary");
        check(NearVisionPolicy.owns(0,0,8,8,-1,2.8)&&!NearVisionPolicy.owns(0,0,8.001,8,-1,2.8),"horizontal cylinder radius uses xz rather than eye distance");
        check(!NearVisionPolicy.owns(0,-1.001,0,8,-1,2.8)
                &&!NearVisionPolicy.owns(0,2.801,0,8,-1,2.8),"points outside the height slab retain ordinary vision handling");
        check(NearVisionPolicy.inSector(1,0,1,0,nearHalf)
                &&!NearVisionPolicy.inSector(-1,0,1,0,nearHalf),"head yaw rotates near sector without changing its height");
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
        var facing=new AttentionFacingPolicy.Facing(0,0,0);
        for(int tick=1;tick<=6;tick++){
            float oldBody=facing.body();
            facing=AttentionFacingPolicy.step(facing.body(),facing.head(),facing.pitch(),180,0,75,true);
            check(Math.abs(Math.IEEEremainder(facing.body()-oldBody,360))<=30.001,"body turns smoothly without snapping");
            check(Math.abs(Math.IEEEremainder(facing.head()-facing.body(),360))<=75.001,"head remains inside anatomical body limit");
        }
        check(Math.abs(facing.body())==180&&Math.abs(facing.head())==180,"stationary rear attacker produces a complete turn in six steps");
        var crossing=AttentionFacingPolicy.step(170,170,0,-170,60,75,true);
        check(crossing.body()==-170&&crossing.head()==-170&&crossing.pitch()==35,"wrap boundary uses shortest turn and bounded pitch");
        var retreatFacing=AttentionFacingPolicy.step(0,0,0,180,0,75,false);
        check(retreatFacing.body()==0,"retreat retains movement-facing body yaw");
        var body=BodyVisibilitySamples.of(-.3,0,-.3,.3,1.8,.3);
        check(body.size()==BodyVisibilitySamples.MAX_POINTS,"normal body has 27 unique surface/interior samples");
        check(body.get(0).equals(new BodyVisibilitySamples.Point(0,.9,0)),"torso is first fallback after eye/previous visible point");
        for(var p:body)check(p.x()>=-.3&&p.x()<=.3&&p.y()>=0&&p.y()<=1.8&&p.z()>=-.3&&p.z()<=.3,"body points remain within logical bounds");
        check(body.contains(new BodyVisibilitySamples.Point(0,1.8,0)),"head exposure sampled");
        check(body.contains(new BodyVisibilitySamples.Point(0,0,0)),"feet exposure sampled");
        check(body.contains(new BodyVisibilitySamples.Point(.3,.9,0)),"lateral body exposure sampled");
        check(body.contains(new BodyVisibilitySamples.Point(.3,1.8,.3)),"upper body corner exposure sampled");
        check(body.contains(new BodyVisibilitySamples.Point(-.3,0,-.3)),"lower body corner exposure sampled");
        check(body.stream().anyMatch(p->p.y()==0),"low opening can expose body with eye blocked");
        check(body.stream().anyMatch(p->p.x()==.3),"edge of view can admit a lateral sample when center is outside");
        check(BodyVisibilitySamples.of(1,1,1,1,1,1).size()==1,"degenerate box has no duplicate rays");
        check(BodyVisibilitySamples.of(1,0,0,-1,1,1).isEmpty(),"invalid bounds rejected");
        check(BodyVisibilitySamples.of(Double.NaN,0,0,1,1,1).isEmpty(),"nonfinite bounds rejected");
        var bodyBudget=new ScopedWorkBudget<String,String>(128,128,32,40);
        check(bodyBudget.claimFull(0,"fast","level",2)==2,"two ordinary fast probes still fit");
        check(bodyBudget.claimFull(0,"body-A","level",27)==27&&bodyBudget.claimFull(0,"body-B","level",27)==27,
                "two body batches fit the unchanged shared pool");
        bodyBudget.cancel("body-A");bodyBudget.cancel("body-B");
        check(bodyBudget.claimFull(0,"body-A","level",27)==0,"body cancellation never refunds same-tick capacity");
        bodyBudget.cancel("body-A");
        check(bodyBudget.claimFull(1,"other-A","level",32)==32
                &&bodyBudget.claimFull(1,"other-B","level",32)==32
                &&bodyBudget.claimFull(1,"other-C","level",32)==32,
                "completed fallback batches do not pre-reserve unused rays next tick");
        var tinyBodyBudget=new ScopedWorkBudget<String,String>(1,1,1,40);
        check(tinyBodyBudget.claimFull(0,"fast","level",1)==1
                &&tinyBodyBudget.claimFull(0,"body","level",1)==0,"tiny pool defers body after fast ray");
        check(tinyBodyBudget.claimFull(1,"fast","level",1)==0
                &&tinyBodyBudget.claimFull(1,"body","level",1)==1,"body can resume independently when it owns tiny pool grant");
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
