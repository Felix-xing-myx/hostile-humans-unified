package club.someoneice.humangunner;

import com.google.gson.JsonObject;

/** Server restart-scoped knobs for population-aware work density. */
public record ActivityBudgetConfig(boolean enabled,int sampleTicks,int staleTicks,int downgradeTicks,
        int sparseHumans,int crowdedHumans,int battleHumans,double sparseInterval,double crowdedInterval,double battleInterval,
        int sparseProbes,int crowdedBodyRays,int battleBodyRays,int normalSources,int crowdedSources,int battleSources,
        double crowdedNavigation,double battleNavigation) {
    static ActivityBudgetConfig parse(JsonObject data) {
        int sample=i(data,"sample_ticks",20,1,100);
        int sparse=i(data,"sparse_humans",16,1,128);
        int crowded=Math.max(sparse+1,i(data,"crowded_humans",64,2,512));
        return new ActivityBudgetConfig(UnifiedConfig.bool(data,"enabled",true),sample,
                Math.max(sample*2,i(data,"stale_ticks",60,2,600)),i(data,"downgrade_ticks",100,20,1200),
                sparse,crowded,Math.max(crowded+1,i(data,"battle_humans",128,3,1024)),
                n(data,"sparse_interval_factor",.5,.25,1),n(data,"crowded_interval_factor",2,1,4),n(data,"battle_interval_factor",3,1,8),
                i(data,"sparse_probe_multiplier",2,1,4),i(data,"crowded_body_rays",9,1,27),i(data,"battle_body_rays",5,1,27),
                i(data,"normal_source_samples",16,1,64),i(data,"crowded_source_samples",8,1,64),i(data,"battle_source_samples",4,1,64),
                n(data,"crowded_navigation_factor",.5,.1,1),n(data,"battle_navigation_factor",.25,.1,1));
    }
    private static int i(JsonObject d,String k,int fallback,int min,int max){return (int)n(d,k,fallback,min,max);}
    private static double n(JsonObject d,String k,double fallback,double min,double max){return UnifiedConfig.number(d,k,fallback,min,max);}
}
