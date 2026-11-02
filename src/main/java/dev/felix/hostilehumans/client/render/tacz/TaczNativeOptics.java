package dev.felix.hostilehumans.client.render.tacz;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.util.math.MathUtil;
import dev.felix.hostilehumans.entity.Human;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ViewportEvent;

/** Native optic resource selection. Mutable attachment models belong to one observation. */
final class TaczNativeOptics {
    private static Human aimOwner;
    private static Object aimGun;
    private static net.minecraft.world.item.Item aimItem;
    private static NativeAimTimeline aimTimeline = new NativeAimTimeline();
    private ClientAttachmentIndex index;
    private BedrockAttachmentModel model;

    private static ClientAttachmentIndex index(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return null;
        var id = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(id)) id = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        return DefaultAssets.isEmptyAttachmentId(id) ? null : TimelessAPI.getClientAttachmentIndex(id).orElse(null);
    }

    private static int zoom(ItemStack stack) {
        return AttachmentItemDataAccessor.getZoomNumberFromTag(IGun.getIGunOrNull(stack).getAttachmentTag(stack, AttachmentType.SCOPE));
    }

    static String failureKey(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return "";
        var id = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(id)) id = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        return id + ":" + zoom(stack);
    }

    void update(ItemStack stack) {
        var selected = index(stack);
        if (selected == index) return;
        index = null;
        model = null;
        if (selected == null) return;
        IGun gun = IGun.getIGunOrNull(stack);
        var id = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (DefaultAssets.isEmptyAttachmentId(id)) id = gun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        var common = TimelessAPI.getCommonAttachmentIndex(id).orElseThrow(() -> new IllegalArgumentException("Missing native optic index"));
        var display = ClientAssetsManager.INSTANCE.getAttachmentDisplay(common.getPojo().getDisplay());
        if (display == null) throw new IllegalArgumentException("Missing native optic display");
        // This public factory constructs a NEW model from the immutable loaded POJO on both supported versions.
        var owned = ClientAttachmentIndex.getOrLoadAttachmentModel(display.getModel());
        if (owned == null) throw new IllegalArgumentException("Missing native optic model");
        owned.setIsScope(selected.isScope());
        owned.setIsSight(selected.isSight());
        owned.setTextShowList(display.getTextShows());
        index = selected;
        model = owned;
    }

    List<BedrockPart> aimPath(BedrockGunModel gun, ItemStack stack) {
        if (index == null) return gun.getIronSightPath();
        var mount = gun.getScopePosPath();
        if (mount == null) throw new IllegalArgumentException("Native gun has no optic mount");
        int[] views = index.getViews();
        if (views == null || views.length == 0) throw new IllegalArgumentException("Native optic has no views");
        var path = model.getScopeViewPath(views[Math.floorMod(zoom(stack), views.length)] - 1);
        if (path == null) throw new IllegalArgumentException("Native optic has no selected view");
        List<BedrockPart> combined = new ArrayList<>(mount);
        combined.addAll(path);
        return combined;
    }

    TaczOpticRenderContext enter(float aim) { return new TaczOpticRenderContext(index, model, aim); }

    static boolean isGun(ItemStack stack) { return IGun.getIGunOrNull(stack) != null; }

    static void fov(Human human, ViewportEvent.ComputeFov event) {
        ItemStack stack = human.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        var view = aim(human, (float) event.getPartialTick());
        float aim = view.aim();
        double base = event.getFOV();
        if (event.usedConfiguredFov()) {
            event.setFOV(MathUtil.magnificationToFov(effectiveMagnification(view), base));
        } else {
            var optic = index(stack);
            var display = TaczPresentationResources.display(stack);
            float target = display == null ? (float) base : display.getZoomModelFov();
            if (optic != null) {
                var values = optic.getViewsFov();
                if (values != null && values.length > 0) target = values[Math.floorMod(zoom(stack), values.length)];
            }
            if (Float.isFinite(target) && target > 1 && target < 179) event.setFOV(base + (target - base) * aim);
        }
    }
    static NativeAimTimeline.Sample aim(Human human, float partialTick) {
        ItemStack stack = human.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return new NativeAimTimeline.Sample(0, 1);
        Object id = gun.getGunId(stack);
        if (aimOwner != human || aimItem != stack.getItem() || !java.util.Objects.equals(aimGun, id)) {
            aimOwner = human; aimItem = stack.getItem(); aimGun = id;
            aimTimeline = new NativeAimTimeline();
        }
        var operator = IGunOperator.fromLivingEntity(human);
        float wanted = operator.getSynReloadState().getStateType().isReloading() ? 0 : operator.getSynAimingProgress();
        double time = (human.level().getGameTime() + (double) net.minecraft.util.Mth.clamp(partialTick, 0, 1)) / 20D;
        return aimTimeline.sample(time, wanted, gun.getAimingZoom(stack));
    }

    private static double effectiveMagnification(NativeAimTimeline.Sample sample) {
        return 1 + (sample.magnification() - 1) * sample.aim();
    }

    static double lookScale(Human human, float partialTick) {
        var sample = aim(human, partialTick);
        var client = net.minecraft.client.Minecraft.getInstance();
        double base = client.options.fov().get();
        double fov = MathUtil.magnificationToFov(effectiveMagnification(sample), base);
        double coefficient = com.tacz.guns.config.client.ZoomConfig.SCREEN_DISTANCE_COEFFICIENT.get();
        double multiplier = com.tacz.guns.config.client.ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.get();
        double scale = MathUtil.zoomSensitivityRatio(fov, base, coefficient)
                * (1 + (multiplier - 1) * sample.aim());
        return Double.isFinite(scale) && scale >= 0 ? scale : 1;
    }

    static void clearAim() {
        aimOwner = null; aimGun = null; aimItem = null;
        aimTimeline = new NativeAimTimeline();
    }

}
