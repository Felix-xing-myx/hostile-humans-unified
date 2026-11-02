package dev.felix.hostilehumans.client.ui.gun;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.item.GunItem;

/** Native ammunition atlas icon, with no invocation of player-bound HUD caches. */
public final class ScgunsMonitorHud {
    private ScgunsMonitorHud() {}
    public static void icon(GuiGraphics g,ItemStack stack,int x,int y) {
        if(stack.getItem() instanceof GunItem gun) {
            var definition=gun.getModifiedGun(stack);
            var ammo=definition.getProjectile(stack).getItem();
            if(ammo!=null)g.renderItem(new ItemStack(ammo),x,y);
        }
    }
}
