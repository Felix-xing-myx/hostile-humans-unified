package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.entity.ai.AIGunEvent;
import top.ribs.scguns.entity.projectile.ProjectileEntity;
import top.ribs.scguns.event.GunEventBus;
import top.ribs.scguns.item.GunItem;
import top.ribs.scguns.interfaces.IEnergyGun;
import top.ribs.scguns.util.GunModifierHelper;
import java.util.*;

/** Scorched Guns 0.5.5 public NPC shot API; never installs its tactical/navigation goal. */
final class ScgunsIntegration implements GunSupport {
    private static final String RELOAD = HumanGunner.MOD_ID + ":scguns_reload";
    private static final String NEXT_SHOT = HumanGunner.MOD_ID + ":scguns_next_shot";
    public boolean enabled() { return true; }
    public boolean isGun(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem)) return false;
        if (!(stack.getItem() instanceof IEnergyGun)) return true;
        var storage = energy(stack);
        return storage != null && storage.canReceive() && storage.canExtract() && storage.getMaxEnergyStored() > 0;
    }
    private static net.minecraftforge.energy.IEnergyStorage energy(ItemStack stack) {
        return stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElse(null);
    }
    private boolean energyWeapon(ItemStack stack) { return stack.getItem() instanceof IEnergyGun; }
    private int energyCost(ItemStack stack) { return Math.max(1, definition(stack).getProjectile(stack).getEnergyUse()); }
    private int remaining(ItemStack stack) { return energyWeapon(stack) ? energy(stack).getEnergyStored() : Gun.getAmmoCount(stack); }
    private void fill(ItemStack stack) {
        if (energyWeapon(stack)) {
            var storage = energy(stack);
            storage.receiveEnergy(Math.max(0, storage.getMaxEnergyStored() - storage.getEnergyStored()), false);
        } else writeRounds(stack, capacity(stack));
    }
    public int magazineCost(ItemStack stack) {
        String nativeType = definition(stack).getGeneral().getWeaponType().name().toLowerCase(Locale.ROOT);
        return UnifiedConfig.get().scguns().magazineCosts().getOrDefault(nativeType, HumanAmmoPolicy.magazineCost(gunType(stack)));
    }
    private Gun definition(ItemStack stack) { return ((GunItem) stack.getItem()).getModifiedGun(stack); }
    public String gunType(ItemStack stack) {
        if (!isGun(stack)) return "";
        return switch (definition(stack).getGeneral().getWeaponType().name().toLowerCase(Locale.ROOT)) {
            case "lmg" -> "mg";
            case "magnum" -> "pistol";
            case "heavy", "flamethrower" -> "rpg";
            case "shock", "laser", "plasma", "special" -> "rifle";
            default -> definition(stack).getGeneral().getWeaponType().name().toLowerCase(Locale.ROOT);
        };
    }
    public ResourceLocation gunId(ItemStack stack) { return isGun(stack) ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null; }
    public String backendId() { return "scguns"; }
    public Map<ResourceLocation, String> catalog() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (isGun(stack)) result.put(BuiltInRegistries.ITEM.getKey(item), gunType(stack));
        }
        return result;
    }
    public ItemStack createLoadedGun(ResourceLocation id) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        if (!isGun(stack)) return ItemStack.EMPTY;
        fill(stack);
        return stack;
    }
    public boolean hasLoadedAmmo(ItemStack stack) {
        return isGun(stack) && (infinite(stack) || (energyWeapon(stack)
                ? energy(stack).extractEnergy(energyCost(stack), true) >= energyCost(stack) : Gun.getAmmoCount(stack) > 0));
    }
    private boolean infinite(ItemStack stack) { return definition(stack).getGeneral().getInfiniteAmmo() || Gun.Reloads.hasInfiniteAmmo(stack); }
    private int capacity(ItemStack stack) {
        return energyWeapon(stack) ? energy(stack).getMaxEnergyStored()
                : Math.max(1, GunModifierHelper.getModifiedAmmoCapacity(stack, definition(stack)));
    }
    /** Only native NPC schema write: public Gun API has no ammo-count setter in 0.5.5. */
    private static void writeRounds(ItemStack stack, int rounds) { stack.getOrCreateTag().putInt("AmmoCount", Math.max(0, rounds)); }
    public boolean ownsShotSpread(ItemStack stack) { return true; }
    public net.minecraft.world.phys.Vec3 shotTargetPoint(ItemStack stack, net.minecraft.world.entity.LivingEntity target) {
        // AIGunEvent aims at 80% of the target's eye height, not its eyes.
        return target.position().add(0, target.getEyeHeight() * 0.8D, 0);
    }
    public double projectileSpeed(ItemStack stack) { return 0; } // AIGunEvent owns native leading; avoid double prediction
    public double projectileGravity(ItemStack stack) { return 0; } // native AIGunEvent handles projectile physics
    public double clearanceProjectileSpeed(ItemStack stack) {
        return GunModifierHelper.getModifiedProjectileSpeed(stack, definition(stack).getProjectile().getSpeed()
                * top.ribs.scguns.util.GunEnchantmentHelper.getProjectileSpeedModifier(stack));
    }
    public double clearanceProjectileGravity(ItemStack stack) {
        return definition(stack).getProjectile().isGravity()
                ? -GunModifierHelper.getModifiedProjectileGravity(stack, -0.04D) : 0;
    }
    public Goal goal(Human human) { return new GunnerGoal<>(human); }
    public boolean isReloading(Human human) {
        if (isReloading(human, human.getMainHandItem()) || isReloading(human, human.getOffhandItem())) return true;
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            if (isReloading(human, human.getData().getInventoryItem(i))) return true;
        return false;
    }
    public boolean isReloading(Human human, ItemStack stack) {
        if (!isGun(stack) || stack.getTag() == null || !stack.getTag().hasUUID(RELOAD)) return false;
        var state = human.getPersistentData().getCompound(RELOAD);
        return state.hasUUID("transaction") && stack.getTag().getUUID(RELOAD).equals(state.getUUID("transaction"))
                && state.getInt("ticks") > 0;
    }
    private void startReload(Human human, ItemStack stack) {
        if (human.level().isClientSide || human.isUsingItem() || isReloading(human)
                || !HumanAmmo.canReload(human, stack) || remaining(stack) >= capacity(stack)) return;
        Gun gun = definition(stack);
        var plan = ScgunsReloadTiming.plan(human, stack, gun,
                Math.max(0, capacity(stack) - remaining(stack)), energyWeapon(stack));
        if (plan.isEmpty() || !HumanAmmo.consumeMagazines(human, stack)) return;
        var state = new net.minecraft.nbt.CompoundTag();
        java.util.UUID transaction = java.util.UUID.randomUUID();
        state.putUUID("transaction", transaction);
        var steps = new net.minecraft.nbt.ListTag();
        for (var step : plan) {
            var entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("ticks", step.ticks());
            entry.putInt("rounds", step.rounds());
            steps.add(entry);
        }
        state.put("steps", steps);
        state.putInt("step", 0);
        state.putInt("ticks", plan.get(0).ticks());
        // The gun carries only a stable identity. Per-tick progress stays server-side
        // on the Human, avoiding an equipment/NBT network update on every reload tick.
        stack.getOrCreateTag().putUUID(RELOAD, transaction);
        human.getPersistentData().put(RELOAD, state);
        ScgunsSounds.reload(human, gun, false);
    }
    private void tickReload(Human human, ItemStack stack) {
        if (!isReloading(human, stack)) return;
        var state = human.getPersistentData().getCompound(RELOAD);
        long now = human.level().getGameTime();
        if (state.contains("last_tick") && state.getLong("last_tick") == now) return;
        state.putLong("last_tick", now);
        int remaining = state.getInt("ticks") - 1;
        if (remaining <= 0) {
            var steps = state.getList("steps", net.minecraft.nbt.Tag.TAG_COMPOUND);
            int index = state.getInt("step");
            // Old saved transactions have no schedule: finish their already-paid timer once.
            if (steps.isEmpty()) fill(stack);
            else {
                if (index < 0 || index >= steps.size()) {
                    stack.getOrCreateTag().remove(RELOAD);
                    human.getPersistentData().remove(RELOAD);
                    return; // corrupt schedule must not create free ammo
                }
                if (!energyWeapon(stack)) writeRounds(stack, Math.min(capacity(stack), Gun.getAmmoCount(stack)
                        + Math.max(0, steps.getCompound(index).getInt("rounds"))));
                if (++index < steps.size()) {
                    state.putInt("step", index);
                    state.putInt("ticks", Math.max(1, steps.getCompound(index).getInt("ticks")));
                    if (steps.getCompound(index).getInt("rounds") > 0)
                        ScgunsSounds.reload(human, definition(stack), false);
                    return;
                }
                if (energyWeapon(stack)) fill(stack);
            }
            stack.getOrCreateTag().remove(RELOAD);
            human.getPersistentData().remove(RELOAD);
            ScgunsSounds.reload(human, definition(stack), true);
        } else state.putInt("ticks", remaining);
    }
    public void tickIdleReload(Human human) {
        if (human.level().isClientSide) return;
        tickReload(human, human.getMainHandItem());
        tickReload(human, human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            tickReload(human, human.getData().getInventoryItem(i));
        if (human.getPersistentData().contains(RELOAD) && !isReloading(human))
            human.getPersistentData().remove(RELOAD); // the paid gun left custody; never fill a replacement
        if ((human.tickCount + Math.floorMod(human.getId(), 20)) % 20 != 0) return;
        ItemStack weapon = human.getMainHandItem();
        if (isGun(weapon) && GunReloadPolicy.shouldIdleTopOff(remaining(weapon), capacity(weapon),
                human.getTarget() != null, human.isFleeing, human.isUsingItem(), isReloading(human, weapon))) startReload(human, weapon);
    }
    public Control control(Human human) {
        return new Control() {
            public void shoot(ItemStack weapon, net.minecraft.world.entity.LivingEntity target, float pitch, float yaw) {
                if (!GunFireAuthorization.allows(human, weapon, target) || !isGun(weapon) || human.isUsingItem()) return;
                if (isReloading(human, weapon)) return;
                if (!hasLoadedAmmo(weapon)) { startReload(human, weapon); return; }
                long now = human.level().getGameTime();
                if (now < human.getPersistentData().getLong(NEXT_SHOT)) return;
                Gun gun = definition(weapon);
                human.setXRot(pitch); human.setYRot(yaw); human.setYHeadRot(yaw);
                float accuracy = (float) (UnifiedConfig.get().scguns().accuracyModifier()
                        / Math.max(.01, HumanGrowth.gunSpreadMultiplier(human)));
                AIGunEvent.performGunAttack(human, target, weapon, gun, accuracy);
                if (!infinite(weapon)) {
                    if (energyWeapon(weapon)) energy(weapon).extractEnergy(energyCost(weapon), false);
                    else writeRounds(weapon, Gun.getAmmoCount(weapon) - 1);
                }
                int rate = Math.max(1, GunModifierHelper.getModifiedRate(weapon, gun.getGeneral().getRate()));
                // Honor native mob rate config; projectile count/spread/effects remain AIGunEvent-owned.
                double multiplier = top.ribs.scguns.Config.COMMON.gameplay.mobFireRateMultiplier.get();
                int delay = Math.max(1, (int) (rate * multiplier));
                if (Gun.hasBurstFire(weapon)) {
                    String key = HumanGunner.MOD_ID + ":scguns_burst_remaining";
                    int remaining = weapon.getOrCreateTag().getInt(key);
                    if (remaining <= 0) remaining = Math.max(1, Gun.getBurstCount(weapon));
                    weapon.getOrCreateTag().putInt(key, --remaining);
                    if (remaining == 0) delay += Math.max(1, (int) (Gun.getBurstCooldown(weapon)
                            * top.ribs.scguns.Config.COMMON.gameplay.mobBurstDelayMultiplier.get()));
                }
                human.getPersistentData().putLong(NEXT_SHOT, now + delay);
                human.getPersistentData().putLong(HumanGunner.MOD_ID + ":last_gunshot_game_time", now);
                GunEventBus.ejectCasing(human.level(), human, false);
                ScgunsSounds.fire(human, gun);
            }
        };
    }
    public boolean isBullet(DamageSource source) { return source.getDirectEntity() instanceof ProjectileEntity; }
    public void registerEvents() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ScgunsIntegration::onProjectileJoin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ScgunsIntegration::onProjectileHit);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ScgunsIntegration::onDamage);
    }
    /** Restore unscaled native projectile inputs before damage/armor processing.
     * AIDamageScale is the sole upstream NPC schema field without a public setter.
     * Removing it also makes rejoining a saved projectile idempotent.
     */
    private static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ProjectileEntity projectile)
                || !(projectile.getShooter() instanceof Human)
                || !projectile.getPersistentData().contains("AIDamageScale")) return;
        ItemStack weapon = projectile.getWeapon();
        if (!(weapon.getItem() instanceof GunItem)) return;
        projectile.setAdditionalDamage(Gun.getAdditionalDamage(weapon));
        projectile.getPersistentData().remove("AIDamageScale");
    }
    static boolean isHumanBullet(DamageSource source) {
        return source.getEntity() instanceof Human && source.getDirectEntity() instanceof ProjectileEntity;
    }
    private static void onProjectileHit(top.ribs.scguns.event.GunProjectileHitEvent event) {
        if (event.getRayTrace() instanceof net.minecraft.world.phys.EntityHitResult hit) {
            var shooter = event.getProjectile().getShooter();
            if (HumanOwnerProtection.protects(hit.getEntity(), shooter)
                    || (shooter instanceof Human human && HumanRelations.allied(human, hit.getEntity())))
                event.setCanceled(true);
        }
    }
    private static void onDamage(LivingHurtEvent event) {
        ScgunsThrowableDamage.onDamage(event);
        if (!(event.getSource().getDirectEntity() instanceof ProjectileEntity)) return;
        if (event.getSource().getEntity() instanceof Human attacker) {
            if (HumanRelations.allied(attacker, event.getEntity())) { event.setCanceled(true); return; }
            event.setAmount(HumanGrowth.scaleGunDamage(attacker, event.getAmount(),
                    UnifiedConfig.get().scguns().damageMultiplier()
                            * UnifiedConfig.get().tierScgunsDamage(TierAttributes.key(attacker))));
        }
        if (event.getEntity() instanceof Human human) human.getPersistentData().putInt(
                HumanGunner.MOD_ID + ":incoming_gunfire_until", human.tickCount + 40);
    }
}
