package club.someoneice.humangunner;

import com.tacz.guns.api.item.IGun;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/** Only constructed when TaCZ is installed; independent of NPC gun configuration. */
final class TaczGunItemIdentity implements Predicate<ItemStack> {
    @Override public boolean test(ItemStack stack) { return IGun.getIGunOrNull(stack) != null; }
}
