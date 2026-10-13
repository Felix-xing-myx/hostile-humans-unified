package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import ttv.migami.jeg.common.Gun;
import ttv.migami.jeg.common.ReloadType;
import ttv.migami.jeg.entity.ai.AIGunEvent;
import ttv.migami.jeg.entity.projectile.ProjectileEntity;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.util.GunEnchantmentHelper;
import ttv.migami.jeg.util.GunModifierHelper;
import java.util.*;

/** Public NPC projectile API. Shared GunnerGoal exclusively owns navigation and tactics. */
final class JegIntegration implements GunSupport {
    private static final String RELOAD = HumanGunner.MOD_ID + ":jeg_reload";
    private final Map<String, String> types = UnifiedConfig.get().jegGunTypes();
    private static final String NEXT_SHOT = HumanGunner.MOD_ID + ":jeg_next_shot";
    public boolean enabled() { return true; }
    private static final Set<ResourceLocation> SUPPORTED_COMBAT_AMMO = Set.of(
            new ResourceLocation("jeg", "pistol_ammo"), new ResourceLocation("jeg", "rifle_ammo"),
            new ResourceLocation("jeg", "shotgun_shell"), new ResourceLocation("jeg", "handmade_shell"),
            new ResourceLocation("jeg", "spectre_round"), new ResourceLocation("jeg", "blaze_round"),
            new ResourceLocation("minecraft", "echo_shard"), new ResourceLocation("minecraft", "sculk_catalyst"),
            new ResourceLocation("minecraft", "fire_charge"), new ResourceLocation("jeg", "grenade"),
            new ResourceLocation("jeg", "rocket"), new ResourceLocation("jeg", "water_bomb"),
            new ResourceLocation("jeg", "pocket_bubble"));
    public boolean isGun(ItemStack stack) {
        return GunSupport.isJegGunItem(stack) && SUPPORTED_COMBAT_AMMO.contains(definition(stack).getProjectile().getItem());
    }
    private Gun definition(ItemStack stack) { return ((GunItem) stack.getItem()).getModifiedGun(stack); }
    public String backendId() { return "jeg"; }
    public ResourceLocation gunId(ItemStack stack) { return isGun(stack) ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null; }
    public String gunType(ItemStack stack) {
        if (!isGun(stack)) return "";
        String id = gunId(stack).toString();
        String configured = types.get(id);
        if (configured != null) return configured;
        return "rifle"; // Unknown addon guns have an explicit, documented conservative default.
    }
    public int magazineCost(ItemStack stack) {
        String type = gunType(stack);
        return UnifiedConfig.get().scguns().magazineCosts().getOrDefault(type,
                HumanAmmoPolicy.magazineCost(type.equals("lmg") ? "mg" : type));
    }
    private static final String VIRTUAL_AMMO = HumanGunner.MOD_ID + ":jeg_virtual_ammo";
    private boolean inventoryFed(ItemStack stack) {
        return definition(stack).getReloads().getReloadType() == ReloadType.INVENTORY_FED;
    }
    private int capacity(ItemStack stack) {
        return inventoryFed(stack) ? UnifiedConfig.get().jegInventoryFedCapacity()
                : Math.max(1, GunModifierHelper.getModifiedAmmoCapacity(stack, definition(stack)));
    }
    // Native capacity stays valid for players/renderers; only NPC inventory-fed reserves use our own key.
    private int rounds(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null) return 0;
        String key = inventoryFed(stack) && tag.contains(VIRTUAL_AMMO) ? VIRTUAL_AMMO : "AmmoCount";
        return Math.max(0, Math.min(capacity(stack), tag.getInt(key)));
    }
    private void rounds(ItemStack stack, int count) {
        int bounded = Math.max(0, Math.min(capacity(stack), count));
        if (inventoryFed(stack)) {
            stack.getOrCreateTag().putInt(VIRTUAL_AMMO, bounded);
            stack.getOrCreateTag().putInt("AmmoCount", Math.min(bounded,
                    Math.max(1, GunModifierHelper.getModifiedAmmoCapacity(stack, definition(stack)))));
        } else stack.getOrCreateTag().putInt("AmmoCount", bounded);
    }
    public boolean hasLoadedAmmo(ItemStack stack) { return isGun(stack) && rounds(stack) > 0; }
    public Map<ResourceLocation, String> catalog() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (isGun(stack) && !gunId(stack).getPath().equals("abstract_gun")) result.put(gunId(stack), gunType(stack));
        }
        return result;
    }
    public ItemStack createLoadedGun(ResourceLocation id) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (!isGun(stack)) return ItemStack.EMPTY;
        rounds(stack, capacity(stack));
        return stack;
    }
    public boolean ownsShotSpread(ItemStack stack) { return true; }
    public net.minecraft.world.phys.Vec3 shotOriginOffset(ItemStack stack, LivingEntity shooter) {
        var look = shooter.getLookAngle();
        return new net.minecraft.world.phys.Vec3(-look.x, 0, -look.z);
    }
    public double maximumProjectileReach(ItemStack stack) {
        int life = GunModifierHelper.getModifiedProjectileLife(stack, definition(stack).getProjectile().getLife());
        return Math.max(1, clearanceProjectileSpeed(stack) * Math.max(1, life) - 1);
    }
    public double projectileSpeed(ItemStack stack) { return clearanceProjectileSpeed(stack); }
    public double projectileGravity(ItemStack stack) { return clearanceProjectileGravity(stack); }
    public double clearanceProjectileSpeed(ItemStack stack) {
        return GunModifierHelper.getModifiedProjectileSpeed(stack,
                definition(stack).getProjectile().getSpeed() * GunEnchantmentHelper.getProjectileSpeedModifier(stack));
    }
    public double clearanceProjectileGravity(ItemStack stack) {
        return definition(stack).getProjectile().isGravity()
                ? -GunModifierHelper.getModifiedProjectileGravity(stack, -.04D) : 0;
    }
    public Goal goal(Human human) { return new GunnerGoal<>(human); }
    public boolean isReloading(Human human, ItemStack stack) {
        if (!isGun(stack) || stack.getTag() == null || !stack.getTag().hasUUID(RELOAD)) return false;
        CompoundTag state = human.getPersistentData().getCompound(RELOAD);
        return state.hasUUID("transaction") && state.getUUID("transaction").equals(stack.getTag().getUUID(RELOAD))
                && state.getInt("ticks") > 0;
    }
    private List<ItemStack> custody(Human human) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(human.getMainHandItem()); stacks.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            stacks.add(human.getData().getInventoryItem(i));
        return stacks;
    }
    public boolean isReloading(Human human) { return custody(human).stream().anyMatch(s -> isReloading(human, s)); }
    private void startReload(Human human, ItemStack stack) {
        if (human.level().isClientSide || human.isUsingItem() || isReloading(human)
                || rounds(stack) >= capacity(stack) || !HumanAmmo.canReload(human, stack)) return;
        Gun gun = definition(stack);
        var plan = JegReloadPlan.create(stack, gun, rounds(stack), capacity(stack));
        if (plan.isEmpty() || !HumanAmmo.consumeMagazines(human, stack)) return;
        CompoundTag state = new CompoundTag();
        UUID transaction = UUID.randomUUID();
        state.putUUID("transaction", transaction);
        var steps = new net.minecraft.nbt.ListTag();
        for (var step : plan) {
            var entry = new CompoundTag(); entry.putInt("ticks", step.ticks()); entry.putInt("rounds", step.rounds());
            steps.add(entry);
        }
        state.put("steps", steps); state.putInt("step", 0); state.putInt("ticks", plan.get(0).ticks());
        state.putInt("duration", plan.get(0).ticks()); state.putInt("elapsed", 0);
        stack.getOrCreateTag().putUUID(RELOAD, transaction);
        human.getPersistentData().put(RELOAD, state);

    }
    private void tickReload(Human human, ItemStack stack) {
        if (!isReloading(human, stack)) return;
        CompoundTag state = human.getPersistentData().getCompound(RELOAD);
        long now = human.level().getGameTime();
        if (state.contains("last_tick") && state.getLong("last_tick") == now) return;
        state.putLong("last_tick", now);
        int elapsed = state.getInt("elapsed") + 1;
        state.putInt("elapsed", elapsed);
        Gun gun = definition(stack);
        if (elapsed == 4) JegSounds.send(human, gun.getSounds().getReloadStart(), 1, false, true);
        int ticks = state.getInt("ticks") - 1;
        boolean manual = gun.getReloads().getReloadType() == ReloadType.MANUAL;
        if (!manual && ticks == state.getInt("duration") / 2)
            JegSounds.send(human, gun.getSounds().getReloadLoad(), 1, false, true);
        if (!manual && ticks == 5) JegSounds.send(human, gun.getSounds().getEjectorPull(), 1, false, true);
        if (ticks > 0) { state.putInt("ticks", ticks); return; }
        var steps = state.getList("steps", net.minecraft.nbt.Tag.TAG_COMPOUND);
        int index = state.getInt("step");
        if (index >= 0 && index < steps.size()) {
            rounds(stack, Math.min(capacity(stack), rounds(stack) + Math.max(0, steps.getCompound(index).getInt("rounds"))));
            if (++index < steps.size()) {
                state.putInt("step", index); state.putInt("ticks", Math.max(1, steps.getCompound(index).getInt("ticks")));
                state.putInt("duration", state.getInt("ticks"));
                JegSounds.send(human, gun.getSounds().getReloadLoad(), 1, false, true);
                return;
            }
            JegSounds.send(human, gun.getSounds().getReloadEnd(), 1, false, true);
            JegSounds.send(human, gun.getSounds().getEjectorRelease(), 1, false, true);
        } // A corrupt schedule never fills a gun.
        stack.getOrCreateTag().remove(RELOAD); human.getPersistentData().remove(RELOAD);
    }
    public void tickIdleReload(Human human) {
        if (human.level().isClientSide) return;
        custody(human).forEach(stack -> tickReload(human, stack));
        if (human.getPersistentData().contains(RELOAD) && !isReloading(human)) human.getPersistentData().remove(RELOAD);
        if ((human.tickCount + Math.floorMod(human.getId(), 20)) % 20 != 0) return;
        ItemStack stack = human.getMainHandItem();
        if (isGun(stack) && GunReloadPolicy.shouldIdleTopOff(rounds(stack), capacity(stack), human.getTarget() != null,
                human.isFleeing, human.isUsingItem(), isReloading(human))) startReload(human, stack);
    }
    public Control control(Human human) {
        return new Control() {
            private ItemStack preparing = ItemStack.EMPTY;
            private LivingEntity preparedTarget;
            private long readyAt, lastPreparation;
            public void aim(boolean aiming) {
                if (!aiming) { preparing = ItemStack.EMPTY; preparedTarget = null; }
            }
            public void shoot(ItemStack stack, LivingEntity target, float pitch, float yaw) {
                if (!GunFireAuthorization.allows(human, stack, target) || !isGun(stack) || human.isUsingItem()
                        || isReloading(human, stack)) return;
                if (!hasLoadedAmmo(stack)) { startReload(human, stack); return; }
                long now = human.level().getGameTime();
                if (now < human.getPersistentData().getLong(NEXT_SHOT)) return;
                Gun gun = definition(stack);
                int windup = Math.max(0, gun.getGeneral().getFireTimer());
                if (windup > 0) {
                    if (preparing != stack || preparedTarget != target || now - lastPreparation > 2) {
                        preparing = stack; preparedTarget = target; readyAt = now + windup;
                    }
                    lastPreparation = now;
                    if (now < readyAt) return;
                }
                preparing = ItemStack.EMPTY; preparedTarget = null;
                human.setXRot(pitch); human.setYRot(yaw); human.setYHeadRot(yaw);
                float spread = (float) (HumanGrowth.gunSpreadMultiplier(human)
                        / Math.max(.05, UnifiedConfig.get().scguns().accuracyModifier()));
                AIGunEvent.performGunAttack(human, target, stack, gun, spread, false);
                rounds(stack, rounds(stack) - 1);
                int delay = Math.max(1, GunEnchantmentHelper.getRate(stack, gun));
                if (gun.getGeneral().getFireMode() == ttv.migami.jeg.common.FireMode.BURST) {
                    String key = HumanGunner.MOD_ID + ":jeg_burst_remaining";
                    int burst = stack.getOrCreateTag().getInt(key);
                    if (burst <= 0) burst = Math.max(1, gun.getGeneral().getBurstAmount());
                    stack.getOrCreateTag().putInt(key, --burst);
                    if (burst == 0) delay += Math.max(1, gun.getGeneral().getBurstDelay());
                }
                human.getPersistentData().putLong(NEXT_SHOT, now + delay);
                human.getPersistentData().putLong(HumanGunner.MOD_ID + ":last_gunshot_game_time", now);
                ResourceLocation sound = GunModifierHelper.isSilencedFire(stack) ? gun.getSounds().getSilencedFire() : gun.getSounds().getFire();
                JegSounds.send(human, sound, .5F, !GunModifierHelper.isSilencedFire(stack), false);
            }
        };
    }
    static boolean isNativeBullet(DamageSource source) {
        return source.getDirectEntity() instanceof ProjectileEntity
                || source.getDirectEntity() instanceof ttv.migami.jeg.entity.Splash;
    }
    static LivingEntity projectileOwner(net.minecraft.world.entity.Entity direct) {
        if (direct instanceof ProjectileEntity projectile) return projectile.getShooter();
        if (direct instanceof ttv.migami.jeg.entity.Splash splash) return splash.getOwner();
        if (direct instanceof ttv.migami.jeg.entity.Bubble bubble) return bubble.getOwner();
        return null;
    }
    public boolean isBullet(DamageSource source) { return isNativeBullet(source); }
    public void registerEvents() { MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, JegIntegration::onDamage); }
    private static void onDamage(LivingHurtEvent event) {
        if (!isNativeBullet(event.getSource())) return;
        if (event.getSource().getEntity() instanceof Human attacker) {
            if (HumanRelations.allied(attacker, event.getEntity())) { event.setCanceled(true); return; }
            event.setAmount(HumanGrowth.scaleGunDamage(attacker, event.getAmount(),
                    UnifiedConfig.get().scguns().damageMultiplier() * UnifiedConfig.get().tierScgunsDamage(TierAttributes.key(attacker))));
        }
        if (event.getEntity() instanceof Human human)
            human.getPersistentData().putInt(HumanGunner.MOD_ID + ":incoming_gunfire_until", human.tickCount + 40);
    }
}
