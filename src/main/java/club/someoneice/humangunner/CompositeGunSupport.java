package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import java.util.*;

/** One tactical goal, multiple optional weapon backends, selected by the held item. */
final class CompositeGunSupport implements GunSupport {
    private static final GunSupport NONE = new GunSupport() {};
    private final List<GunSupport> providers;
    private CompositeGunSupport(List<GunSupport> providers) { this.providers = List.copyOf(providers); }
    static GunSupport create() {
        List<GunSupport> providers = new ArrayList<>();
        if (ModList.get().isLoaded("tacz") && UnifiedConfig.get().taczEnabled()) providers.add(new TaczIntegration());
        if (ModList.get().isLoaded("scguns") && UnifiedConfig.get().scguns().enabled()) providers.add(new ScgunsIntegration());
        return new CompositeGunSupport(providers);
    }
    private GunSupport forWeapon(ItemStack stack) {
        return providers.stream().filter(p -> p.isGun(stack)).findFirst().orElse(NONE);
    }
    public boolean enabled() { return !providers.isEmpty(); }
    public boolean isGun(ItemStack stack) { return forWeapon(stack) != NONE; }
    public int magazineCost(ItemStack stack) { return forWeapon(stack).magazineCost(stack); }
    public String gunType(ItemStack stack) { return forWeapon(stack).gunType(stack); }
    public ResourceLocation gunId(ItemStack stack) { return forWeapon(stack).gunId(stack); }
    public boolean hasLoadedAmmo(ItemStack stack) { return forWeapon(stack).hasLoadedAmmo(stack); }
    public boolean isBullet(DamageSource source) { return providers.stream().anyMatch(p -> p.isBullet(source)); }
    public double projectileSpeed(ItemStack stack) { return forWeapon(stack).projectileSpeed(stack); }
    public double projectileGravity(ItemStack stack) { return forWeapon(stack).projectileGravity(stack); }
    public double clearanceProjectileSpeed(ItemStack stack) { return forWeapon(stack).clearanceProjectileSpeed(stack); }
    public double clearanceProjectileGravity(ItemStack stack) { return forWeapon(stack).clearanceProjectileGravity(stack); }
    public net.minecraft.world.phys.Vec3 shotTargetPoint(ItemStack stack, LivingEntity target) {
        return forWeapon(stack).shotTargetPoint(stack, target);
    }
    public boolean ownsShotSpread(ItemStack stack) { return forWeapon(stack).ownsShotSpread(stack); }
    public String backendId(ItemStack stack) { return forWeapon(stack).backendId(); }
    public Map<String, Map<ResourceLocation, String>> catalogsByBackend() {
        Map<String, Map<ResourceLocation, String>> result = new LinkedHashMap<>();
        providers.forEach(p -> result.put(p.backendId(), p.catalog()));
        return result;
    }
    public Map<ResourceLocation, String> catalog() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        providers.forEach(p -> result.putAll(p.catalog()));
        return result;
    }
    public ItemStack createLoadedGun(GunChoice choice) {
        return providers.stream().filter(p -> p.backendId().equals(choice.backend())).findFirst()
                .map(p -> p.createLoadedGun(choice.id())).orElse(ItemStack.EMPTY);
    }
    public ItemStack createLoadedGun(ResourceLocation id) {
        for (GunSupport provider : providers) {
            ItemStack result = provider.createLoadedGun(id);
            if (!result.isEmpty()) return result;
        }
        return ItemStack.EMPTY;
    }
    public Goal goal(Human human) { return new GunnerGoal<>(human); }
    public void tickIdleReload(Human human) { providers.forEach(p -> {
        if (HumanGunAcceptance.accepts(human.getMainHandItem())) p.tickIdleReload(human);
    }); }
    public boolean isReloading(Human human) { return providers.stream().anyMatch(p -> p.isReloading(human)); }
    public boolean isReloading(Human human, ItemStack weapon) { return forWeapon(weapon).isReloading(human, weapon); }
    public void registerEvents() { providers.forEach(GunSupport::registerEvents); }
    public Control control(Human human) {
        Map<GunSupport, Control> controls = new IdentityHashMap<>();
        return new Control() {
            private Control previous = Control.NONE;
            private Control current() {
                Control next = controls.computeIfAbsent(forWeapon(human.getMainHandItem()), p -> p.control(human));
                if (next != previous) { previous.aim(false); previous = next; }
                return next;
            }
            public void aim(boolean value) { current().aim(value); }
            public void draw() { current().draw(); }
            public boolean ensureDrawn() { return current().ensureDrawn(); }
            public void clearSprintLock() { human.setSprinting(false); current().clearSprintLock(); }
            public void prepareUrgentShot() { current().prepareUrgentShot(); }
            public void shoot(ItemStack weapon, LivingEntity target, float pitch, float yaw) {
                if (!GunFireAuthorization.allows(human, weapon, target)) {
                    current().aim(false);
                    return;
                }
                controls.computeIfAbsent(forWeapon(weapon), p -> p.control(human)).shoot(weapon, target, pitch, yaw);
            }
        };
    }
}
