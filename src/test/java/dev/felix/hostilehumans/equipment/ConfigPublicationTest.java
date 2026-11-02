package dev.felix.hostilehumans.equipment;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.equipment.FactionEquipmentConfig;
import dev.felix.hostilehumans.progression.SpecialtyConfig;
import dev.felix.hostilehumans.soldier.OrderDistances;

import com.google.gson.JsonParser;

/** Invalid construction must not publish one module before a later module rejects its input. */
public final class ConfigPublicationTest {
    public static void main(String[] args) {
        var specialty = SpecialtyConfig.get();
        long revision = FactionEquipmentConfig.revision();
        var orders = OrderDistances.defaults();
        boolean rejected = false;
        try {
            new UnifiedConfig(JsonParser.parseString("""
                    {"specialty":{"enabled":false},"orders":{"follow_distance":96},
                     "equipment_loadouts":{"tier1":{"gun_chance":2}}}
                    """).getAsJsonObject());
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected,"invalid equipment rejected");
        check(SpecialtyConfig.get()==specialty,"failed equipment does not publish specialty");
        check(FactionEquipmentConfig.revision()==revision,"failed preparation keeps equipment revision");
        check(OrderDistances.defaults()==orders,"failed preparation keeps geometry defaults");
        rejected=false;
        try { new UnifiedConfig(JsonParser.parseString("{\"specialty\":{\"budget_sigma\":-1}}").getAsJsonObject()); }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected&&SpecialtyConfig.get()==specialty,"invalid specialty fails without publication");
        System.out.println("Config publication checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
