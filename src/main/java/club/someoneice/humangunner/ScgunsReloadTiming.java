package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.common.ReloadType;
import top.ribs.scguns.item.GunItem;
import top.ribs.scguns.util.GunEnchantmentHelper;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public timing helpers plus a confined read-only animation resource boundary for animated guns. */
final class ScgunsReloadTiming {
    private static final Map<ResourceLocation, Optional<JsonObject>> ANIMATIONS = new HashMap<>();
    private ScgunsReloadTiming() { }

    static List<ScgunsReloadPlan.Step> plan(Human human, ItemStack stack, Gun gun, int missing, boolean energy) {
        var attribute = human.getAttribute(top.ribs.scguns.attributes.SCAttributes.RELOAD_SPEED.get());
        double speed = attribute == null ? 1 : Math.max(.01, attribute.getValue());
        boolean single = gun.getReloads().getReloadType() == ReloadType.SINGLE_ITEM;
        int adjusted = single ? GunEnchantmentHelper.getMagReloadSpeed(stack)
                : GunEnchantmentHelper.getRealReloadSpeed(stack);
        int ticks = Math.max(1, (int) Math.ceil(adjusted / speed));
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        JsonObject animations = ANIMATIONS.computeIfAbsent(id, ScgunsReloadTiming::read).orElse(null);
        GunItem item = (GunItem) stack.getItem();
        String prefix = item.isOneHandedCarbineCandidate(stack) && (Gun.hasExtendedBarrel(stack) || Gun.hasStock(stack))
                && animations != null && (animations.has("carbine_reload")
                || animations.has("carbine_reload_loop")) ? "carbine_" : "";
        // Energy uses one paid full recharge. Never create one loop per FE unit.
        boolean manual = !energy && gun.getReloads().getReloadType() == ReloadType.MANUAL;
        return ScgunsReloadPlan.create(animations, prefix, manual, missing,
                gun.getReloads().getReloadAmount(), ticks, gun.getReloads().getReloadTimer());
    }

    private static Optional<JsonObject> read(ResourceLocation id) {
        String path = "/assets/" + id.getNamespace() + "/animations/item/" + id.getPath() + ".animation.json";
        try (var stream = GunItem.class.getResourceAsStream(path)) {
            if (stream == null) return Optional.empty();
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return Optional.ofNullable(root.getAsJsonObject("animations"));
        } catch (java.io.IOException | RuntimeException invalid) {
            HumanGunner.LOGGER.warn("Cannot read native reload timeline {}; using public gun timing", id, invalid);
            return Optional.empty();
        }
    }
}
