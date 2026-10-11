package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
    public double projectileSpeed(ItemStack stack) { return 0; } // AIGunEvent owns native leading; avoid double prediction
    public double projectileGravity(ItemStack stack) { return 0; } // native AIGunEvent handles projectile physics
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
        if (!HumanAmmo.consumeMagazines(human, stack)) return;
        Gun gun = definition(stack);
        int ticks = Math.max(1, (int) Math.ceil(gun.getReloads().getReloadTimer()
                / Math.max(.01, GunModifierHelper.getModifiedReloadSpeed(stack, 1))));
        var state = new net.minecraft.nbt.CompoundTag();
        java.util.UUID transaction = java.util.UUID.randomUUID();
        state.putUUID("transaction", transaction);
        state.putInt("ticks", ticks);
        // The gun carries only a stable identity. Per-tick progress stays server-side
        // on the Human, avoiding an equipment/NBT network update on every reload tick.
        stack.getOrCreateTag().putUUID(RELOAD, transaction);
        human.getPersistentData().put(RELOAD, state);
        sound(human, top.ribs.scguns.init.ModSounds.ITEM_PISTOL_RELOAD.get(), 1);
    }
    private void tickReload(Human human, ItemStack stack) {
        if (!isReloading(human, stack)) return;
        var state = human.getPersistentData().getCompound(RELOAD);
        long now = human.level().getGameTime();
        if (state.contains("last_tick") && state.getLong("last_tick") == now) return;
        state.putLong("last_tick", now);
        int remaining = state.getInt("ticks") - 1;
        if (remaining <= 0) {
            fill(stack);
            stack.getOrCreateTag().remove(RELOAD);
            human.getPersistentData().remove(RELOAD);
            sound(human, top.ribs.scguns.init.ModSounds.ITEM_PISTOL_COCK.get(), 1);
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
            public void shoot(ItemStack weapon, float pitch, float yaw) {
                if (human.level().isClientSide || !isGun(weapon) || human.getTarget() == null
                        || !human.getTarget().isAlive() || !human.canAttack(human.getTarget()) || human.isUsingItem()) return;
                if (isReloading(human, weapon)) return;
                if (!hasLoadedAmmo(weapon)) { startReload(human, weapon); return; }
                long now = human.level().getGameTime();
                if (now < human.getPersistentData().getLong(NEXT_SHOT)) return;
                Gun gun = definition(weapon);
                human.setXRot(pitch); human.setYRot(yaw); human.setYHeadRot(yaw);
                float accuracy = (float) (UnifiedConfig.get().scguns().accuracyModifier()
                        / Math.max(.01, HumanGrowth.gunSpreadMultiplier(human)));
                AIGunEvent.performGunAttack(human, human.getTarget(), weapon, gun, accuracy);
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
                ResourceLocation fire = gun.getSounds().getFire();
                if (fire != null) sound(human, SoundEvent.createVariableRangeEvent(fire),
                        Math.max(0, top.ribs.scguns.Config.COMMON.gameplay.mobGunfireVolume.get() - .5F));
            }
        };
    }
    private static void sound(Human human, SoundEvent sound, float volume) {
        human.level().playSound(null, human.blockPosition(), sound, SoundSource.HOSTILE, volume, 1);
    }
    public boolean isBullet(DamageSource source) { return source.getDirectEntity() instanceof ProjectileEntity; }
    public void registerEvents() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ScgunsIntegration::onProjectileHit);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, ScgunsIntegration::onDamage);
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
        if (!(event.getSource().getDirectEntity() instanceof ProjectileEntity)) return;
        if (event.getSource().getEntity() instanceof Human attacker) {
            if (HumanRelations.allied(attacker, event.getEntity())) { event.setCanceled(true); return; }
            event.setAmount(event.getAmount() * (float) UnifiedConfig.get().scguns().damageMultiplier()
                    * (float) UnifiedConfig.get().tierScgunsDamage(TierAttributes.key(attacker))
                    * (float) HumanGrowth.gunDamageMultiplier(attacker));
        }
        if (event.getEntity() instanceof Human human) human.getPersistentData().putInt(
                HumanGunner.MOD_ID + ":incoming_gunfire_until", human.tickCount + 40);
    }
}
