package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.craftix.hostile_humans.patch.HostileHumansEquipmentPatch;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import static club.someoneice.humangunner.HumanGunner.LOGGER;

/** Loaded exclusively by the installed-and-enabled TaCZ branch in GunSupport. */
final class TaczIntegration implements GunSupport {
    private static final String INCOMING_GUNFIRE_UNTIL = "humangunner:incoming_gunfire_until";
    private static final AtomicBoolean LOGGED_FIRST_GUN_HIT = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_ROAMER_GUN_HIT = new AtomicBoolean();
    public boolean enabled() { return true; }
    public boolean isGun(ItemStack stack) { return IGun.getIGunOrNull(stack) != null; }
    public String gunType(ItemStack stack) {
        ResourceLocation id = gunId(stack);
        return id == null ? "" : TimelessAPI.getCommonGunIndex(id)
                .map(index -> index.getType()).orElse("");
    }
    public ResourceLocation gunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? null : gun.getGunId(stack);
    }
    public boolean isBullet(DamageSource source) {
        return source.is(com.tacz.guns.init.ModDamageTypes.BULLETS_TAG)
                || source.getDirectEntity() instanceof com.tacz.guns.entity.EntityKineticBullet;
    }
    public Map<ResourceLocation, String> catalog() {
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        TimelessAPI.getAllCommonGunIndex().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.put(e.getKey(), e.getValue().getType().toLowerCase(java.util.Locale.ROOT)));
        return result;
    }
    public ItemStack createLoadedGun(ResourceLocation id) {
        var index = TimelessAPI.getCommonGunIndex(id).orElse(null);
        if (index == null || index.getGunData().getFireModeSet().isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = GunItemBuilder.create().setId(id).build();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return ItemStack.EMPTY;
        gun.setCurrentAmmoCount(stack, Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData())));
        gun.setFireMode(stack, index.getGunData().getFireModeSet().get(0));
        gun.setBulletInBarrel(stack, true);
        return stack;
    }
    public Goal goal(Human human) { return new GunnerGoal<>(human); }
    public void tickIdleReload(Human human) {
        // A staggered one-second check avoids a reload attempt on every tick
        // for every gunner. GunCustody has already restored an idle backpack
        // gun to the main hand before this hook runs.
        if ((human.tickCount + Math.floorMod(human.getId(), 20)) % 20 != 0) return;
        ItemStack weapon = human.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(weapon);
        if (gun == null || gun.useInventoryAmmo(weapon)) return;
        ResourceLocation gunId = gun.getGunId(weapon);
        if (gunId == null) return;
        var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null || index.getGunData().getReloadData() == null) return;
        int capacity = AttachmentDataUtils.getAmmoCountWithAttachment(weapon, index.getGunData());
        IGunOperator operator = IGunOperator.fromLivingEntity(human);
        boolean reloading = operator.getDataHolder().reloadStateType.isReloading()
                || operator.getSynReloadState().getStateType().isReloading();
        if (!GunReloadPolicy.shouldIdleTopOff(gun.getCurrentAmmoCount(weapon), capacity,
                human.getTarget() != null, human.isFleeing, human.isUsingItem(), reloading)) return;

        // The native operator needs a current-gun supplier even when its combat
        // goal has never run. Drawing once may impose TaCZ's ordinary draw
        // cooldown; the next check will start a normal timed reload.
        var currentGun = operator.getDataHolder().currentGunItem;
        if (currentGun == null || currentGun.get() != weapon) {
            operator.draw(human::getMainHandItem);
            return;
        }
        operator.reload();
    }
    public Control control(Human human) {
        IGunOperator operator = IGunOperator.fromLivingEntity(human);
        return new Control() {
            public void aim(boolean aiming) { operator.aim(aiming); }
            public void draw() { operator.draw(human::getMainHandItem); }
            public void clearSprintLock() { GunnerGoal.clearNpcSprintLock(human, operator); }
            public void prepareUrgentShot() { GunnerGoal.prepareUrgentCloseShot(human, operator); }
            public void shoot(ItemStack weapon, float pitch, float yaw) {
                GunnerGoal.handleShootResult(human, operator, weapon, operator.shoot(() -> pitch, () -> yaw));
            }
        };
    }
    public void registerEvents() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TaczIntegration::onGunDamagePre);
        MinecraftForge.EVENT_BUS.addListener(TaczIntegration::onGunDamagePost);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            TaczReloadSoundClient.register();
        }
    }
    private static boolean isActivelyBlockingWithShield(Human human) {
        return human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem());
    }
    private static void onGunDamagePre(EntityHurtByGunEvent.Pre event) {
        if (event.getHurtEntity() instanceof Human defender
                && event.getAttacker() instanceof Player player
                && !HumanRelations.isControlledBy(defender, player)) {
            IdentityBadgeAccess.rememberPlayerAttack(defender, player);
        }
        if (event.getAttacker() instanceof Human attacker) {
            if (event.getHurtEntity() instanceof Player player
                    && HumanRelations.allied(attacker, player)) {
                event.setBaseAmount(0.0F);
                return;
            }
            if (event.getHurtEntity() instanceof Human target
                    && HumanGunner.shouldBlockHumanDamage(attacker, target)) {
                event.setBaseAmount(0.0F);
                return;
            }
            event.setBaseAmount(event.getBaseAmount()
                    * (float) (HumanGunnerConfig.get().humanGunDamageMultiplier()
                    * UnifiedConfig.get().tierGunDamage(TierAttributes.key(attacker))));
        } else if (event.getAttacker() instanceof Pillager) {
            event.setBaseAmount(event.getBaseAmount()
                    * (float) HumanGunnerConfig.get().pillagerGunDamageMultiplier());
        }
        if (event.getHurtEntity() instanceof Human defender && event.getBaseAmount() > 0.0F) {
            defender.getPersistentData().putInt(INCOMING_GUNFIRE_UNTIL, defender.tickCount + 40);
            if (isActivelyBlockingWithShield(defender)) {
                // TaCZ does not pass through vanilla projectile shield handling,
                // so apply both the shield durability cost and the requested
                // 70% reduction at its own damage event.
                HostileHumansEquipmentPatch.damageShield(defender, event.getBaseAmount());
                event.setBaseAmount(event.getBaseAmount() * (float) UnifiedConfig.get().gunSetting("shield_damage_multiplier", .3));
            }
        }
    }
    private static void onGunDamagePost(EntityHurtByGunEvent.Post event) {
        if (event.getAttacker() instanceof Human attacker
                && attacker.getTier() == HumanTier.ROAMER
                && event.getHurtEntity() instanceof Human target
                && target.getTier() == HumanTier.ROAMER
                && event.getAmount() > 0.0F
                && LOGGED_FIRST_ROAMER_GUN_HIT.compareAndSet(false, true)) {
            LOGGER.info(
                    "Human Gunner confirmed roamer friendly-fire compatibility: {} hit {} with {} for {} damage",
                    attacker.getUUID(),
                    target.getUUID(),
                    event.getGunId(),
                    event.getAmount()
            );
        }
        if (event.getAttacker() instanceof Human
                && event.getAmount() > 0.0F
                && LOGGED_FIRST_GUN_HIT.compareAndSet(false, true)) {
            LOGGER.info(
                    "Human Gunner first confirmed projectile hit: {} hit {} with {} for {} damage",
                    event.getAttacker().getUUID(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(event.getHurtEntity().getType()),
                    event.getGunId(),
                    event.getAmount()
            );
        }
    }
}
