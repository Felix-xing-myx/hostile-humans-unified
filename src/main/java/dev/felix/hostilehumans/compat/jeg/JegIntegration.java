package dev.felix.hostilehumans.compat.jeg;

import dev.felix.hostilehumans.behavior.ActionProgressSnapshot;
import dev.felix.hostilehumans.behavior.combat.gun.GunnerGoal;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.combat.gun.GunAttackAction;
import dev.felix.hostilehumans.behavior.combat.gun.GunCombatProfile;
import dev.felix.hostilehumans.behavior.combat.gun.GunFireAuthorization;
import dev.felix.hostilehumans.behavior.combat.gun.GunReloadPolicy;
import dev.felix.hostilehumans.behavior.combat.gun.GunReloadSession;
import dev.felix.hostilehumans.behavior.combat.gun.GunSupport;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmo;
import dev.felix.hostilehumans.behavior.combat.ranged.HumanAmmoPolicy;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.damage.HumanDamagePipeline;
import dev.felix.hostilehumans.faction.HumanRelations;
import dev.felix.hostilehumans.perception.HumanAwareness;
import dev.felix.hostilehumans.progression.HumanGrowth;
import dev.felix.hostilehumans.progression.TierAttributes;

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
public final class JegIntegration implements GunSupport {
    private static final String RELOAD = HumanRuntime.MOD_ID + ":jeg_reload";
    private final Map<String, String> types = UnifiedConfig.get().jegGunTypes();
    private static final String NEXT_SHOT = HumanRuntime.MOD_ID + ":jeg_next_shot";
    long nextShotAt(Human human) { return human.getPersistentData().getLong(NEXT_SHOT); }
    void tickMonitorReload(Human human, ItemStack weapon) { tickReload(human, weapon); }
    boolean continuousTrigger(ItemStack weapon) {
        var mode = definition(weapon).getGeneral().getFireMode();
        return mode != ttv.migami.jeg.common.FireMode.SEMI_AUTO && mode != ttv.migami.jeg.common.FireMode.PULSE;
    }
    boolean releaseTrigger(ItemStack weapon) {
        return definition(weapon).getGeneral().getFireMode() == ttv.migami.jeg.common.FireMode.RELEASE_FIRE;
    }
    public boolean enabled() { return true; }
    public java.util.List<ActionProgressSnapshot> reloadProgress(Human human) { return GunReloadSession.progress(human, RELOAD); }
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
    public dev.felix.hostilehumans.behavior.combat.gun.GunHudData hud(ItemStack stack) {
        if (!isGun(stack)) return dev.felix.hostilehumans.behavior.combat.gun.GunHudData.EMPTY;
        return new dev.felix.hostilehumans.behavior.combat.gun.GunHudData("jeg", gunId(stack), rounds(stack), capacity(stack),
                definition(stack).getGeneral().getFireMode().getId().getPath(), false);
    }
    public String backendId() { return "jeg"; }
    public ResourceLocation gunId(ItemStack stack) { return isGun(stack) ? BuiltInRegistries.ITEM.getKey(stack.getItem()) : null; }
    public String gunType(ItemStack stack) {
        if (!isGun(stack)) return "";
        String id = gunId(stack).toString();
        String configured = types.get(id);
        if (configured != null) return configured;
        return "other"; // Unknown addon weapons use the confirmed conservative fallback.
    }
    public int magazineCost(ItemStack stack) {
        String type = gunType(stack);
        return UnifiedConfig.get().scguns().magazineCosts().getOrDefault(type,
                HumanAmmoPolicy.magazineCost(type.equals("lmg") ? "mg" : type));
    }
    private static final String VIRTUAL_AMMO = HumanRuntime.MOD_ID + ":jeg_virtual_ammo";
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
        return Math.max(0, clearanceProjectileSpeed(stack) * Math.max(0, life) - 1);
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
    public boolean isReloading(Human human,ItemStack stack) {
        return isGun(stack)&&GunReloadSession.matches(human,stack,RELOAD)
                &&human.getPersistentData().getCompound(RELOAD).getInt("ticks")>0;
    }
    private List<ItemStack> custody(Human human) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(human.getMainHandItem()); stacks.add(human.getOffhandItem());
        if (human.getData() != null) for (int i = 0; i < human.getData().getInventoryItemsSize(); i++)
            stacks.add(human.getData().getInventoryItem(i));
        return stacks;
    }
    public boolean isReloading(Human human) { return custody(human).stream().anyMatch(s -> isReloading(human, s)); }
    public boolean beginReload(Human human,ItemStack stack) {
        if(!isGun(stack)) return false;
        startReload(human,stack);
        return isReloading(human,stack);
    }
    public void cancelReload(Human human,ItemStack stack) { GunReloadSession.cancel(human,stack,RELOAD); }
    private void startReload(Human human, ItemStack stack) {
        if (human.level().isClientSide || !GunCombatProfile.canReloadDuringUse(human) || isReloading(human)
                || rounds(stack) >= capacity(stack) || !HumanAmmo.canReload(human, stack)) return;
        Gun gun = definition(stack);
        var plan = JegReloadPlan.create(stack, gun, rounds(stack), capacity(stack));
        if(plan.isEmpty()) return;
        var steps=new net.minecraft.nbt.ListTag();
        for(var step:plan) {
            var row=new net.minecraft.nbt.CompoundTag();row.putInt("ticks",step.ticks());row.putInt("rounds",step.rounds());steps.add(row);
        }
        if(GunReloadSession.begin(human,stack,RELOAD,steps)==null) return;
    }
    private void tickReload(Human human,ItemStack stack) {
        GunReloadSession.runTick(human,stack,RELOAD,(state,work)->tickReloadSteps(human,stack,state,work));
    }
    private void tickReloadSteps(Human human,ItemStack stack,net.minecraft.nbt.CompoundTag state,double work) {
        var steps=state.getList("steps",net.minecraft.nbt.Tag.TAG_COMPOUND);
        if(steps.isEmpty() || state.getInt("step") < 0 || state.getInt("step") > steps.size()) {
            GunReloadSession.cancel(human,stack,RELOAD,state);return;
        }
        double boundary=0;
        for(int i=0;i<steps.size();i++) {
            boundary+=Math.max(1,steps.getCompound(i).getInt("ticks"));
            if(i<state.getInt("step")||work+1e-8<boundary) continue;
            if(!GunReloadSession.matches(human,stack,RELOAD,state)) return;
            int add=Math.max(0,steps.getCompound(i).getInt("rounds"));
            int loaded=Math.min(capacity(stack),rounds(stack)+add);
            if(!GunReloadSession.matches(human,stack,RELOAD,state)) return;
            state.putInt("step",i+1); // Publish before native item/capability callbacks.
            rounds(stack,loaded);
            if(!GunReloadSession.matches(human,stack,RELOAD,state)) return;
            if(!GunCombatProfile.backgroundReload(human,stack)&&add>0) JegSounds.send(human,definition(stack).getSounds().getReloadLoad(),1,false,true);
        }
        if(state.getInt("step")>=steps.size()) {
            if(!GunReloadSession.complete(human,stack,RELOAD,state,()->{})) return;
            if(!GunCombatProfile.backgroundReload(human,stack)) JegSounds.send(human,definition(stack).getSounds().getReloadEnd(),1,false,true);
        }
        if(human.getData()!=null&&stack!=human.getMainHandItem()) human.getData().markInventoryDirty();
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
                if (!GunAttackAction.owns(human)||!GunFireAuthorization.allows(human, stack, target) || !isGun(stack) || human.isUsingItem()
                        || isReloading(human, stack)) return;
                if (!hasLoadedAmmo(stack)) { if (target != null) startReload(human, stack); return; }
                long now = human.level().getGameTime();
                if (now < human.getPersistentData().getLong(NEXT_SHOT)) return;
                Gun gun = definition(stack);
                int windup = Math.max(0, gun.getGeneral().getFireTimer());
                // A manual RELEASE_FIRE attempt already earned its charge while held; AI windup is unchanged.
                if (windup > 0 && !(target == null && releaseTrigger(stack))) {
                    if (preparing != stack || preparedTarget != target || target != null && now - lastPreparation > 2) {
                        preparing = stack; preparedTarget = target; readyAt = now + windup;
                    }
                    lastPreparation = now;
                    if (now < readyAt) return;
                }
                if (target != null || !continuousTrigger(stack)) { preparing = ItemStack.EMPTY; preparedTarget = null; }
                human.setXRot(pitch); human.setYRot(yaw); human.setYHeadRot(yaw);
                float spread = (float) (HumanGrowth.gunSpreadMultiplier(human)*GunCombatProfile.spread(human,stack)
                        / Math.max(.05, UnifiedConfig.get().scguns().accuracyModifier()));
                AIGunEvent.performGunAttack(human, target, stack, gun, spread, false);
                HumanAwareness.gunshot(human);
                rounds(stack, rounds(stack) - 1);
                int delay = Math.max(1, GunEnchantmentHelper.getRate(stack, gun));
                if (gun.getGeneral().getFireMode() == ttv.migami.jeg.common.FireMode.BURST) {
                    String key = HumanRuntime.MOD_ID + ":jeg_burst_remaining";
                    int burst = stack.getOrCreateTag().getInt(key);
                    if (burst <= 0) burst = Math.max(1, gun.getGeneral().getBurstAmount());
                    stack.getOrCreateTag().putInt(key, --burst);
                    if (burst == 0) delay += Math.max(1, gun.getGeneral().getBurstDelay());
                }
                human.getPersistentData().putLong(NEXT_SHOT, now + delay);
                JegMonitorRecoilAdapter.committed(human, stack, gun);
                human.getPersistentData().putLong(HumanRuntime.MOD_ID + ":last_gunshot_game_time", now);
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
    public void registerEvents() { MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, JegIntegration::onDamage);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, JegIntegration::onGunSound); }
    private static void onGunSound(ttv.migami.jeg.event.GunFireEvent.Post event){
        if(!event.isClient())HumanAwareness.gunshot(event.getEntity());
    }
    private static void onDamage(LivingHurtEvent event) {
        if (!isNativeBullet(event.getSource())) return;
        if (event.getSource().getEntity() instanceof Human attacker) {
            if (HumanRelations.allied(attacker, event.getEntity())) { event.setCanceled(true); return; }
            ItemStack firedWeapon = event.getSource().getDirectEntity() instanceof ProjectileEntity projectile
                    ? projectile.getWeapon() : ItemStack.EMPTY;
            event.setAmount(HumanDamagePipeline.scaleGunDamage(attacker, firedWeapon, event.getAmount(),
                    UnifiedConfig.get().scguns().damageMultiplier() * UnifiedConfig.get().tierScgunsDamage(TierAttributes.key(attacker))));
        }
        if (event.getEntity() instanceof Human human)
            human.getPersistentData().putInt(HumanRuntime.MOD_ID + ":incoming_gunfire_until", human.tickCount + 40);
    }
}
