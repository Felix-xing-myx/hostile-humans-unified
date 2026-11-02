package dev.felix.hostilehumans.observer;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.observer.AttributeReference;
import dev.felix.hostilehumans.progression.GrowthConfig;
import dev.felix.hostilehumans.progression.HumanGrowth;
import dev.felix.hostilehumans.progression.SpecialtyConfig;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;

/** Both combat and inspection consume the same pure resolver; inspection never repairs the input. */
public final class AttributeDetailGrowthViewTest {
    public static void main(String[] args) {
        GrowthConfig config=new UnifiedConfig(new JsonObject()).growth();
        CompoundTag saved=new CompoundTag();saved.putInt("level",2);saved.putInt("experience_percent",105);
        saved.putDouble("stage_progress",17.5);CompoundTag before=saved.copy();
        var layers=HumanGrowth.layers(saved,config);
        check(layers.known(),"saved state known");
        close(layers.healthMultiplier(),config.stagePercent(2)/100*1.05,"health shared layers");
        close(layers.damageMultiplier(),config.damageStagePercent(2)/100*1.05,"damage shared layers");
        close(layers.gunDamage(),config.gunDamageMultiplier(105),"independent firearm experience");
        check(saved.equals(before),"valid saved state unchanged");
        saved.putInt("level",99);saved.putInt("experience_percent",999);before=saved.copy();
        var capped=HumanGrowth.layers(saved,config);
        close(capped.experience(),config.experienceCapPercent()/100,"view uses runtime cap");
        check(saved.equals(before),"view does not persist its clamp");
        check(!HumanGrowth.layers(null,config).known(),"absent state remains unknown");
        var specialty = SpecialtyConfig.defaults();
        close(AttributeReference.ordinaryBudget(specialty, "tier1"), 1.1, "tier1 reference follows current budget centre");
        close(AttributeReference.ordinaryBudget(specialty, "tier2"), 1.21, "tier2 reference includes rank factor");
        close(AttributeReference.ordinaryBudget(specialty, "tier3"), 1.32, "tier3 reference includes rank factor");
        double[] preferences = new double[13], costs = new double[13], caps = new double[13];
        java.util.Arrays.fill(preferences, 1); preferences[12] = .01;
        java.util.Arrays.fill(costs, 1); java.util.Arrays.fill(caps, .45); caps[7] = .3;
        double lastHealth = 0;
        for (String tier : java.util.List.of("tier1", "tier2", "tier3")) {
            double budget = AttributeReference.ordinaryBudget(specialty, tier);
            double[] centre = AttributeReference.allocate(budget, preferences, costs, caps, specialty, 1.8);
            double total = java.util.Arrays.stream(centre).sum();
            check(Math.abs(total - budget * 12) < 1e-8, "reference conserves current total budget for " + tier);
            check(centre[0] > lastHealth && centre[12] > 0, "higher tier reference rises and penetration is not compared to zero");
            for (int i = 0; i < 12; i++) check(centre[i] >= .5 && centre[i] <= 1.8, "reference respects absolute bounds");
            lastHealth = centre[0];
        }
        var beforePreferences = preferences.clone();
        var first = AttributeReference.allocate(1.848, preferences, costs, caps, specialty, 2.1);
        var second = AttributeReference.allocate(1.848, preferences, costs, caps, specialty, 2.1);
        check(java.util.Arrays.equals(first, second) && java.util.Arrays.equals(beforePreferences, preferences),
                "reference neither draws randomness nor changes faction inputs");
        check(Math.abs(java.util.Arrays.stream(first).sum() - 1.848 * 12) < 1e-8,
                "boss reference uses higher budget and separate bounds");
        System.out.println("AttributeDetailGrowthViewTest: all checks passed");
    }
    private static void close(double value,double expected,String message){check(Math.abs(value-expected)<1e-9,message);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
