package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import dev.felix.hostilehumans.core.ActionCoordinator;
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
    private HumanArrowCombat() {}

    private static ArrowAttackCadence cadence(Human human) {
        return CADENCE.computeIfAbsent(human, ignored -> new ArrowAttackCadence());
    }
    public static void configureBowInterval(Human human, int interval) {
        if (!human.level().isClientSide) INTERVALS.put(human, interval);
    }
    public static boolean canDrawBow(Human human) {
        return !human.level().isClientSide && cadence(human).canDrawBow(human.tickCount);
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
    public static void beginCrossbowLoad(Human human, ItemStack weapon) {
        // A new unloaded cycle cannot reuse the deadline of a cancelled prior volley.
        if (!human.level().isClientSide && !CrossbowItem.isCharged(weapon)
                && cadence(human).hasLoadedCrossbow(weapon)) cadence(human).crossbowShot();
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
        ItemStack weapon = shotWeapon(human);
        if (weapon.getItem() instanceof CrossbowItem) return crossbowReady(human, weapon);
        return !(weapon.getItem() instanceof net.minecraft.world.item.BowItem) || cadence(human).canFireBow(human.tickCount);
    }

    public static boolean canShoot(Human human, LivingEntity target) {
        return !human.level().isClientSide && human.isAlive()
                && !human.humanGunner$shieldDefense().reservesRangedUse()
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
                || arrow.getPersistentData().getBoolean(HumanGunner.NPC_CROSSBOW_PROJECTILE)
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
            arrow.getPersistentData().putBoolean(HumanGunner.NPC_CROSSBOW_PROJECTILE, true);
            double dx = target.getX() - human.getX(), dz = target.getZ() - human.getZ();
            double dy = target.getY(.3333333333333333) - arrow.getY();
            arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz, 1.6F,
                    14 - human.level().getDifficulty().getId() * 4);
            HumanGunner.applyFirstTickArrowBallistics(human, arrow, target);
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
        if (human.level().isClientSide || !FIRING.add(human)) return 0;
        ActionCoordinator actions = null;
        ActionCoordinator.Token token = null;
        int inserted = 0;
        boolean settled = false;
        try {
            actions = HumanBehaviorControl.actions(human);
            if (!RangedWeaponCustody.isBowOrCrossbow(weapon) || !canShoot(human, target)
                    || !shotStillValid(human, target, weapon)) { settled = true; return 0; }
            token = actions.request("arrow-volley", java.util.EnumSet.of(ActionCoordinator.Resource.ATTACK,
                    ActionCoordinator.Resource.INVENTORY), 75, false, 2);
            if (!actions.acquire(token)) { settled = true; return 0; }
            inserted = executeVolley(human, target, weapon, factory, actions, token);
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

    private static boolean shotStillValid(Human human, LivingEntity target, ItemStack weapon) {
        return !human.level().isClientSide && human.isAlive() && !human.isRemoved() && !human.isNoAi()
                && !HumanInventorySession.isEditing(human) && human.getData() != null
                && human.level().getDifficulty() != Difficulty.PEACEFUL
                && target != null && target.isAlive() && !target.isRemoved() && target.level() == human.level()
                && HumanPerception.canTarget(human, target) && HumanPerception.allowCombatLook(human, target)
                && HumanAttention.canAim(human, target)
                && !human.humanGunner$shieldDefense().reservesRangedUse()
                && !(human.isUsingItem() && SpartanEquipmentCompat.isShield(human.getUseItem()))
                && (weapon == human.getMainHandItem() || weapon == human.getOffhandItem());
    }

    private static int executeVolley(Human human, LivingEntity target, ItemStack weapon,
            java.util.function.Supplier<net.minecraft.world.entity.projectile.AbstractArrow> factory,
            ActionCoordinator actions, ActionCoordinator.Token token) {
        if (!actions.running(token) || !shotStillValid(human, target, weapon)) return 0;
        var profile = RangedCombatProfile.of(human, weapon);
        int total = 0;
        net.minecraft.nbt.CompoundTag firstArrow = null;
        int groups = profile.doubleArrow() ? 2 : 1;
        for (int group = 0; group < groups; group++) {
            if (!actions.running(token) || !shotStillValid(human, target, weapon)) break;
            var layout = ArrowLayout.capture(human);
            int available = HumanAmmo.arrowCount(human);
            float[] angles = ArrowVolleyPolicy.angles(profile.tripleArrow(),
                    (profile.tripleArrow() || weapon.getItem() instanceof CrossbowItem)
                            && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0, available);
            if (angles.length == 0) break; // Never gamble on the saving roll without an actual arrow.
            java.util.List<net.minecraft.world.entity.projectile.AbstractArrow> arrows = new java.util.ArrayList<>();
            for (float angle : angles) {
                var arrow = factory.get();
                if (arrow == null || arrow.isAddedToWorld()) return total;
                if (profile.doubleArrow()) {
                    if (firstArrow == null) firstArrow = arrow.saveWithoutId(new CompoundTag());
                    else {
                        java.util.UUID ownIdentity = arrow.getUUID();
                        arrow.load(firstArrow.copy());
                        arrow.setUUID(ownIdentity);
                    }
                }
                if (angle != 0) arrow.setDeltaMovement(arrow.getDeltaMovement().yRot((float) Math.toRadians(-angle)));
                arrow.setBaseDamage(arrow.getBaseDamage() * profile.damage());
                arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
                arrows.add(arrow);
            }
            int cost = ArrowVolleyPolicy.cost(profile.tripleArrow(), available);
            // Recheck after optional native factory hooks, before publishing any projectile.
            if (!actions.running(token) || !shotStillValid(human, target, weapon)
                    || !layout.matches(human) || HumanAmmo.arrowCount(human) < cost) break;
            boolean save = profile.savingChance() > 0 && human.getRandom().nextDouble() < profile.savingChance();
            // Reserve the real arrows first. A failed entity insertion refunds only our untouched slots.
            var payment = ArrowPayment.take(human, save ? 0 : cost);
            if (payment == null) break;
            int inserted = 0;
            var paidLayout = ArrowLayout.capture(human);
            try {
                for (var arrow : arrows) {
                    if (!actions.running(token) || !shotStillValid(human, target, weapon)
                            || !paidLayout.matches(human)) break;
                    if (human.level().addFreshEntity(arrow)) inserted++;
                }
            } finally {
                inserted = (int) arrows.stream().filter(net.minecraft.world.entity.Entity::isAddedToWorld).count();
                if (inserted == 0) payment.refund(human);
                total += inserted;
                if (inserted > 0) {
                    if (weapon.getItem() instanceof CrossbowItem) recordCrossbowShot(human);
                    else recordBowShot(human);
                }
            }
        }
        return total;
    }

    /** Optional factories and entity-join callbacks cannot silently edit the backing layout. */
    private record ArrowLayout(com.craftix.hostile_humans.entity.data.HumanData data,
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
                                com.craftix.hostile_humans.entity.data.HumanData data) {
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
