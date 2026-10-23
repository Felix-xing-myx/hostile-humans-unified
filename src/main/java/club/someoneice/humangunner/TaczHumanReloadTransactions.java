package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** TaCZ-only bridge called at the native start/tick boundary, after cooldowns and events. */
public final class TaczHumanReloadTransactions {
    private static final ReloadPaymentLedger<ShooterDataHolder, ItemStack> PAYMENTS = new ReloadPaymentLedger<>();
    private static final java.util.Map<ShooterDataHolder, Long> INVENTORY_FEED_END = new java.util.WeakHashMap<>();
    private static final java.util.Map<ShooterDataHolder, net.minecraft.resources.ResourceLocation> GUN_IDS = new java.util.WeakHashMap<>();
    private static final java.util.Map<ShooterDataHolder, java.lang.ref.WeakReference<Human>> VISUAL_OWNERS = new java.util.WeakHashMap<>();
    private static final java.util.Map<ShooterDataHolder, Clock> CLOCKS = new java.util.WeakHashMap<>();
    private static final java.util.Map<Human, java.lang.ref.WeakReference<Clock>> OBSERVATIONS = new java.util.WeakHashMap<>();
    private static final java.util.Set<ShooterDataHolder> STARTING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<ShooterDataHolder,Long> GENERATIONS = new java.util.WeakHashMap<>();
    private static final java.util.Set<ShooterDataHolder> TICKING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final class Clock {
        final HumanEquipmentExchange.Handle weapon;
        final com.craftix.hostile_humans.entity.data.HumanData inventory;
        final long session;
        final long startedTick;
        final java.lang.ref.WeakReference<Human> human;
        final java.lang.ref.WeakReference<ShooterDataHolder> holder;
        final java.lang.ref.WeakReference<ItemStack> actual;
        long gameTick, wallMillis;
        double work, fraction;
        double rate = -1;
        long sampledTick, phaseRemainingMillis = -1;
        String phase = "starting";
        Clock(Human human, GunReloadAuthority.Capture authority, ShooterDataHolder holder) {
            weapon=authority.handle;session=authority.session;inventory=authority.inventory;
            gameTick=human.level().getGameTime();wallMillis=System.currentTimeMillis();
            startedTick=sampledTick=gameTick;
            this.human=new java.lang.ref.WeakReference<>(human);
            this.holder=new java.lang.ref.WeakReference<>(holder);
            actual=new java.lang.ref.WeakReference<>(authority.weapon);
        }
    }
    private TaczHumanReloadTransactions() {}

    public static boolean start(AbstractGunItem gun, ShooterDataHolder data,
                                ItemStack weapon, LivingEntity shooter) {
        if (!(shooter instanceof Human human)) return gun.startReload(data, weapon, shooter);
        if (TICKING.contains(data)||!STARTING.add(data)) return false;
        long generation=GENERATIONS.getOrDefault(data,0L);
        try { return startHuman(gun,data,weapon,human,generation); }
        finally { STARTING.remove(data); }
    }

    private static boolean startHuman(AbstractGunItem gun,ShooterDataHolder data,ItemStack weapon,Human human,long generation) {
        var authority=GunReloadAuthority.capture(human,weapon);
        if(authority==null) return false;
        var gunId=gun.getGunId(weapon);
        var index = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (index == null || index.getGunData().getReloadData() == null || !authority.validSource()
                ||GENERATIONS.getOrDefault(data,0L)!=generation) return false;
        TaczNpcMagazine.normalize(weapon, index);
        if(!authority.validSource()||GENERATIONS.getOrDefault(data,0L)!=generation) return false;
        int loaded = gun.getCurrentAmmoCount(weapon);
        boolean chambered = gun.hasBulletInBarrel(weapon);
        int capacity = TaczNpcMagazine.capacity(weapon, index);
        var reload = index.getGunData().getReloadData();
        boolean inventoryFeed = TaczNpcMagazine.inventoryFed(weapon, index);
        int occupied = GunReloadPolicy.capacityCheckedRounds(loaded, chambered, inventoryFeed,
                index.getGunData().getBolt() == Bolt.OPEN_BOLT);
        if (occupied >= capacity) return false;
        boolean inCombat = human.getTarget() != null;
        int thresholdRounds = inCombat
                ? GunReloadPolicy.capacityCheckedRounds(loaded, chambered, true,
                index.getGunData().getBolt() == Bolt.OPEN_BOLT) : occupied;
        if (!GunReloadPolicy.reloadThresholdSatisfied(thresholdRounds, capacity, inCombat)
                || !GunCombatProfile.canReloadDuringUse(human) || (!inCombat && human.isFleeing)) return false;
        int missing = Math.max(1, capacity - loaded);
        int recoveryDelay = GunReloadPolicy.recoveryDelayTicks(reload.getType() == FeedType.MANUAL, missing,
                reload.getFeed() == null ? 0 : reload.getFeed().getEmptyTime(),
                reload.getCooldown() == null ? 0 : reload.getCooldown().getEmptyTime());
        boolean empty = occupied == 0;
        int feedDelay = GunReloadPolicy.inventoryFeedDurationTicks(
                reload.getFeed() == null ? 0 : empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime(),
                reload.getCooldown() == null ? 0 : empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime());
        java.util.function.BooleanSupplier validStart=() -> GENERATIONS.getOrDefault(data,0L)==generation
                &&!PAYMENTS.startingCancelled(data)&&authority.validSource()
                &&java.util.Objects.equals(gunId,gun.getGunId(weapon))&&authority.validSource()
                &&GENERATIONS.getOrDefault(data,0L)==generation&&!PAYMENTS.startingCancelled(data);
        boolean[] nativeInvoked={false};
        boolean started;
        try {
            started = PAYMENTS.begin(data, weapon,
                    human.level().getGameTime() + GunCombatProfile.reloadHardLimit(human, weapon, recoveryDelay),
                    validStart,
                    () -> HumanAmmo.canReload(human, weapon)&&validStart.getAsBoolean()
                            &&gun.getCurrentAmmoCount(weapon)==loaded&&gun.hasBulletInBarrel(weapon)==chambered,
                    () -> {
                        nativeInvoked[0]=true;
                        return inventoryFeed || gun.startReload(data, weapon, human);
                    },
                    () -> authority.pay(validStart),
                    () -> {
                        // A failed callback must not restore snapshots over an
                        // owner edit, transferred gun, replacement inventory or session.
                        if(nativeInvoked[0] && validStart.getAsBoolean()) {
                            gun.setCurrentAmmoCount(weapon, loaded);
                            if(validStart.getAsBoolean()) gun.setBulletInBarrel(weapon, chambered);
                        }
                    });
        } catch(RuntimeException|LinkageError failure) {
            HumanGunner.LOGGER.warn("TaCZ NPC reload callback failed; no stale weapon will be restored",failure);
            return false;
        }
        if (!started) return false;
        if (inventoryFeed) INVENTORY_FEED_END.put(data,(long)feedDelay);
        GUN_IDS.put(data,gunId);
        Clock clock=new Clock(human,authority,data);
        CLOCKS.put(data,clock);
        OBSERVATIONS.put(human,new java.lang.ref.WeakReference<>(clock));
        {
            // Gun data cooldown is an endpoint, not an extra delay after feeding.
            double visualSeconds = reload.getCooldown() == null ? 0
                    : empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime();
            if (reload.getType() == FeedType.MANUAL) {
                double feedSeconds = reload.getFeed() == null ? 0
                        : empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime();
                visualSeconds += Math.max(0, missing - 1) * feedSeconds;
            }
            int visualTicks = inventoryFeed ? feedDelay
                    : (int) Math.max(1, Math.min(72000, Math.ceil(visualSeconds * 20)));
            double visualFeed = reload.getFeed() == null ? 0
                    : empty ? reload.getFeed().getEmptyTime() : reload.getFeed().getTacticalTime();
            double visualCooldown = reload.getCooldown() == null ? visualFeed
                    : empty ? reload.getCooldown().getEmptyTime() : reload.getCooldown().getTacticalTime();
            int visualTail = inventoryFeed ? 0
                    : (int) Math.max(0, Math.min(72000, Math.ceil((visualCooldown - visualFeed) * 20)));
            int visualFeedTicks = (int) Math.max(0, Math.min(72000, Math.ceil(visualFeed * 20)));
            double rate=GunCombatProfile.reloadWork(human,weapon);
            clock.rate=rate;
            TaczReloadVisualTimeline.begin(human, gunId.toString(),
                    (int)Math.ceil(visualTicks/rate),(int)Math.ceil(visualTail/rate),(int)Math.ceil(visualFeedTicks/rate));
            TaczReloadVisualTimeline.work(human,0,rate);
            if(!authority.validIdentity(true)||!validCycle(human,data,weapon,clock)) { finish(human,data);return false; }
            if(GunCombatProfile.silent(human,weapon)) TaczReloadVisualTimeline.clear(human);
            VISUAL_OWNERS.put(data, new java.lang.ref.WeakReference<>(human));
        }
        return started;
    }

    public static ReloadState tick(AbstractGunItem gun, ShooterDataHolder data,
                                   ItemStack weapon, LivingEntity shooter) {
        if (!(shooter instanceof Human human)) return gun.tickReload(data, weapon, shooter);
        if (!TICKING.add(data)) {
            ReloadState pending=new ReloadState();
            pending.setStateType(data.reloadStateType);
            pending.setCountDown(1L);
            return pending;
        }
        Clock clock=CLOCKS.get(data);
        try {
            if (!validCycle(human,data,weapon,clock)) { finish(human,data);return new ReloadState(); }
            Long feedEnd=INVENTORY_FEED_END.get(data);
            if(feedEnd!=null) {
                ReloadState state=new ReloadState();
                double remaining=feedEnd-clock.work;
                if(remaining>1e-8) {
                    state.setStateType(data.reloadStateType);
                    state.setCountDown((long)Math.ceil(remaining*50));
                    recordProgress(clock,state);
                    updateVisual(human,weapon,state.getCountDown(),true);
                    return state;
                }
                var index=TimelessAPI.getCommonGunIndex(gun.getGunId(weapon)).orElse(null);
                if(index!=null && validCycle(human,data,weapon,clock)) {
                    // Claim before native setters/capability callbacks. A partial
                    // external commit is never retried using the same payment.
                    PAYMENTS.complete(data,weapon,human.level().getGameTime(),
                            () -> validBinding(human,data,weapon,clock),
                            () -> TaczNpcMagazine.fill(weapon,index));
                }
                finish(human,data);
                return state;
            }
            ReloadState state=gun.tickReload(data,weapon,shooter);
            if(!validCycle(human,data,weapon,clock)) { finish(human,data);return new ReloadState(); }
            recordProgress(clock,state);
            if(state.getStateType().isReloading())
                updateVisual(human,weapon,state.getCountDown(),state.getStateType().isReloadFinishing());
            if(human.getData()!=null&&weapon!=human.getMainHandItem()) human.getData().markInventoryDirty();
            if(!state.getStateType().isReloading()) finish(human,data);
            return state;
        } catch(RuntimeException|LinkageError failure) {
            finish(human,data);
            HumanGunner.LOGGER.warn("TaCZ NPC reload tick failed; paid transaction cancelled without a snapshot restore",failure);
            return new ReloadState();
        } finally { TICKING.remove(data); }
    }

    private static boolean validCycle(Human human,ShooterDataHolder data,ItemStack weapon,Clock clock) {
        return PAYMENTS.authorized(data,weapon,human.level().getGameTime())&&validBinding(human,data,weapon,clock);
    }

    private static boolean validBinding(Human human,ShooterDataHolder data,ItemStack weapon,Clock clock) {
        return clock!=null&&CLOCKS.get(data)==clock&&sameGun(data,weapon)
                &&ownsReloadWeapon(human,data,weapon)&&CLOCKS.get(data)==clock;
    }

    public static boolean isPaidReloading(Human human, ShooterDataHolder data) {
        ItemStack weapon = PAYMENTS.weapon(data, human.level().getGameTime());
        boolean active = data.reloadTimestamp != -1L && data.reloadStateType.isReloading()
                && weapon != null && sameGun(data, weapon) && ownsReloadWeapon(human,data,weapon);
        // Queries must not cancel native state; cleanup belongs to tick/cancel.
        return active;
    }

    public static boolean isReloadingWeapon(Human human, ShooterDataHolder data, ItemStack weapon) {
        return PAYMENTS.weapon(data, human.level().getGameTime()) == weapon && isPaidReloading(human, data);
    }

    /** Called before native reload ticks, including while its combat Goal is stopped. */
    public static boolean prepareNativeTick(Human human, ShooterDataHolder data) {
        if (data.reloadTimestamp == -1L) {
            end(data);
            return true;
        }
        ItemStack weapon = PAYMENTS.weapon(data, human.level().getGameTime());
        if (!human.isAlive() || human.isRemoved() || human.isNoAi() || HumanInventorySession.isEditing(human)
                || weapon == null || !validCycle(human,data,weapon,CLOCKS.get(data))) {
            finish(human, data);
            return false;
        }
        // The dynamic main-hand supplier would yield a sword during close
        // defense. Bind native reload scripts only for this tick's call.
        data.currentGunItem = () -> weapon;
        Clock clock=CLOCKS.get(data);
        long wall=System.currentTimeMillis(), game=human.level().getGameTime();
        long ticks=Math.max(0,game-clock.gameTick);
        // Native Lua reads elapsed real milliseconds. Translate only its reload
        // origin so pauses/low TPS cannot finish a silent reload without AI ticks.
        double rate=GunCombatProfile.reloadWork(human,weapon);
        if(!validCycle(human,data,weapon,clock)) { finish(human,data);return false; }
        double work=ticks*rate;
        double virtualMillis=work*50+clock.fraction;
        long whole=(long)Math.floor(virtualMillis);clock.fraction=virtualMillis-whole;
        data.reloadTimestamp+=wall-clock.wallMillis-whole;
        clock.wallMillis=wall;clock.gameTick=game;clock.work+=work;
        clock.rate=rate;clock.sampledTick=game;
        TaczReloadVisualTimeline.work(human,clock.work,rate);
        return true;
    }

    private static void updateVisual(Human human,ItemStack weapon,long millis,boolean finishing) {
        if(GunCombatProfile.silent(human,weapon)) TaczReloadVisualTimeline.clear(human);
        else TaczReloadVisualTimeline.update(human,(long)Math.ceil(millis/GunCombatProfile.reloadWork(human,weapon)),finishing);
    }

    private static void recordProgress(Clock clock, ReloadState state) {
        clock.phase=state.getStateType().name();
        clock.phaseRemainingMillis=state.getCountDown();
        clock.sampledTick=clock.gameTick;
    }

    /** No operator lookup, native callbacks, handle resolution or ledger expiry during observation. */
    public static java.util.List<ActionProgressSnapshot> progress(Human human) {
        var reference=OBSERVATIONS.get(human);
        Clock clock=reference==null?null:reference.get();
        if(human.level().isClientSide || clock==null) return java.util.List.of();
        ShooterDataHolder data=clock.holder.get();ItemStack actual=clock.actual.get();
        var authority=HumanBehaviorControl.peekActions(human);
        String location=ActionProgressSnapshot.location(human,actual);
        boolean valid=data!=null && CLOCKS.get(data)==clock && human.getData()==clock.inventory
                && authority!=null && authority.session()==clock.session
                && actual!=null && PAYMENTS.peekWeapon(data,human.level().getGameTime())==actual
                && !location.equals("missing") && !location.equals("ambiguous")
                && human.isAlive() && !human.isRemoved() && !human.isNoAi() && !HumanInventorySession.isEditing(human);
        Long full=data==null?null:INVENTORY_FEED_END.get(data);
        double total=full==null?-1:full, rate=Double.isFinite(clock.rate)&&clock.rate>0?clock.rate:-1;
        double work=Double.isFinite(clock.work)&&clock.work>=0?clock.work:-1;
        long remaining=!valid?-1:full!=null?ActionProgressSnapshot.remaining(work,total,rate)
                :clock.phaseRemainingMillis<0||rate<=0?-1:(long)Math.ceil(clock.phaseRemainingMillis/(50*rate));
        String detail=full==null?"原生阶段余时；脚本整轮总量未知，不计算整体百分比；原枪 ":"原生虚拟弹匣完整计划；原枪 ";
        return java.util.List.of(new ActionProgressSnapshot("gun_reload",ActionProgressSnapshot.item(actual),location,
                valid?ActionProgressSnapshot.clip(clock.phase,32):"invalid",clock.sampledTick,work,total,rate,
                Math.max(0,clock.sampledTick-clock.startedTick),remaining,detail+clock.weapon.identity()));
    }

    private static void forgetObservation(ShooterDataHolder data) {
        Clock clock=CLOCKS.get(data);
        Human human=clock==null?null:clock.human.get();
        var reference=human==null?null:OBSERVATIONS.get(human);
        if(reference!=null&&reference.get()==clock) OBSERVATIONS.remove(human);
    }

    private static boolean sameGun(ShooterDataHolder data, ItemStack weapon) {
        return weapon.getItem() instanceof AbstractGunItem gun
                && java.util.Objects.equals(GUN_IDS.get(data), gun.getGunId(weapon));
    }

    private static boolean ownsReloadWeapon(Human human,ShooterDataHolder data,ItemStack weapon) {
        Clock clock=CLOCKS.get(data);
        return weapon!=null&&!weapon.isEmpty()&&clock!=null&&human.getData()==clock.inventory
                &&GunReloadAuthority.validIdentity(human,weapon,clock.weapon,clock.session);
    }

    private static void finish(Human human, ShooterDataHolder data) {
        TaczReloadVisualTimeline.clear(human);
        end(data);
        restoreHeldBinding(human, data);
        if (human.getData() != null) human.getData().markInventoryDirty();
    }

    /** Other native operations must see the real held item, not a stowed gun. */
    public static void restoreHeldBinding(Human human, ShooterDataHolder data) {
        // A never-drawn operator must remain unbound until the real draw API.
        if (data.currentGunItem != null) data.currentGunItem = human::getMainHandItem;
    }

    public static void end(ShooterDataHolder data) {
        forgetObservation(data);
        GENERATIONS.put(data,GENERATIONS.getOrDefault(data,0L)+1);
        clearVisual(data);
        PAYMENTS.end(data);
        INVENTORY_FEED_END.remove(data);
        CLOCKS.remove(data);
        GUN_IDS.remove(data);
        data.reloadTimestamp = -1L;
        data.reloadStateType = ReloadState.StateType.NOT_RELOADING;
    }

    public static void forgetFinished(ShooterDataHolder data) {
        if (!data.reloadStateType.isReloading() || data.reloadTimestamp == -1L) {
            forgetObservation(data);
            clearVisual(data);
            PAYMENTS.end(data);
            INVENTORY_FEED_END.remove(data);
            CLOCKS.remove(data);
            GUN_IDS.remove(data);
        }
    }

    private static void clearVisual(ShooterDataHolder data) {
        var reference = VISUAL_OWNERS.remove(data);
        Human human = reference == null ? null : reference.get();
        if (human != null) TaczReloadVisualTimeline.clear(human);
    }
}
