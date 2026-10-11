package com.craftix.hostile_humans.client.renderer;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Client resource-pack profiles: no optional-mod classes or server configuration. */
final class BackShieldPose extends SimpleJsonResourceReloadListener {
    static final BackShieldPose INSTANCE = new BackShieldPose();
    private volatile List<Rule> rules = List.of();
    private static final Profile DEFAULT = new Profile(false, true, new double[]{0,0,0},
            new double[]{0,0,0}, new double[]{1,1,1});
    private BackShieldPose() { super(new Gson(), "back_shields"); }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        List<Rule> loaded = new ArrayList<>();
        resources.forEach((id, json) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                String item = string(obj, "item", ""), namespace = string(obj, "namespace", "");
                if (!item.isEmpty() && ResourceLocation.tryParse(item) == null)
                    throw new IllegalArgumentException("Invalid item ID");
                if (!namespace.isEmpty() && !namespace.matches("[a-z0-9_.-]+"))
                    throw new IllegalArgumentException("Invalid namespace");
                Boolean custom = obj.has("custom_renderer") ? obj.get("custom_renderer").getAsBoolean() : null;
                String mode = string(obj, "mode", "back"), context = string(obj, "context", "third_person");
                if (!Set.of("back", "offhand").contains(mode) || !Set.of("third_person", "none").contains(context))
                    throw new IllegalArgumentException("Unknown mode/context");
                Profile profile = new Profile(mode.equals("offhand"), context.equals("third_person"),
                        vector(obj, "translation", 0, false), vector(obj, "rotation", 0, false), vector(obj, "scale", 1, true));
                loaded.add(new Rule(id, item, namespace, custom, obj.has("priority") ? obj.get("priority").getAsInt() : 0, profile));
            } catch (RuntimeException ex) {
                LogUtils.getLogger().warn("Ignoring back-shield profile {}: {}", id, ex.getMessage());
            }
        });
        loaded.sort(Comparator.comparingInt(Rule::priority).reversed()
                .thenComparing(Comparator.comparingInt(Rule::specificity).reversed())
                .thenComparing(rule -> rule.id().toString()));
        rules = List.copyOf(loaded);
    }
    private static String string(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }
    private static double[] vector(JsonObject obj, String key, double fallback, boolean positive) {
        if (!obj.has(key)) return new double[]{fallback, fallback, fallback};
        JsonArray array = obj.getAsJsonArray(key);
        if (array.size() != 3) throw new IllegalArgumentException(key + " needs three numbers");
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            values[i] = array.get(i).getAsDouble();
            if (!Double.isFinite(values[i]) || (positive && values[i] <= 0))
                throw new IllegalArgumentException("Invalid " + key);
        }
        return values;
    }
    static Profile resolve(Human human, ItemStack shield) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(shield.getItem());
        boolean custom = Minecraft.getInstance().getItemRenderer()
                .getModel(shield, human.level(), human, human.getId()).isCustomRenderer();
        for (Rule rule : INSTANCE.rules) {
            if ((!rule.item().isEmpty() && !rule.item().equals(id.toString()))
                    || (!rule.namespace().isEmpty() && !rule.namespace().equals(id.getNamespace()))
                    || (rule.custom() != null && rule.custom() != custom)) continue;
            return rule.profile();
        }
        return DEFAULT;
    }
    private record Rule(ResourceLocation id, String item, String namespace, Boolean custom, int priority, Profile profile) {
        int specificity() { return (!item.isEmpty() ? 4 : !namespace.isEmpty() ? 2 : 0) + (custom != null ? 1 : 0); }
    }
    record Profile(boolean offhand, boolean thirdPerson, double[] translation, double[] rotation, double[] scale) {
        ItemDisplayContext apply(PoseStack poses, HumanoidArm arm) {
            poses.translate(translation[0], translation[1], translation[2]);
            poses.mulPose(Axis.XP.rotationDegrees((float) rotation[0]));
            poses.mulPose(Axis.YP.rotationDegrees((float) rotation[1]));
            poses.mulPose(Axis.ZP.rotationDegrees((float) rotation[2]));
            poses.scale((float) scale[0], (float) scale[1], (float) scale[2]);
            if (!thirdPerson) return ItemDisplayContext.NONE;
            // Vanilla holding frame relocated to the torso; native item transforms stay intact.
            poses.mulPose(Axis.XP.rotationDegrees(-90));
            poses.mulPose(Axis.YP.rotationDegrees(180));
            poses.translate((arm == HumanoidArm.LEFT ? -1.0 : 1.0) / 16, .125, -.625);
            return arm == HumanoidArm.LEFT ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
    }
}
