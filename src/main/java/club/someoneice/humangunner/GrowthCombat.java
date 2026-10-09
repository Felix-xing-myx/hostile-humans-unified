package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Aggregate actual health loss. No target searches, per-hit histories or client calculations. */
public final class GrowthCombat {
    private static final String CONTRIBUTIONS = HumanGunner.MOD_ID + ":growth_contributions";
    private static final String DEATH = HumanGunner.MOD_ID + ":growth_death_candidate";
    private static final String SETTLED = HumanGunner.MOD_ID + ":growth_settled";
    private static final ThreadLocal<Deque<Hit>> HITS = new ThreadLocal<>();
    private static final java.util.Set<LivingEntity> CONFIRMED = new java.util.HashSet<>();
    private static final class Hit {
        final LivingEntity target;
        final Human soldier;
        final java.util.UUID owner;
        final double before;
        double nested;
        double healthLoss;
        boolean observedHealth;
        Deque<HealthWrite> healthChanges;
        Hit(LivingEntity target, DamageSource source) {
            this.target = target;
            before = target.getHealth();
            soldier = source.getEntity() instanceof Human human && formal(human)
                    && !HumanRelations.allied(human, target) ? human : null;
            owner = soldier == null ? null : soldier.getOwnerUUID();
        }
    }
    private static final class HealthWrite {
        final float before;
        double nested;
        HealthWrite(float before) { this.before = before; }
    }

    private GrowthCombat() { }
    private static boolean formal(Human human) { return human.hasOwner() && human.getOwnerUUID() != null; }

    /** Called at the stable LivingEntity.hurt boundary, including final death/totem processing. */
    public static void beginHit(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide) return;
        Deque<Hit> stack = HITS.get();
        if (stack == null) {
            if (!UnifiedConfig.get().growth().enabled() || !(source.getEntity() instanceof Human soldier)
                    || !formal(soldier) || HumanRelations.allied(soldier, target)) return;
            stack = new ArrayDeque<>();
            HITS.set(stack);
        }
        stack.push(new Hit(target, source));
    }

    public static void endHit(LivingEntity target) {
        if (target.level().isClientSide) return;
        Deque<Hit> stack = HITS.get();
        if (stack == null || stack.isEmpty()) { HITS.remove(); return; }
        Hit hit = stack.pop();
        if (hit.target != target) { stack.clear(); HITS.remove(); return; }
        double loss = hit.observedHealth ? hit.healthLoss
                : Math.max(0, Math.min(hit.before, hit.before - target.getHealth()) - hit.nested);
        // Reentrant damage to the same entity belongs to the nested source, not both sources.
        for (Hit parent : stack) {
            if (parent.target == target) { parent.nested += loss; break; }
        }
        if (hit.soldier != null && formal(hit.soldier) && hit.owner.equals(hit.soldier.getOwnerUUID()))
            record(target, hit.soldier, loss);
        if (target.getRemovalReason() == Entity.RemovalReason.KILLED) settle(target);
        if (stack.isEmpty()) HITS.remove();
    }

    /** Observe individual health writes so a totem or instant healing inside hurt cannot erase real damage. */
    public static void beforeHealthChange(LivingEntity target) {
        if (target.level().isClientSide) return;
        Deque<Hit> stack = HITS.get();
        if (stack == null) return;
        for (Hit hit : stack) if (hit.target == target) {
            if (hit.healthChanges == null) hit.healthChanges = new ArrayDeque<>();
            hit.healthChanges.push(new HealthWrite(target.getHealth()));
            return;
        }
    }

    public static void afterHealthChange(LivingEntity target) {
        if (target.level().isClientSide) return;
        Deque<Hit> stack = HITS.get();
        if (stack == null) return;
        for (Hit hit : stack) if (hit.target == target) {
            if (hit.healthChanges == null || hit.healthChanges.isEmpty()) return;
            HealthWrite write = hit.healthChanges.pop();
            double loss = Math.max(0, Math.min(write.before, write.before - target.getHealth()));
            if (!hit.healthChanges.isEmpty()) hit.healthChanges.peek().nested += loss;
            hit.observedHealth = true;
            hit.healthLoss += Math.max(0, loss - write.nested);
            return;
        }
    }

    static void record(LivingEntity target, Human soldier, double damage) {
        GrowthConfig config = UnifiedConfig.get().growth();
        MinecraftServer server = target.getServer();
        if (!config.enabled() || server == null || !Double.isFinite(damage) || damage <= 0) return;
        long epoch = GrowthRewards.get(server).epoch();
        CompoundTag root = target.getPersistentData().getCompound(CONTRIBUTIONS);
        if (!root.contains("epoch") || root.getLong("epoch") != epoch) {
            root = new CompoundTag();
            root.putLong("epoch", epoch);
            root.putDouble("damage_unit", config.damageUnit());
            root.putDouble("reward_unit", config.rewardUnit());
            root.putDouble("base_cap", config.baseRewardCap());
            root.putDouble("bonus_threshold", config.bonusDamageThreshold());
            root.putDouble("bonus_reward", config.bonusReward());
            target.getPersistentData().put(CONTRIBUTIONS, root);
        }
        String key = soldier.getUUID().toString();
        CompoundTag entry = root.getCompound(key);
        if (!entry.hasUUID("owner") || !soldier.getOwnerUUID().equals(entry.getUUID("owner"))) {
            entry = new CompoundTag();
            entry.putUUID("owner", soldier.getOwnerUUID());
            root.put(key, entry);
        }
        // Store the strict-threshold flag separately so exactly 300 never earns the bonus.
        double damageUnit = root.getDouble("damage_unit"), rewardUnit = root.getDouble("reward_unit");
        double baseCap = root.getDouble("base_cap"), bonusThreshold = root.getDouble("bonus_threshold");
        if (!Double.isFinite(damageUnit) || damageUnit <= 0 || !Double.isFinite(rewardUnit) || rewardUnit < 0) return;
        double total = entry.getDouble("damage") + damage;
        double baseLimit = rewardUnit <= 0 ? 0 : Math.ceil(baseCap / rewardUnit) * damageUnit;
        if (entry.getBoolean("bonus") && entry.getDouble("damage") >= baseLimit) return;
        double limit = Math.max(baseLimit, bonusThreshold);
        if (total > bonusThreshold) entry.putBoolean("bonus", true);
        entry.putDouble("damage", Math.min(limit, total));
        // Freeze the eligible award at damage time; later config edits never retroactively add rewards.
        entry.putDouble("reward", GrowthProgression.reward(total, entry.getBoolean("bonus"),
                damageUnit, rewardUnit, baseCap, root.getDouble("bonus_reward")));
    }

    static void onDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide || event.isCanceled()) return;
        // A first lethal hit is recorded when hurt returns, after the death event.
        if (UnifiedConfig.get().growth().enabled() && (target.getPersistentData().contains(CONTRIBUTIONS)
                || event.getSource().getEntity() instanceof Human soldier && formal(soldier)))
            target.getPersistentData().putBoolean(DEATH, true);
    }

    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof LivingEntity target)) return;
        if (target.getRemovalReason() == Entity.RemovalReason.KILLED && target.getPersistentData().getBoolean(DEATH))
            CONFIRMED.add(target);
        else if (target.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            // Forge respawn discards the old player before PlayerEvent.Clone.
            if (target instanceof net.minecraft.server.level.ServerPlayer && target.getPersistentData().getBoolean(DEATH)) return;
            target.getPersistentData().remove(DEATH);
            target.getPersistentData().remove(CONTRIBUTIONS);
        }
    }

    static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || event.getEntity().level().isClientSide) return;
        CONFIRMED.add(event.getOriginal()); // Defer until END so an immediate respawn cannot lose the final hit.
        event.getEntity().getPersistentData().remove(DEATH);
        event.getEntity().getPersistentData().remove(CONTRIBUTIONS);
        event.getEntity().getPersistentData().remove(SETTLED);
    }

    static void settle(LivingEntity target) {
        CompoundTag data = target.getPersistentData();
        MinecraftServer server = target.getServer();
        if (server == null || !data.getBoolean(DEATH) || data.getBoolean(SETTLED)) return;
        GrowthRewards store = GrowthRewards.get(server);
        CompoundTag contributions = data.getCompound(CONTRIBUTIONS);
        data.putBoolean(SETTLED, true);
        data.remove(DEATH);
        data.remove(CONTRIBUTIONS);
        if (!UnifiedConfig.get().growth().enabled() || contributions.getLong("epoch") != store.epoch()) return;
        RecruitmentLedger ledger = RecruitmentLedger.get(server);
        for (String key : contributions.getAllKeys()) {
            if (!contributions.contains(key, Tag.TAG_COMPOUND)) continue;
            CompoundTag entry = contributions.getCompound(key);
            try {
                java.util.UUID soldier = java.util.UUID.fromString(key);
                if (entry.hasUUID("owner") && ledger.isOwnedRecord(soldier, entry.getUUID("owner")))
                    store.enqueue(soldier, entry.getUUID("owner"), entry.getDouble("reward"));
            } catch (IllegalArgumentException malformed) { /* Ignore corrupt entity contributions. */ }
        }
    }

    static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        HITS.remove(); // Never retain an abandoned frame if another mod catches a hurt exception.
        GrowthRewards.get(event.getServer()); // Invalidate unfinished fights on the enabled -> disabled transition.
        var iterator = CONFIRMED.iterator();
        while (iterator.hasNext()) {
            LivingEntity target = iterator.next();
            if (target.getServer() == event.getServer()) { settle(target); iterator.remove(); }
        }
        HumanCommandNetwork.tickServer(event.getServer());
    }

    static void onStopped(ServerStoppedEvent event) {
        CONFIRMED.removeIf(target -> target.getServer() == event.getServer());
        HITS.remove();
    }
}
