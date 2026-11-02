package dev.felix.hostilehumans.compat.scguns;
import net.minecraft.world.item.ItemStack;
/** Native type check, reached only after the scguns installation gate. */
public final class ScgunsMedicalBridge {
    private ScgunsMedicalBridge() {}
    static boolean supports(ItemStack stack) { return stack.getItem() instanceof top.ribs.scguns.item.HealingBandageItem; }
}
