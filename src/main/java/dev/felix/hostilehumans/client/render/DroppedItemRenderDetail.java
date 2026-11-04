package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Distance detail for independent dropped entities, measured before culling. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class DroppedItemRenderDetail {
    private static int attempted;
    private static boolean reduced;

    private DroppedItemRenderDetail() {}

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (Minecraft.getInstance().level == null || attempted < 96) reduced = false;
        else if (attempted >= 160) reduced = true;
        attempted = 0;
    }

    public static boolean hide(ItemEntity item) {
        attempted++;
        if (!reduced || item.isCurrentlyGlowing()) return false;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.isInitialized() && camera.getPosition().distanceToSqr(item.position()) > 24 * 24;
    }
}
