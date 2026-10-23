package club.someoneice.humangunner;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;

/** Central reload contract: prepare is isolated, invalid data throws, publication is explicit. */
public final class SpecialtyDataTest {
    public static void main(String[] args) {
        var previous = SpecialtyData.get();
        check(SpecialtyData.parse(Map.of()).caps(.45)[SpecialtyAttributes.Dimension.SPEED.ordinal()] == .2,
                "missing dimension resources retain the explicit speed cap fallback");
        ResourceLocation dimensionsId = new ResourceLocation("hostile_humans_unified", "dimensions");
        ResourceLocation labelsId = new ResourceLocation("hostile_humans_unified", "labels");
        Map<ResourceLocation, JsonElement> files = new HashMap<>();
        JsonObject dimensions = new JsonObject();
        for (var dimension : SpecialtyAttributes.Dimension.values()) {
            JsonObject entry = new JsonObject(); entry.addProperty("cost", 1);
            dimensions.add(dimension.key(), entry);
        }
        files.put(dimensionsId, dimensions);
        dimensions.getAsJsonObject("speed").addProperty("deviation_cap", .2);
        JsonObject labels = new JsonObject(), condition = new JsonObject();
        condition.addProperty("accuracy", 1.25); labels.add("precise", condition); files.put(labelsId, labels);
        var candidate = SpecialtyData.parse(files);
        check(SpecialtyData.get() == previous, "prepare never publishes");
        double[] costs = candidate.costs(); costs[0] = 999;
        double[] caps = candidate.caps(.3); caps[0] = 999;
        check(candidate.costs()[0] == 1 && candidate.caps(.3)[0] == .3, "array getters are defensive");
        dimensions.getAsJsonObject("health").addProperty("cost", 999);
        condition.addProperty("accuracy", 999);
        check(candidate.costs()[0] == 1 && candidate.labels().get(0).thresholds().get(SpecialtyAttributes.Dimension.ACCURACY) == 1.25,
                "input JSON cannot mutate candidate");
        try { candidate.labels().clear(); throw new AssertionError("mutable labels"); } catch (UnsupportedOperationException expected) { }
        try { candidate.labels().get(0).thresholds().clear(); throw new AssertionError("mutable thresholds"); } catch (UnsupportedOperationException expected) { }
        dimensions.getAsJsonObject("health").addProperty("cost", 0);
        try { SpecialtyData.parse(files); throw new AssertionError("invalid cost accepted"); } catch (IllegalArgumentException expected) { }
        check(SpecialtyData.get() == previous, "invalid preparation retains published revision");
        SpecialtyData.publish(candidate);
        check(SpecialtyData.get() == candidate, "explicit central publish");
        SpecialtyData.publish(previous);
        System.out.println("SpecialtyDataTest: all checks passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
