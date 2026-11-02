package dev.felix.hostilehumans.behavior.combat.ranged;

import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.behavior.combat.ranged.VolleyContinuation;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.behavior.combat.recovery.RecoverySupplies;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.compat.spartan.SpartanEquipmentCompat;
import dev.felix.hostilehumans.compat.spartan.SpartanRangedCompat;
import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.perception.HumanAttention;
import dev.felix.hostilehumans.perception.HumanPerception;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import dev.felix.hostilehumans.behavior.ActionCoordinator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Backpack arrows pay for a shot, never for a projectile query or interrupted draw. */
public final class HumanArrowCombat {
    private static final java.util.Set<Human> FIRING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final String NPC_LOAD = "hostile_humans_unified:NpcArrowLoad";
    private static final java.util.Map<Human, Integer> INTERVALS = new java.util.WeakHashMap<>();
    private static final java.util.Map<Human, ArrowAttackCadence> CADENCE = new java.util.WeakHashMap<>();
    static final int DOUBLE_ARROW_DELAY_TICKS = 4;
    static final int FOLLOWUP_MAXIMUM_WAIT_TICKS = 40;
    static final int FOLLOWUP_RETRY_TICKS = 2;
    private static final java.util.Map<Human, Followup> FOLLOWUPS = new java.util.WeakHashMap<>();
    private record Followup(java.lang.ref.WeakReference<LivingEntity> target,
                            java.lang.ref.WeakReference<ItemStack> weapon,
                            java.util.List<ProjectileSnapshot> projectiles,
                            net.minecraft.world.phys.Vec3 origin, VolleyContinuation timing, long session) { }
    /** Pre-spread, pre-profile snapshots preserve each native multishot projectile independently. */
    private record ProjectileSnapshot(net.minecraft.world.entity.EntityType<?> type, CompoundTag payload) {
        net.minecraft.world.entity.projectile.AbstractArrow recreate(Human human, LivingEntity target, net.minecraft.world.phys.Vec3 origin) {
            var created = type.create(human.level());
            if (!(created instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)) return null;
            java.util.UUID identity = arrow.getUUID();
            arrow.load(payload.copy());
            arrow.setUUID(identity); arrow.setOwner(human);
            arrow.setPos(arrow.position().add(human.position().subtract(origin)));
            // Keep native arrow state, not the first shot's old direction.
            // Re-aim only this unattached continuation; paid volley applies
            // multishot fan angles and faction damage once afterwards.
            if (target == null && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.ownsWeapon(human, human.getMainHandItem())) {
                ManualArrowBallistics.aim(human, arrow, (float) arrow.getDeltaMovement().length(), isCrossbowProjectile(arrow));
                return arrow;
            }
            return HumanRuntime.reaimArrowBallistics(human, arrow, target) ? arrow : null;
        }
    }
    private HumanArrowCombat() {}

    /** One bounded continuation: short interruptions defer it, identity/lifecycle changes cancel it. */
    public static void tickFollowup(Human human) {
        Followup shot = FOLLOWUPS.get(human);
        if (shot == null) return;
        LivingEntity target = shot.target().get();
        ItemStack weapon = shot.weapon().get();
        if (!followupIdentityValid(human, target, weapon, shot.session())) {
            shot.timing().cancel(); FOLLOWUPS.remove(human, shot); return;
        }
        long now = human.level().getGameTime();
        if (!shot.timing().canAttempt(now)) {
            if (shot.timing().terminal()) FOLLOWUPS.remove(human, shot);
            return;
        }
        // Do not spend arrows or instantiate projectiles while defence/recovery/geometry blocks firing.
        if (RecoverySupplies.hasActiveUse(human) || !hasFollowupShot(human, target, weapon)) {
            shot.timing().attempted(now, false); return;
        }
        int[] next = {0};
        boolean settled = false;
        try {
            int inserted = publishVolley(human, target, weapon, () -> next[0] < shot.projectiles().size()
                    ? shot.projectiles().get(next[0]++).recreate(human, target, shot.origin()) : null, true);
            // The pending record stays installed during callbacks, preventing a new draw/load cycle.
            shot.timing().attempted(now, inserted > 0);
            settled = true;
            if (inserted > 0 && RangedWeaponCustody.isCrossbowWeapon(weapon))
                human.playSound(net.minecraft.sounds.SoundEvents.CROSSBOW_SHOOT, 1.0F,
                        .95F + human.getRandom().nextFloat() * .10F);
        } finally {
            // A callback exception cannot be retried with an uncertain publication/payment result.
            if (!settled) shot.timing().cancel();
            if (shot.timing().terminal()) FOLLOWUPS.remove(human, shot);
        }
    }
    private static boolean followupIdentityValid(Human human, LivingEntity target, ItemStack weapon, long session) {
        var actions = HumanBehaviorControl.peekActions(human);
        boolean manual = target == null && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.ownsWeapon(human, weapon);
        return weapon != null && shotContextValid(human, target, weapon) && (manual || target == human.getTarget())
                && (manual || !human.isFleeing) && HumanAmmo.arrowCount(human) > 0 && HumanAmmo.hasArrows(human)
                && RangedCombatProfile.of(human, weapon).doubleArrow()
                && actions != null && actions.session() == session;
    }
    public static void clear(Human human) {
        Followup shot = FOLLOWUPS.remove(human);
        if (shot != null) shot.timing().cancel();
    }
    private static boolean hasFollowupShot(Human human, LivingEntity target, ItemStack weapon) {
        if (target == null && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.arrowIntent(human, weapon, true))
            return shotStillValid(human, target, weapon, true) && HumanAmmo.hasArrows(human);
        return shotStillValid(human, target, weapon, true) && target == human.getTarget()
                && HumanAmmo.arrowCount(human) > 0 && HumanAmmo.hasArrows(human)
                && human.distanceToSqr(target) <= RangedCombatProfile.of(human, weapon).maximumSqr()
                && RangedShotClearance.canShoot(human, target);
    }

    private static ArrowAttackCadence cadence(Human human) {
        return CADENCE.computeIfAbsent(human, ignored -> new ArrowAttackCadence());
    }
    public static void configureBowInterval(Human human, int interval) {
        if (!human.level().isClientSide) INTERVALS.put(human, interval);
    }
    public static boolean canDrawBow(Human human) {
        return !human.level().isClientSide && !FOLLOWUPS.containsKey(human) && cadence(human).canDrawBow(human.tickCount);
    }
    public static void recordBowShot(Human human) {
        if (!human.level().isClientSide) {
            cadence(human).bowInterval(INTERVALS.getOrDefault(human, 32));
            cadence(human).bowShot(human.tickCount,
                    RangedCombatProfile.of(human, human.getMainHandItem()).attackRate());
        }
    }
    public static void advanceBowDraw(Human human) {
        double rate = 3 * RangedCombatProfile.of(human, human.getUseItem()).attackRate();
        int amount = (int) Math.floor(human.tickCount * rate) - (int) Math.floor((human.tickCount - 1) * rate);
        human.advanceUseTime(Math.max(0, amount - 1)); // Vanilla already supplied one unit.
    }
    /** Both loading backends must honor false while a paid continuation is pending. */
    public static boolean beginCrossbowLoad(Human human, ItemStack weapon) {
        if (human.level().isClientSide || FOLLOWUPS.containsKey(human)) return false;
        // A new unloaded cycle cannot reuse the deadline of a cancelled prior volley.
        if (!CrossbowItem.isCharged(weapon)
                && cadence(human).hasLoadedCrossbow(weapon)) cadence(human).crossbowShot();
        return true;
    }
    public static void crossbowLoaded(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon) || cadence(human).hasLoadedCrossbow(weapon)) return;
        cadence(human).crossbowLoaded(weapon, human.tickCount,
                ArrowAttackCadence.CROSSBOW_DELAY_MIN + human.getRandom().nextInt(ArrowAttackCadence.CROSSBOW_DELAY_VARIANCE),
                SpartanRangedCompat.isHeavyCrossbow(weapon));
    }
    public static void crossbowCompletedBehindGuard(Human human, ItemStack weapon) {
        if (!human.level().isClientSide && CrossbowItem.isCharged(weapon))
            cadence(human).crossbowReadyNow(weapon, human.tickCount);
    }
    public static boolean crossbowReady(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon)) return false;
        // A player-provided charged weapon also receives the normal loaded wait.
        if (!cadence(human).hasLoadedCrossbow(weapon)) crossbowLoaded(human, weapon);
        return cadence(human).canFireCrossbow(weapon, human.tickCount);
    }
    public static boolean crossbowWaitFinished(Human human, ItemStack weapon) {
        if (human.level().isClientSide || !CrossbowItem.isCharged(weapon)) return false;
        if (!cadence(human).hasLoadedCrossbow(weapon)) crossbowLoaded(human, weapon);
        return cadence(human).crossbowWaitFinished(weapon, human.tickCount);
    }
    public static void recordCrossbowShot(Human human) {
        if (!human.level().isClientSide) cadence(human).crossbowShot();
    }

    private static ItemStack shotWeapon(Human human) {
        return RangedWeaponCustody.isBowOrCrossbow(human.getMainHandItem())
                ? human.getMainHandItem() : human.getOffhandItem();
    }
    private static boolean cadenceAllowsShot(Human human) {
        if (FOLLOWUPS.containsKey(human)) return false;
        ItemStack weapon = shotWeapon(human);
        if (weapon.getItem() instanceof CrossbowItem) return crossbowReady(human, weapon);
        return !(weapon.getItem() instanceof net.minecraft.world.item.BowItem) || cadence(human).canFireBow(human.tickCount);
    }

    public static boolean canShoot(Human human, LivingEntity target) {
        if (target == null && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.arrowIntent(human, shotWeapon(human), false))
            return shotContextValid(human, null, shotWeapon(human)) && HumanAmmo.hasArrows(human) && cadenceAllowsShot(human);
        return !human.level().isClientSide && human.isAlive()
                && !human.hhu$shieldDefense().reservesRangedUse()
                && !(human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem()))
                && human.level().getDifficulty() != Difficulty.PEACEFUL
                && target != null && target.isAlive() && !target.isRemoved()
                && HumanPerception.canTarget(human,target) && HumanPerception.allowCombatLook(human,target) && HumanAttention.canAim(human,target)
                && target.level() == human.level() && HumanAmmo.arrowCount(human) > 0 && HumanAmmo.hasArrows(human) && cadenceAllowsShot(human)
                && human.distanceToSqr(target) <= RangedCombatProfile.of(human, shotWeapon(human)).maximumSqr()
                && RangedShotClearance.canShoot(human, target);
    }

    /** Read the projectile's saved firing origin; the shooter may have switched weapons. */
    public static boolean isCrossbowProjectile(net.minecraft.world.entity.projectile.AbstractArrow arrow) {
        return arrow.shotFromCrossbow()
                || arrow.getPersistentData().getBoolean(HumanRuntime.NPC_CROSSBOW_PROJECTILE)
                || arrow.getPersistentData().getBoolean(SpartanRangedCompat.NPC_BOLT);
    }

    /** Native loading may shrink this temporary stack; it never touches the backpack. */
    public static ItemStack projectileFor(Human human) {
        return HumanAmmo.arrowCount(human) > 0 && HumanAmmo.hasArrows(human) ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
    }

    public static void markNpcLoad(ItemStack crossbow) {
        crossbow.getOrCreateTag().putBoolean(NPC_LOAD, true);
        // Only retain the charged action state. Unpaid temporary ammunition
        // must not survive equipment transfer, death drops or item swapping.
        crossbow.getTag().remove("ChargedProjectiles");
        crossbow.getTag().remove("Projectile");
    }

    /** Inventory transfer clears only an NPC's empty charged action marker. */
    public static void clearNpcLoad(ItemStack crossbow) {
        if (!crossbow.hasTag() || !crossbow.getTag().getBoolean(NPC_LOAD)) return;
        CrossbowItem.setCharged(crossbow, false);
        crossbow.getTag().remove("ChargedProjectiles");
        crossbow.getTag().remove("Projectile");
        crossbow.getTag().remove(NPC_LOAD);
    }

    /** Saved/looted charged ammunition is also an NPC arrow volley, not a free rocket. */
    public static boolean prepareCrossbowVolley(ItemStack crossbow) {
        if (!(crossbow.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(crossbow)) {
            return false;
        }
        ListTag arrows = new ListTag();
        int count = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow) > 0 ? 3 : 1;
        for (int i = 0; i < count; i++) arrows.add(new ItemStack(Items.ARROW).save(new CompoundTag()));
        crossbow.getOrCreateTag().put("ChargedProjectiles", arrows);
        return true;
    }
    /** Vanilla arrow factory preserves native arrow type, piercing and the existing NPC ballistics. */
    public static int fireNativeCrossbow(Human human, LivingEntity target, ItemStack weapon) {
        if (!(weapon.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(weapon)
                || SpartanRangedCompat.isHeavyCrossbow(weapon)) return 0;
        java.util.List<ItemStack> payload = new java.util.ArrayList<>();
        if (weapon.hasTag() && weapon.getTag().contains("ChargedProjectiles", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag charged = weapon.getTag().getList("ChargedProjectiles", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < charged.size(); i++) payload.add(ItemStack.of(charged.getCompound(i)));
        }
        if (payload.isEmpty()) payload.add(projectileFor(human));
        // -1 means unsupported native payload: leave it completely intact for the original backend.
        if (payload.stream().anyMatch(ammo -> !(ammo.getItem() instanceof net.minecraft.world.item.ArrowItem))) return -1;
        int[] next = {0};
        int count = fireVolley(human, target, weapon, () -> {
            ItemStack ammunition = payload.get(next[0] < payload.size() ? next[0]++ : 0).copy();
            var item = (net.minecraft.world.item.ArrowItem) ammunition.getItem();
            var arrow = item.createArrow(human.level(), ammunition, human);
            arrow.setShotFromCrossbow(true);
            int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
            if (piercing > 0) arrow.setPierceLevel((byte) Math.min(127, piercing));
            arrow.setCritArrow(true);
            arrow.getPersistentData().putBoolean(HumanRuntime.NPC_CROSSBOW_PROJECTILE, true);
            if (target == null && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.ownsWeapon(human, weapon)) {
                ManualArrowBallistics.aim(human, arrow, 2.7F, true);
                return arrow;
            }
            double dx = target.getX() - human.getX(), dz = target.getZ() - human.getZ();
            double dy = target.getY(.3333333333333333) - arrow.getY();
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz, 1.6F,
                    14 - human.level().getDifficulty().getId() * 4);
            HumanRuntime.applyFirstTickArrowBallistics(human, arrow, target);
            return arrow;
        });
        // Failed insertion retains the ready cycle; no ammo or duplicate payload escapes.
        if (count > 0) { CrossbowItem.setCharged(weapon, false); markNpcLoad(weapon); }
        return count;
    }

    /** Factory must return an unattached arrow with native weapon/enchantment ballistics applied.
     * This boundary owns a short ATTACK/INVENTORY lease; callers apply wear/sound once.
     * No backend may also insert or consume ammunition for this transaction. */
    public static int fireVolley(Human human, LivingEntity target, ItemStack weapon,
            java.util.function.Supplier<net.minecraft.world.entity.projectile.AbstractArrow> factory) {
        return publishVolley(human, target, weapon, factory, false);
    }
    private static int publishVolley(Human human, LivingEntity target, ItemStack weapon,
            java.util.function.Supplier<net.minecraft.world.entity.projectile.AbstractArrow> factory, boolean followup) {
        if (human.level().isClientSide || !FIRING.add(human)) return 0;
        ActionCoordinator actions = null;
        ActionCoordinator.Token token = null;
        int inserted = 0;
        boolean settled = false;
        try {
            actions = HumanBehaviorControl.actions(human);
            if (!RangedWeaponCustody.isBowOrCrossbow(weapon)
                    || !(followup ? hasFollowupShot(human, target, weapon) : canShoot(human, target))
                    || !shotStillValid(human, target, weapon, followup)) { settled = true; return 0; }
            token = actions.request("arrow-volley", java.util.EnumSet.of(ActionCoordinator.Resource.ATTACK,
                    ActionCoordinator.Resource.INVENTORY), 75, false, 2);
            if (!actions.acquire(token)) { settled = true; return 0; }
            inserted = executeVolley(human, target, weapon, factory, actions, token, followup);
            settled = true;
            return inserted;
        } finally {
            try {
                if (token != null) actions.finish(token, !settled ? ActionCoordinator.Status.FAILED
                        : inserted > 0 ? ActionCoordinator.Status.COMPLETED : ActionCoordinator.Status.CANCELLED,
                        !settled ? "arrow callback failed" : inserted > 0 ? "volley fired" : "volley unavailable");
            } finally { FIRING.remove(human); }
        }
    }

    private static boolean shotContextValid(Human human, LivingEntity target, ItemStack weapon) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi() && !human.isSleeping()
                && !HumanInventorySession.isEditing(human) && human.getData() != null
                && human.level().getDifficulty() != Difficulty.PEACEFUL
                && (target == null ? dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.ownsWeapon(human, weapon)
                    : target.isAlive() && !target.isRemoved() && target.level() == human.level() && HumanPerception.canTarget(human, target))
                && (weapon == human.getMainHandItem() || weapon == human.getOffhandItem());
    }

    /** Immediate release checks remain mandatory for both original shots and delayed continuations. */
    private static boolean shotStillValid(Human human, LivingEntity target, ItemStack weapon) {
        return shotStillValid(human, target, weapon, false);
    }
    private static boolean shotStillValid(Human human, LivingEntity target, ItemStack weapon, boolean followup) {
        if (target == null) return shotContextValid(human, null, weapon)
                && dev.felix.hostilehumans.behavior.combat.HumanCombatAuthority.arrowIntent(human, weapon, followup);
        return shotContextValid(human, target, weapon) && HumanPerception.allowCombatLook(human, target)
                && HumanAttention.canAim(human, target)
                && !human.hhu$shieldDefense().reservesRangedUse()
                && !(human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem()))
                && (!followup || target == human.getTarget() && !human.isFleeing
                && !RecoverySupplies.hasActiveUse(human) && RangedCombatProfile.of(human, weapon).doubleArrow());
    }

    private static int executeVolley(Human human, LivingEntity target, ItemStack weapon,
            java.util.function.Supplier<net.minecraft.world.entity.projectile.AbstractArrow> factory,
            ActionCoordinator actions, ActionCoordinator.Token token, boolean followup) {
        if (!actions.running(token) || !shotStillValid(human, target, weapon, followup)) return 0;
        var profile = RangedCombatProfile.of(human, weapon);
        var layout = ArrowLayout.capture(human);
        int available = HumanAmmo.arrowCount(human);
        float[] angles = ArrowVolleyPolicy.angles(profile.tripleArrow(),
                (profile.tripleArrow() || RangedWeaponCustody.isCrossbowWeapon(weapon))
                        && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0, available);
        if (angles.length == 0) return 0;
        java.util.List<net.minecraft.world.entity.projectile.AbstractArrow> arrows = new java.util.ArrayList<>();
        java.util.List<ProjectileSnapshot> followupProjectiles = new java.util.ArrayList<>();
        for (float angle : angles) {
            var arrow = factory.get();
            if (arrow == null || arrow.isAddedToWorld()) return 0;
            if (profile.doubleArrow() && !followup) {
                followupProjectiles.add(new ProjectileSnapshot(arrow.getType(), arrow.saveWithoutId(new CompoundTag())));
            }
            if (angle != 0) arrow.setDeltaMovement(arrow.getDeltaMovement().yRot((float)Math.toRadians(-angle)));
            arrow.setBaseDamage(arrow.getBaseDamage() * profile.damage());
            arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            arrows.add(arrow);
        }
        int cost = ArrowVolleyPolicy.cost(profile.tripleArrow(), available);
        // Recheck after optional native factory hooks, before publishing any projectile.
        if (!actions.running(token) || !shotStillValid(human, target, weapon, followup)
                || !layout.matches(human) || HumanAmmo.arrowCount(human) < cost) return 0;
        boolean save = profile.savingChance() > 0 && human.getRandom().nextDouble() < profile.savingChance();
        var payment = ArrowPayment.take(human, save ? 0 : cost);
        if (payment == null) return 0;
        int inserted = 0;
        var paidLayout = ArrowLayout.capture(human);
        try {
            for (var arrow : arrows) {
                if (!actions.running(token) || !shotStillValid(human, target, weapon, followup)
                        || !paidLayout.matches(human)) break;
                if (human.level().addFreshEntity(arrow)) inserted++;
            }
        } finally {
            inserted = (int)arrows.stream().filter(net.minecraft.world.entity.Entity::isAddedToWorld).count();
            if (inserted == 0) payment.refund(human);
            if (inserted > 0 && !followup) {
                if (weapon.getItem() instanceof CrossbowItem) recordCrossbowShot(human);
                else recordBowShot(human);
            }
        }
        if (inserted > 0 && !followupProjectiles.isEmpty() && !followup)
            FOLLOWUPS.put(human, new Followup(new java.lang.ref.WeakReference<>(target),
                    new java.lang.ref.WeakReference<>(weapon), java.util.List.copyOf(followupProjectiles), human.position(),
                    new VolleyContinuation(human.level().getGameTime() + DOUBLE_ARROW_DELAY_TICKS,
                            FOLLOWUP_MAXIMUM_WAIT_TICKS, FOLLOWUP_RETRY_TICKS), actions.session()));
        return inserted;
    }

    /** Optional factories and entity-join callbacks cannot silently edit the backing layout. */
    private record ArrowLayout(dev.felix.hostilehumans.entity.HumanData data,
                               java.util.List<ItemStack> references, java.util.List<ItemStack> copies) {
        static ArrowLayout capture(Human human) {
            var references = new java.util.ArrayList<ItemStack>();
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) references.add(human.getItemBySlot(slot));
            var data = human.getData();
            for (int i = 0; i < data.getInventoryItemsSize(); i++) references.add(data.getInventoryItem(i));
            return new ArrowLayout(data, references, references.stream().map(ItemStack::copy).toList());
        }
        boolean matches(Human human) {
            if (human.getData() != data || data.getInventoryItemsSize()
                    + net.minecraft.world.entity.EquipmentSlot.values().length != references.size()) return false;
            int index = 0;
            for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                ItemStack actual = human.getItemBySlot(slot);
                if (actual != references.get(index) || !ItemStack.matches(actual, copies.get(index++))) return false;
            }
            for (int i = 0; i < data.getInventoryItemsSize(); i++) {
                ItemStack actual = data.getInventoryItem(i);
                if (actual != references.get(index) || !ItemStack.matches(actual, copies.get(index++))) return false;
            }
            return true;
        }
    }

    /** Real inventory debit. Infinity and the saving roll never create arrows. */
    private record ArrowPayment(java.util.List<Integer> slots, java.util.List<ItemStack> before,
                                java.util.List<ItemStack> after, java.util.List<ItemStack> expectedAfter,
                                dev.felix.hostilehumans.entity.HumanData data) {
        static ArrowPayment take(Human human, int cost) {
            var slots = new java.util.ArrayList<Integer>();
            var before = new java.util.ArrayList<ItemStack>();
            var after = new java.util.ArrayList<ItemStack>();
            var expectedAfter = new java.util.ArrayList<ItemStack>();
            // Explicit global no-consumption configuration still requires a physical reserve.
            if (!UnifiedConfig.get().ammoConsumptionEnabled()) cost = 0;
            if (HumanAmmo.arrowCount(human) < cost) return null;
            var data = human.getData();
            if (data == null) return null;
            for (int i = 0; i < data.getInventoryItemsSize() && cost > 0; i++) {
                ItemStack stack = data.getInventoryItem(i);
                if (!HumanAmmo.isArrow(stack)) continue;
                slots.add(i); before.add(stack.copy());
                ItemStack remaining = stack.copy();
                int taken = Math.min(cost, remaining.getCount());
                remaining.shrink(taken); cost -= taken;
                if (remaining.isEmpty()) remaining = ItemStack.EMPTY;
                after.add(remaining); expectedAfter.add(remaining.copy()); data.setInventoryItem(i, remaining);
            }
            return new ArrowPayment(slots, before, after, expectedAfter, data);
        }
        void refund(Human human) {
            if (human.getData() != data) return;
            for (int i = 0; i < slots.size(); i++) {
                // An external callback may have edited the slot; never overwrite its new contents.
                ItemStack actual = data.getInventoryItem(slots.get(i));
                if (actual == after.get(i) && ItemStack.matches(actual, expectedAfter.get(i)))
                    data.setInventoryItem(slots.get(i), before.get(i));
            }
        }
    }
}
