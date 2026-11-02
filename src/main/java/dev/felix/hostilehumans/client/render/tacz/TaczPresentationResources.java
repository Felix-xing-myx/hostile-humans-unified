package dev.felix.hostilehumans.client.render.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Public immutable pack metadata. Never obtains the shared GunDisplayInstance model/state machine. */
public final class TaczPresentationResources {
    private TaczPresentationResources() {}

    // Added in newer TaCZ releases. Resolve by capability, never by a pack filename or version string.
    private static final java.lang.reflect.Method TRANSPARENCY = transparencyAccessor();
    private static java.lang.reflect.Method transparencyAccessor() {
        try { return GunDisplay.class.getMethod("enablesTransparency"); }
        catch (NoSuchMethodException | SecurityException absent) { return null; }
    }
    /** Older TaCZ uses cutout rendering and has no transparency property at all. */
    static boolean transparent(GunDisplay display) {
        if (TRANSPARENCY == null) return false;
        try { return Boolean.TRUE.equals(TRANSPARENCY.invoke(display)); }
        catch (ReflectiveOperationException | RuntimeException failure) {
            dev.felix.hostilehumans.client.render.ClientAnimationCompatibility.warnOnce(
                    "TaCZ native transparency", "Cannot read optional transparency property; retaining cutout rendering", failure);
            return false;
        }
    }

    public static GunDisplay display(ItemStack weapon) {
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null) return null;
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null || index.getPojo() == null) return null;
        var requested = gun.getGunDisplayId(weapon);
        var assets = ClientAssetsManager.INSTANCE;
        GunDisplay display = requested == null || DefaultAssets.DEFAULT_GUN_DISPLAY_ID.equals(requested)
                ? null : assets.getGunDisplay(requested);
        return display != null ? display : assets.getGunDisplay(index.getPojo().getDisplay());
    }

    /** Capability gate only: the clip plan inspects real resource tracks, never gun or script filenames. */
    public static boolean canSample(GunDisplay display) {
        if (display == null || display.getAnimationLocation() == null) return false;
        var file = ClientAssetsManager.INSTANCE.getBedrockAnimations(display.getAnimationLocation());
        if (file == null) return false;
        java.util.Map<String, Double> lengths = new java.util.HashMap<>();
        file.getAnimations().forEach((name, animation) -> {
            if (Double.isFinite(animation.getAnimationLength()) && animation.getAnimationLength() > 0)
                lengths.put(name, animation.getAnimationLength());
        });
        return NativeReloadPlan.hasCanonicalClips(lengths);
    }

    public static NativeReloadPlan plan(Map<String, Double> lengths, CompoundTag timeline) {
        if (!timeline.contains("NativeTicks") || !timeline.contains("Empty"))
            return NativeReloadPlan.create(Map.of(), new NativeReloadPlan.Request(false, 0, 0, false, ""));
        return NativeReloadPlan.create(lengths, new NativeReloadPlan.Request(timeline.getBoolean("Empty"),
                timeline.getInt("MagazineLevel"), timeline.getInt("Missing"), timeline.getBoolean("Manual"),
                timeline.getString("FireMode")));
    }

    public static double workSeconds(Human human, float partialTick) {
        var timeline = human.getTaczReloadVisual();
        return NativeReloadPlan.checkpointWork(timeline.getDouble("Work"), timeline.getDouble("WorkRate"),
                timeline.getLong("WorkTick"),(double) human.level().getGameTime()+partialTick,timeline.getBoolean("Complete"))/20D;
    }

    public static NativeReloadPlan.Phase phase(CompoundTag tag) {
        if (!tag.contains("PhaseIndex")) return null;
        return new NativeReloadPlan.Phase(tag.getInt("PhaseIndex"), tag.getBoolean("PhaseFinishing"),
                tag.getDouble("PhaseStartWork") / 20D, tag.getDouble("PhaseEndWork") / 20D,
                tag.getInt("PhaseSuppliedStart"));
    }

    public static double presentationTime(NativeReloadPlan plan, Human human, float partialTick) {
        var timeline = human.getTaczReloadVisual();
        return plan.presentationTime(workSeconds(human, partialTick), timeline.getInt("NativeTicks") / 20D, phase(timeline));
    }
}
