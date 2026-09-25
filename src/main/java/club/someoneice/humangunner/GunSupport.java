package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.Map;

/** Optional weapon boundary. Public signatures contain only Minecraft/JDK types. */
public interface GunSupport {
    static GunSupport get() { return Holder.INSTANCE; }

    final class Holder {
        private static final GunSupport INSTANCE = ModList.get().isLoaded("tacz")
                && UnifiedConfig.get().taczEnabled() ? new TaczIntegration() : new GunSupport() {};
        private Holder() {}
    }

    default boolean enabled() { return false; }
    default boolean isGun(ItemStack stack) { return false; }
    default String gunType(ItemStack stack) { return ""; }
    default ResourceLocation gunId(ItemStack stack) { return null; }
    default boolean isBullet(DamageSource source) { return false; }
    default Map<ResourceLocation, String> catalog() { return Map.of(); }
    default ItemStack createLoadedGun(ResourceLocation id) { return ItemStack.EMPTY; }
    default Goal goal(Human human) { return new Goal() { public boolean canUse() { return false; } }; }
    default Control control(Human human) { return Control.NONE; }
    default void tickIdleReload(Human human) {}
    default boolean isReloading(Human human) { return false; }
    default void registerEvents() {}

    interface Control {
        Control NONE = new Control() {};
        default void aim(boolean aiming) {}
        default void draw() {}
        default void clearSprintLock() {}
        default void prepareUrgentShot() {}
        default void shoot(ItemStack weapon, float pitch, float yaw) {}
    }
}
