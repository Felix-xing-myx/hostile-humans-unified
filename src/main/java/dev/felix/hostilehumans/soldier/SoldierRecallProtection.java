package dev.felix.hostilehumans.soldier;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.behavior.HumanBehaviorControl;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.inventory.HumanInventorySession;
import dev.felix.hostilehumans.navigation.SafePositions;
import dev.felix.hostilehumans.perception.HumanPerception;
import dev.felix.hostilehumans.perception.HumanTurnControl;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A bounded lethal-damage transaction: protect briefly, load, validate, relocate, then pay exactly once. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID)
public final class SoldierRecallProtection {
    static final int MAX_PENDING = 64, MAX_ACTIVE = 16, NEW_TICKETS_PER_TICK = 4;
    static final int TOTAL_TIMEOUT = 200, LOAD_TIMEOUT = 100;
    static final float RECOVERY_RATIO = 0.25F;
    private static final String CHECKPOINT = HumanRuntime.MOD_ID + ":recall_rescue_pending";
    private static final TicketType<UUID> TICKET = TicketType.create("hostile_humans_unified_rescue", UUID::compareTo, LOAD_TIMEOUT + 5);
    private static final Map<MinecraftServer, LinkedHashMap<UUID, Pending>> PENDING = new WeakHashMap<>();
    private static final Set<Human> REPLAY = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final class Pending {
        final Human human;
        final UUID owner;
        final SoldierRecallAnchor anchor;
        final ItemStack payment;
        final DamageSource damage;
        final boolean noAi, invulnerable, noGravity;
        final long deadline;
        ServerLevel ticketLevel;
        long loadDeadline;
        Pending(Human human, UUID owner, SoldierRecallAnchor anchor, ItemStack payment, DamageSource damage,
                boolean noAi, boolean invulnerable, boolean noGravity) {
            this.human = human; this.owner = owner; this.anchor = anchor; this.payment = payment.copyWithCount(1);
            this.damage = damage; this.noAi = noAi; this.invulnerable = invulnerable; this.noGravity = noGravity;
            deadline = now(human.getServer()) + TOTAL_TIMEOUT;
        }
    }
    private SoldierRecallProtection() {}
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static Pending pending(Human human) {
        var pending = PENDING.get(human.getServer());
        Pending entry = pending == null ? null : pending.get(human.getUUID());
        return entry != null && entry.human == human ? entry : null;
    }
    public static boolean pendingRescue(Human human) { return !human.level().isClientSide && pending(human) != null; }

    /** Called before inventory totems and before all confirmed-death bookkeeping. Native hand totems retain vanilla priority. */
    public static boolean tryProtect(Human human, DamageSource damage) {
        if (REPLAY.contains(human) || human.level().isClientSide) return false;
        Pending old = pending(human);
        if (damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (old != null) detach(old);
            return false; // Administrative/void damage follows the same bypass rule as existing totems.
        }
        if (old != null) { human.setHealth(1); return true; }
        if (!human.hasOwner() || human.getOwnerUUID() == null || human.isRemoved() || human.getData() == null) return false;
        var entries = PENDING.computeIfAbsent(human.getServer(), ignored -> new LinkedHashMap<>());
        if (entries.size() >= MAX_PENDING) return false;
        for (int slot = 0; slot < human.getData().getInventoryItemsSize(); slot++) {
            ItemStack stack = human.getData().getInventoryItem(slot);
            var anchor = SoldierRecallAnchor.of(stack);
            if (anchor.isEmpty() || destination(human.getServer(), anchor.get()) == null) continue;
            HumanInventorySession.interruptForRelocation(human);
            var request = new Pending(human, human.getOwnerUUID(), anchor.get(), stack, damage,
                    human.isNoAi(), human.isInvulnerable(), human.isNoGravity());
            try { HumanBehaviorControl.cancel(human, "lethal recall rescue"); }
            catch (RuntimeException failure) { HumanRuntime.LOGGER.error("Cannot prepare recall rescue for {}", human.getUUID(), failure); return false; }
            human.stopUsingItem(); human.stopRiding(); human.getNavigation().stop();
            entries.put(human.getUUID(), request);
            checkpoint(request);
            freeze(human);
            return true;
        }
        if (entries.isEmpty()) PENDING.remove(human.getServer());
        return false;
    }
    private static ServerLevel destination(MinecraftServer server, SoldierRecallAnchor anchor) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, anchor.dimension()));
        BlockPos pos = anchor.position();
        return level != null && pos.getY() > level.getMinBuildHeight() && pos.getY() + 2 < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(pos) ? level : null;
    }
    private static void freeze(Human human) {
        human.setHealth(Math.max(1, human.getHealth()));
        human.setNoAi(true); human.setInvulnerable(true); human.setNoGravity(true);
        human.setDeltaMovement(Vec3.ZERO); human.fallDistance = 0;
    }
    private static int paymentSlot(Human human, ItemStack expected) {
        if (human.getData() == null) return -1;
        for (int i = 0; i < human.getData().getInventoryItemsSize(); i++) {
            ItemStack stack = human.getData().getInventoryItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, expected)) return i;
        }
        return -1;
    }
    private static Vec3 landing(Human human, ServerLevel target, BlockPos anchor) {
        // Center first, then bounded rings: no remote queries and no second navigation controller.
        for (int radius = 0; radius <= 2; radius++) for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos floor = SafePositions.nearFloor(target, anchor.getX() + dx, anchor.getZ() + dz, anchor.getY());
                if (floor == null) continue;
                Vec3 candidate = SafePositions.checkedPlacement(human, target,
                        new Vec3(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5));
                if (candidate != null) return candidate;
            }
        return null;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        var entries = PENDING.get(server);
        if (entries == null) return;
        int active = (int) entries.values().stream().filter(p -> p.ticketLevel != null).count(), activated = 0;
        for (Pending request : List.copyOf(entries.values())) {
            Human human = request.human;
            if (human.isRemoved()) { detach(request); continue; }
            ServerLevel target = destination(server, request.anchor);
            if (target == null || !request.owner.equals(human.getOwnerUUID()) || !human.hasOwner()
                    || paymentSlot(human, request.payment) < 0 || now(server) >= request.deadline
                    || request.ticketLevel != null && now(server) >= request.loadDeadline) { fail(request); continue; }
            freeze(human);
            if (request.ticketLevel == null) {
                if (active >= MAX_ACTIVE || activated >= NEW_TICKETS_PER_TICK) continue;
                target.getChunkSource().addRegionTicket(TICKET, new ChunkPos(request.anchor.position()), 2, human.getUUID());
                request.ticketLevel = target; request.loadDeadline = now(server) + LOAD_TIMEOUT;
                active++; activated++;
            }
            if (!target.hasChunk(request.anchor.position().getX() >> 4, request.anchor.position().getZ() >> 4)) continue;
            Vec3 point = landing(human, target, request.anchor.position());
            if (point == null) { fail(request); continue; }
            relocate(request, target, point);
        }
        if (entries.isEmpty() && PENDING.get(server) == entries) PENDING.remove(server);
    }
    private static void detach(Pending request) {
        Human human = request.human;
        var entries = PENDING.get(human.getServer());
        if (entries != null && entries.get(human.getUUID()) == request) {
            entries.remove(human.getUUID());
            if (entries.isEmpty()) PENDING.remove(human.getServer());
        }
        if (request.ticketLevel != null) {
            request.ticketLevel.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(request.anchor.position()), 2, human.getUUID());
            request.ticketLevel = null;
        }
        restoreFlags(request, human);
        human.getPersistentData().remove(CHECKPOINT);
    }
    private static void restoreFlags(Pending request, Human human) {
        human.setNoAi(request.noAi); human.setInvulnerable(request.invulnerable); human.setNoGravity(request.noGravity);
    }
    private static void fail(Pending request) {
        detach(request);
        Human human = request.human;
        if (human.isRemoved()) return;
        // Replay the original lethal boundary directly: do not deal a second enormous hit or wear armor twice.
        REPLAY.add(human);
        try { human.setHealth(0); human.die(request.damage); }
        finally { REPLAY.remove(human); }
    }
    private static void relocate(Pending request, ServerLevel level, Vec3 point) {
        Human human = request.human;
        detach(request); // New dimension copy must not inherit temporary immortality/checkpoints.
        HiredHumanRecall.cancelPending(human.getServer(), human.getUUID());
        human.setHealth(1);
        boolean moved;
        try {
            moved = human.teleportTo(level, point.x, point.y, point.z, Set.<RelativeMovement>of(), human.getYRot(), human.getXRot());
        } catch (RuntimeException failure) {
            HumanRuntime.LOGGER.error("Recall teleport failed for {}", human.getUUID(), failure);
            fail(request); return;
        }
        if (!moved) { fail(request); return; }
        Entity relocated = level.getEntity(human.getUUID());
        if (!(relocated instanceof Human survivor) || survivor.isRemoved()) {
            HumanRuntime.LOGGER.error("Native recall transfer returned success without the expected live UUID {}", human.getUUID());
            return;
        }
        int slot = paymentSlot(survivor, request.payment);
        if (slot >= 0) {
            ItemStack remainder = survivor.getData().getInventoryItem(slot).copy(); remainder.shrink(1);
            survivor.getData().setInventoryItem(slot, remainder);
        } else HumanRuntime.LOGGER.error("Recall succeeded but reserved charm disappeared during transfer for {}", survivor.getUUID());
        restoreFlags(request, survivor);
        survivor.setHealth(Math.max(1, survivor.getMaxHealth() * RECOVERY_RATIO));
        for (var effect : List.copyOf(survivor.getActiveEffects())) if (!effect.getEffect().isBeneficial()) survivor.removeEffect(effect.getEffect());
        survivor.clearFire(); survivor.setTicksFrozen(0); survivor.fallDistance = 0; survivor.setDeltaMovement(Vec3.ZERO);
        survivor.isFleeing = false; survivor.forgetSoldierTarget(); survivor.stopSeekingShore();
        HumanPerception.clear(survivor); HumanTurnControl.clear(survivor);
        GarrisonSettings.set(survivor, new GarrisonSettings(true, false, GarrisonSettings.player(survivor).radius()));
        SoldierOrder.set(survivor, SoldierOrder.GARRISON);
        CommandView.persist(survivor);
        level.playSound(null, survivor.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 1, 1);
        var owner = level.getServer().getPlayerList().getPlayer(request.owner);
        if (owner != null) owner.displayClientMessage(Component.translatable("recall_charm.saved", survivor.getDisplayName(), survivor.blockPosition().toShortString(), level.dimension().location().toString()), false);
    }
    private static void checkpoint(Pending request) {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putUUID("owner", request.owner);
        tag.put("anchor", request.anchor.write()); tag.put("payment", request.payment.save(new CompoundTag()));
        tag.putBoolean("no_ai", request.noAi); tag.putBoolean("invulnerable", request.invulnerable); tag.putBoolean("no_gravity", request.noGravity);
        tag.putString("damage", request.damage.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("minecraft:generic"));
        if (request.damage.getEntity() != null) tag.putUUID("attacker", request.damage.getEntity().getUUID());
        if (request.damage.getDirectEntity() != null) tag.putUUID("direct", request.damage.getDirectEntity().getUUID());
        request.human.getPersistentData().put(CHECKPOINT, tag);
    }
    private static Entity loaded(MinecraftServer server, CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tag.getUUID(key)); if (entity != null) return entity;
        }
        return null;
    }
    private static DamageSource restoredDamage(Human human, CompoundTag tag) {
        ResourceLocation name = ResourceLocation.tryParse(tag.getString("damage"));
        if (name == null) return human.damageSources().generic();
        var holder = human.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(ResourceKey.create(Registries.DAMAGE_TYPE, name));
        return holder.isPresent() ? new DamageSource(holder.get(), loaded(human.getServer(), tag, "direct"), loaded(human.getServer(), tag, "attacker"))
                : human.damageSources().generic();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide || pending(human) != null) return;
        CompoundTag tag = human.getPersistentData().getCompound(CHECKPOINT);
        if (tag.isEmpty()) return;
        human.setNoAi(tag.getBoolean("no_ai")); human.setInvulnerable(tag.getBoolean("invulnerable")); human.setNoGravity(tag.getBoolean("no_gravity"));
        human.getPersistentData().remove(CHECKPOINT);
        var anchor = SoldierRecallAnchor.read(tag.getCompound("anchor"));
        ItemStack payment = ItemStack.of(tag.getCompound("payment"));
        boolean valid = tag.getInt("version") == 1 && tag.hasUUID("owner") && anchor.isPresent()
                && payment.getItem() instanceof SoldierRecallCharmItem;
        // Defer an invalid restored transaction to the normal failure path after entity join.
        // Never silently convert corrupted rescue data into a free one-health resurrection.
        if (!valid) payment = ItemStack.EMPTY;
        var entries = PENDING.computeIfAbsent(human.getServer(), ignored -> new LinkedHashMap<>());
        Pending previous = entries.get(human.getUUID());
        if (previous != null) detach(previous);
        entries = PENDING.computeIfAbsent(human.getServer(), ignored -> new LinkedHashMap<>());
        var request = new Pending(human, tag.hasUUID("owner") ? tag.getUUID("owner") : new UUID(0, 0),
                anchor.orElseGet(() -> new SoldierRecallAnchor(human.level().dimension().location(), human.blockPosition())), payment, restoredDamage(human, tag),
                human.isNoAi(), human.isInvulnerable(), human.isNoGravity());
        entries.put(human.getUUID(), request); checkpoint(request); freeze(human);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void attack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) return;
        Pending request = pending(human);
        if (request == null) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) detach(request);
        else event.setCanceled(true);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof Human human) || human.level().isClientSide) return;
        Pending request = pending(human); if (request == null) return;
        CompoundTag checkpoint = human.getPersistentData().getCompound(CHECKPOINT).copy();
        detach(request);
        if (human.getRemovalReason() != Entity.RemovalReason.KILLED && human.getRemovalReason() != Entity.RemovalReason.DISCARDED)
            human.getPersistentData().put(CHECKPOINT, checkpoint);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var entries = PENDING.get(event.getServer()); if (entries == null) return;
        for (Pending request : List.copyOf(entries.values())) {
            CompoundTag saved = request.human.getPersistentData().getCompound(CHECKPOINT).copy();
            detach(request); request.human.getPersistentData().put(CHECKPOINT, saved);
        }
    }
}
