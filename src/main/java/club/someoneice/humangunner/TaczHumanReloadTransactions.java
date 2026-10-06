package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** TaCZ-only bridge called at the native start/tick boundary, after cooldowns and events. */
public final class TaczHumanReloadTransactions {
    private static final ReloadPaymentLedger<ShooterDataHolder, ItemStack> PAYMENTS = new ReloadPaymentLedger<>();
    private static final java.util.Map<ShooterDataHolder, Long> INVENTORY_FEED_END = new java.util.WeakHashMap<>();
    private TaczHumanReloadTransactions() {}

    public static boolean start(AbstractGunItem gun, ShooterDataHolder data,
                                ItemStack weapon, LivingEntity shooter) {
        if (!(shooter instanceof Human human)) return gun.startReload(data, weapon, shooter);
        if (human.level().isClientSide || human.getServer() == null || !human.getServer().isSameThread()
                || weapon != human.getMainHandItem()) return false;
        int loaded = gun.getCurrentAmmoCount(weapon);
        boolean chambered = gun.hasBulletInBarrel(weapon);
        var index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
        if (index == null || index.getGunData().getReloadData() == null) return false;
        int capacity = Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(weapon, index.getGunData()));
        var reload = index.getGunData().getReloadData();
        boolean inventoryFeed = reload.getType() == FeedType.INVENTORY;
        int occupied = GunReloadPolicy.capacityCheckedRounds(loaded, chambered, inventoryFeed,
                index.getGunData().getBolt() == Bolt.OPEN_BOLT);
        if (occupied >= capacity) return false;
        boolean inCombat = human.getTarget() != null;
        int thresholdRounds = inCombat
                ? GunReloadPolicy.capacityCheckedRounds(loaded, chambered, true,
                index.getGunData().getBolt() == Bolt.OPEN_BOLT) : occupied;
        if (!GunReloadPolicy.reloadThresholdSatisfied(thresholdRounds, capacity, inCombat)
                || human.isUsingItem() || (!inCombat && human.isFleeing)) return false;
        int missing = Math.max(1, capacity - loaded);
        int recoveryDelay = GunReloadPolicy.recoveryDelayTicks(reload.getType() == FeedType.MANUAL, missing,
                reload.getFeed() == null ? 0 : reload.getFeed().getEmptyTime(),
                reload.getCooldown() == null ? 0 : reload.getCooldown().getEmptyTime());
        boolean empty = occupied == 0;
        int feedDelay = GunReloadPolicy.inventoryFeedDurationTicks(
                reload.getFeed() == null ? 0 : empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime(),
                reload.getCooldown() == null ? 0 : empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime());
        boolean started = PAYMENTS.begin(data, weapon,
                human.level().getGameTime() + GunReloadPolicy.hardLimitTicks(recoveryDelay),
                () -> HumanAmmo.canReload(human, weapon),
                () -> inventoryFeed || gun.startReload(data, weapon, shooter),
                () -> HumanAmmo.consumeMagazines(human, weapon));
        if (started && inventoryFeed) {
            // Inventory-feed guns have no native reload script. NPCs instead
            // carry the pack's magazine capacity and use a paid timed refill.
            INVENTORY_FEED_END.put(data, human.level().getGameTime() + feedDelay);
        }
        if (!started) {
            // A pack script may mutate rounds before rejecting start, or change the
            // backpack during start. Failed authorization cannot create ammunition.
            gun.setCurrentAmmoCount(weapon, loaded);
            gun.setBulletInBarrel(weapon, chambered);
        }
        return started;
    }

    public static ReloadState tick(AbstractGunItem gun, ShooterDataHolder data,
                                   ItemStack weapon, LivingEntity shooter) {
        if (!(shooter instanceof Human human)) return gun.tickReload(data, weapon, shooter);
        if (human.level().isClientSide || weapon != human.getMainHandItem()
                || !PAYMENTS.authorized(data, weapon, human.level().getGameTime())) {
            end(data);
            return new ReloadState();
        }
        Long feedEnd = INVENTORY_FEED_END.get(data);
        if (feedEnd != null) {
            ReloadState state = new ReloadState();
            if (human.level().getGameTime() < feedEnd) {
                state.setStateType(data.reloadStateType);
                state.setCountDown((feedEnd - human.level().getGameTime()) * 50L);
                return state;
            }
            var index = TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
            if (index != null) {
                int capacity = Math.max(1, AttachmentDataUtils.getAmmoCountWithAttachment(weapon, index.getGunData()));
                if (index.getGunData().getBolt() != Bolt.OPEN_BOLT) {
                    gun.setCurrentAmmoCount(weapon, capacity - 1);
                    gun.setBulletInBarrel(weapon, true);
                } else {
                    gun.setCurrentAmmoCount(weapon, capacity);
                    gun.setBulletInBarrel(weapon, false);
                }
            }
            end(data);
            return state;
        }
        ReloadState state = gun.tickReload(data, weapon, shooter);
        if (!state.getStateType().isReloading()) PAYMENTS.end(data);
        return state;
    }

    public static boolean isPaidReloading(Human human, ShooterDataHolder data) {
        boolean active = data.reloadTimestamp != -1L && data.reloadStateType.isReloading()
                && PAYMENTS.authorized(data, human.getMainHandItem(), human.level().getGameTime());
        if (!active) end(data);
        return active;
    }

    public static void end(ShooterDataHolder data) {
        PAYMENTS.end(data);
        INVENTORY_FEED_END.remove(data);
        data.reloadTimestamp = -1L;
        data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
    }

    public static void forgetFinished(ShooterDataHolder data) {
        if (!data.reloadStateType.isReloading() || data.reloadTimestamp == -1L) {
            PAYMENTS.end(data);
            INVENTORY_FEED_END.remove(data);
        }
    }
}
