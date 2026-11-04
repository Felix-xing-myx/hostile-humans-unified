package dev.felix.hostilehumans.settlement;

import net.minecraftforge.common.ForgeConfigSpec;

/** Author/query input limits, independent of navigation budgets. */
public final class SettlementLimits {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue REGIONS,
            REGION_BOXES,
            TEMPLATE_BOXES,
            EXTENSION,
            EXTENT,
            INSTANCE_BOXES,
            MODULES,
            INSTANCES,
            QUERY_RESULTS,
            TEMPLATE_VOLUME;

    static {
        var b = new ForgeConfigSpec.Builder();
        REGIONS = b.defineInRange("regions_per_template", 16, 1, 64);
        REGION_BOXES = b.defineInRange("boxes_per_region", 64, 1, 64);
        TEMPLATE_BOXES = b.defineInRange("boxes_per_template", 256, 1, 1024);
        EXTENSION = b.defineInRange("template_extension", 128, 0, 512);
        EXTENT = b.defineInRange("template_region_axis_extent", 512, 1, 1024);
        INSTANCE_BOXES = b.defineInRange("boxes_per_instance", 1024, 1, 8192);
        MODULES = b.defineInRange("modules_per_instance", 128, 1, 256);
        INSTANCES = b.defineInRange("instances_per_dimension", 8192, 1, 65536);
        QUERY_RESULTS = b.defineInRange("overlapping_query_results", 64, 1, 256);
        TEMPLATE_VOLUME = b.defineInRange("template_volume", 1_048_576, 1, 4_194_304);
        SPEC = b.build();
    }

    public static int value(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private SettlementLimits() {}
}
