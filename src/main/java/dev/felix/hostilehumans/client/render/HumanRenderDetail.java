package dev.felix.hostilehumans.client.render;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.entity.Human;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cosmetic render detail, using the previous frame's rendered population. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class HumanRenderDetail {
    enum Level { FULL, REDUCED }
    private static int rendered, pressure;

    private HumanRenderDetail() {}

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (Minecraft.getInstance().level == null) {
            rendered = pressure = 0;
            return;
        }
        int previous = rendered;
        rendered = 0;
        if (previous >= 48) pressure = 2;
        else if (previous >= 24 && pressure < 2) pressure = 1;
        else if (previous < 40 && pressure == 2) pressure = 1;
        if (previous < 18) pressure = 0;
    }

    static Level select(Human human, boolean armsOnly) {
        var client = Minecraft.getInstance();
        var camera = client.getCameraEntity();
        if (armsOnly || camera == null || camera == human || client.level != human.level()
                || client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
            return Level.FULL;
        rendered++;
        double distance = client.gameRenderer.getMainCamera().getPosition().distanceToSqr(human.position());
        int near = pressure == 2 ? 12 : pressure == 1 ? 18 : 32;
        return distance < near * near ? Level.FULL : Level.REDUCED;
    }
}
