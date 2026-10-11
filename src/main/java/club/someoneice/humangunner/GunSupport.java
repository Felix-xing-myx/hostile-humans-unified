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

    /** Physical gun identity must not depend on the NPC integration toggle. */
    static boolean isGunItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && GunIdentityHolder.IDENTITY.test(stack);
    }
    /** Provider-specific animation/hooks must not accept another backend's weapon. */
    static boolean isTaczGunItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && GunIdentityHolder.TACZ.test(stack);
    }
    static boolean isScgunsGunItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && GunIdentityHolder.SCGUNS.test(stack);
    }
    final class GunIdentityHolder {
        private static final java.util.function.Predicate<ItemStack> TACZ = ModList.get().isLoaded("tacz")
                ? new TaczGunItemIdentity() : stack -> false;
        private static final java.util.function.Predicate<ItemStack> SCGUNS = ModList.get().isLoaded("scguns")
                ? new ScgunsGunIdentity() : stack -> false;
        private static final java.util.function.Predicate<ItemStack> IDENTITY = stack -> TACZ.test(stack) || SCGUNS.test(stack);
        private GunIdentityHolder() { }
    }

    final class Holder {
        private static final GunSupport INSTANCE = CompositeGunSupport.create();
        private Holder() {}
    }

    default boolean enabled() { return false; }
    default boolean isGun(ItemStack stack) { return false; }
    default int magazineCost(ItemStack stack) { return HumanAmmoPolicy.magazineCost(gunType(stack)); }
    default String gunType(ItemStack stack) { return ""; }
    default ResourceLocation gunId(ItemStack stack) { return null; }
    default boolean isBullet(DamageSource source) { return false; }
    default Map<ResourceLocation, String> catalog() { return Map.of(); }
    default ItemStack createLoadedGun(ResourceLocation id) { return ItemStack.EMPTY; }
    default boolean hasLoadedAmmo(ItemStack gun) { return false; }
    default Goal goal(Human human) { return new Goal() { public boolean canUse() { return false; } }; }
    default Control control(Human human) { return Control.NONE; }
    default void tickIdleReload(Human human) {}
    default boolean isReloading(Human human) { return false; }
    default boolean isReloading(Human human, ItemStack weapon) {
        return weapon == human.getMainHandItem() && isReloading(human);
    }
    default double projectileSpeed(ItemStack weapon) { return 0; }
    default double projectileGravity(ItemStack weapon) { return 0; }
    default boolean ownsShotSpread(ItemStack weapon) { return false; }
    default void registerEvents() {}

    interface Control {
        Control NONE = new Control() {};
        default void aim(boolean aiming) {}
        default void draw() {}
        default boolean ensureDrawn() { draw(); return false; }
        default void clearSprintLock() {}
        default void prepareUrgentShot() {}
        default void shoot(ItemStack weapon, float pitch, float yaw) {}
    }
}
