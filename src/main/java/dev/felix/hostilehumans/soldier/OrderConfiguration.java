package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.config.UnifiedConfig;

import com.google.gson.JsonObject;

/** Five-file configuration adapter. Geometry and membership consumers never parse settings. */
public final class OrderConfiguration {
    private OrderConfiguration() {}
    public static void apply(JsonObject root) {
        JsonObject o = UnifiedConfig.object(root, "orders"), s = UnifiedConfig.object(root, "squads");
        OrderDistances.configure(new OrderDistances.Defaults(
                integer(o,"follow_distance",48,8,96), integer(o,"garrison_radius",32,8,96),
                number(o,"garrison_return_distance",.8,.3,3), number(o,"garrison_leave_threshold",4,2,16),
                number(o,"garrison_walk_speed",.65,.3,1), integer(o,"chase_extra_radius",16,0,32),
                integer(o,"chase_max_radius",96,8,96), integer(o,"chase_duration_ticks",400,40,1200),
                integer(o,"chase_return_cooldown_ticks",80,0,400), number(o,"route_vertical_tolerance",.75,.1,1),
                integer(o,"route_failure_backoff_ticks",100,20,1200), number(o,"route_reach_distance",1.5,.5,4),
                integer(o,"route_timeout",200,20,1200), integer(o,"route_max_points",64,2,256),
                integer(o,"route_wait_default",0,0,1200)));
        SquadData.configureLimits(new SquadData.Limits(integer(s,"max_squads",8,1,16),
                integer(s,"max_members",32,1,64), integer(s,"name_length",16,4,32)));
    }
    private static int integer(JsonObject o,String key,int fallback,int min,int max) {
        return (int) number(o,key,fallback,min,max);
    }
    private static double number(JsonObject o,String key,double fallback,double min,double max) {
        return UnifiedConfig.number(o,key,fallback,min,max);
    }
}
