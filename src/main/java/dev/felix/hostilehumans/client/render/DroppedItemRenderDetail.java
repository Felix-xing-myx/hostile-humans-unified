package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Limits repeated copies of one stack model under dense render load. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class DroppedItemRenderDetail {
    private static int renderedCopies;
    private static boolean reduced;

    private DroppedItemRenderDetail() {}

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (Minecraft.getInstance().level == null || renderedCopies < 96) reduced = false;
        else if (renderedCopies >= 160) reduced = true;
        renderedCopies = 0;
    }

    public static int copies(int vanilla) {
        // Count vanilla work even while reduced to keep the threshold stable.
        renderedCopies += vanilla;
        return reduced ? Math.min(1, vanilla) : vanilla;
    }
}
