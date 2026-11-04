package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.config.UnifiedConfig;
import dev.felix.hostilehumans.entity.TierThreeHuman;
import dev.felix.hostilehumans.perception.HumanPerception;

import dev.felix.hostilehumans.entity.HumanTier;
import dev.felix.hostilehumans.faction.StandingIncidents;
import dev.felix.hostilehumans.faction.StandingPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Political standing and short-term hostility. Never assigns a combat target or grants visual confirmation. */
public final class StandingEffects {
    private static final Map<Human, StandingIncidents> INCIDENTS = new WeakHashMap<>();
    private record Reward(String faction, int amount) {}
    private record PendingDeath(long tick, Attribution actor, String faction, List<Reward> rewards, boolean boss, boolean owned, boolean rewardBlocked) {}
    private static final Map<Human, PendingDeath> DEATHS = new WeakHashMap<>();
    private StandingEffects() {}
    /** Only incident deadlines are captured. Standing, identity bands and configuration remain live. */
    public record OriginPolitics(String faction, boolean roamer, Map<UUID, Long> retaliationUntil) {
        public OriginPolitics {
            Map<UUID, Long> bounded = new LinkedHashMap<>();
            retaliationUntil.entrySet().stream().limit(16).forEach(entry -> bounded.put(entry.getKey(), entry.getValue()));
            retaliationUntil = Map.copyOf(bounded);
        }
        public net.minecraft.nbt.CompoundTag save() {
            var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("faction", faction); tag.putBoolean("roamer", roamer);
            var rows = new net.minecraft.nbt.ListTag();
            for (var entry : retaliationUntil.entrySet()) {
                var row = new net.minecraft.nbt.CompoundTag(); row.putUUID("player", entry.getKey()); row.putLong("until", entry.getValue()); rows.add(row);
            }
            tag.put("incidents", rows); return tag;
        }
        public static OriginPolitics load(net.minecraft.nbt.CompoundTag tag) {
            Map<UUID, Long> incidents = new LinkedHashMap<>(); var rows = tag.getList("incidents", 10);
            for (int i = 0; i < Math.min(16, rows.size()); i++) {
                var row = rows.getCompound(i); if (row.hasUUID("player")) incidents.put(row.getUUID("player"), row.getLong("until"));
            }
            return new OriginPolitics(tag.getString("faction"), tag.getBoolean("roamer"), incidents);
        }
    }
    public static OriginPolitics originPolitics(Human human) {
        var memory = INCIDENTS.get(human);
        return new OriginPolitics(HumanFactions.id(human), human.getTier() == HumanTier.ROAMER,
                memory == null ? Map.of() : memory.retaliationExpiries(human.level().getGameTime()));
    }
    static boolean neutralTo(OriginPolitics source, Player player, long now) {
        return !source.roamer() && source.retaliationUntil().getOrDefault(player.getUUID(), Long.MIN_VALUE) <= now
                && !StandingPolicy.hostile(effectiveStanding(source.faction(), source.roamer(), player));
    }
    static int trueStanding(Human human, Player player) {
        return trueStanding(HumanFactions.id(human), human.getTier() == HumanTier.ROAMER, player);
    }
    private static int trueStanding(String faction, boolean roamer, Player player) {
        if (roamer) return -500;
        if (!UnifiedConfig.get().featureEnabled("standing", true)) return 100;
        if (player.getServer() == null || FactionDefinitions.find(faction) == null) return 100;
        return FactionStandingData.get(player.getServer()).standing(player.getUUID(), faction);
    }
    static int effectiveStanding(Human human, Player player) {
        return effectiveStanding(HumanFactions.id(human), human.getTier() == HumanTier.ROAMER, player);
    }
    static int effectiveStanding(String faction, boolean roamer, Player player) {
        int standing = trueStanding(faction, roamer, player);
        if (!UnifiedConfig.get().featureEnabled("standing", true) || roamer) return standing;
        IdentityBands.Selection band = IdentityBands.selected(player);
        int modifier = UnifiedConfig.get().factionRules().bandModifier(faction.equals(band.faction()),
                FactionDefinitions.enemies(faction, band.faction()), band.grade());
        return StandingPolicy.effective(standing, modifier);
    }
    static boolean isNeutralTo(Human human, Player player) { return !StandingPolicy.hostile(effectiveStanding(human, player)); }
    static boolean isFriendlyTo(Human human, Player player) { return effectiveStanding(human, player) >= 250; }
    public static boolean blocksInitiatedHostility(Human human, Player player) {
        return (isNeutralTo(human, player) || human.getTarget()!=player && FactionPassage.parleyInitiation(human,player)) && !mayRetaliateAgainst(human, player);
    }
    static boolean mayRetaliateAgainst(Human human, Player player) {
        StandingIncidents memory = INCIDENTS.get(human);
        return memory != null && memory.reason(player.getUUID(), human.level().getGameTime()) != StandingIncidents.Reason.NONE;
    }
    static StandingIncidents.Reason angerReason(Human human, Player player) {
        StandingIncidents memory = INCIDENTS.get(human);
        return memory == null ? StandingIncidents.Reason.NONE : memory.reason(player.getUUID(), human.level().getGameTime());
    }
    public static int humanRank(Human human) {
        if (TierThreeHuman.isTierThree(human)) return 3;
        return human.getTier() == HumanTier.LEVEL2 ? 2 : human.getTier() == HumanTier.LEVEL1 ? 1 : 0;
    }
    static int bandGrade(Human human) { return HumanFactions.isBoss(human) ? 4 : humanRank(human); }
    static boolean canHire(Human human, Player player) {
        int tier = humanRank(human);
        var definition = FactionDefinitions.find(HumanFactions.id(human));
        return definition != null && tier >= 1 && tier <= 3
                && trueStanding(human, player) >= definition.hireStanding().get(tier - 1)
                && !mayRetaliateAgainst(human, player) && human.getTarget() != player;
    }
    private record Attribution(UUID id, ServerPlayer live, String weapon) {}
    /** Offline tameable/formal soldier owners remain attributable; unknown third-party ownership is not guessed. */
    private static Attribution attribution(Entity source) {
        if (source == null || source.getServer() == null) return null;
        UUID id = StandingAccess.responsible(source);
        if (id == null) return null;
        ServerPlayer live = source.getServer().getPlayerList().getPlayer(id);
        net.minecraft.world.item.ItemStack weapon = source instanceof net.minecraft.world.entity.LivingEntity entity
                ? entity.getMainHandItem() : net.minecraft.world.item.ItemStack.EMPTY;
        String key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(weapon.getItem()).toString();
        if (!(source instanceof Player)) key = source.getUUID() + "/" + key;
        return new Attribution(id, live, key);
    }
    public static void damage(Human human, DamageSource source, float amount) {
        Attribution actor = attribution(source.getEntity());
        ServerPlayer player = actor == null ? null : actor.live();
        if (actor == null || human.level().isClientSide || amount <= 0 || !Float.isFinite(amount)) return;
        FactionPassage.recordAttack(human,source,actor.id());
        FactionPassage.violate(human.getServer(),actor.id(),HumanFactions.id(human));
        if(human.getTier()==HumanTier.ROAMER)return;
        long now = human.level().getGameTime();
        StandingIncidents memory = memory(human);
        // No backend currently guarantees a common native shot identifier here; use the documented 2-tick fallback.
        var outcome = memory.damage(actor.id(), now, actor.weapon(), null,
                amount, human.getMaxHealth(), player == null ? FactionStandingData.get(human.getServer()).standing(actor.id(), HumanFactions.id(human)) : effectiveStanding(human, player),
                player == null ? 0 : IdentityBands.selected(player).grade(), bandGrade(human));
        change(human.getServer(), actor.id(), HumanFactions.id(human), -outcome.standingPenalty());
        if (outcome.notifyGroup() && HumanRelations.effectiveOwner(human) == null) {
            for (Human nearby : human.level().getEntitiesOfClass(Human.class, human.getBoundingBox().inflate(UnifiedConfig.get().factionRules().groupRadius()),
                    e -> e.isAlive() && HumanRelations.effectiveOwner(e) == null && HumanFactions.same(human, e))) {
                memory(nearby).groupAlert(actor.id(), now);
                HumanPerception.notice(nearby, "aid", human.position(), actor.id(), UnifiedConfig.get().factionRules().incidents().groupTicks());
            }
        }
    }
    /** Candidate only: lower-priority Forge listeners may still cancel this death. */
    public static void death(Human human, DamageSource source) {
        DEATHS.remove(human);
        Attribution actor = attribution(source.getEntity());
        if (actor == null) return;
        boolean blocked=FactionPassage.blockedDeath(human,source,actor.id());
        ServerPlayer player = actor.live();
        List<Reward> rewards = new ArrayList<>();
        if (!blocked && human.getTier()!=HumanTier.ROAMER && player != null && !player.isCreative() && !player.isSpectator()) {
            List<Human> witnesses = human.level().getEntitiesOfClass(Human.class, human.getBoundingBox().inflate(48),
                    e -> e != human && e.isAlive() && HumanRelations.effectiveOwner(e) == null);
            for (var faction : FactionDefinitions.current().definitions().values()) {
                if (!FactionDefinitions.enemies(HumanFactions.id(human), faction.id().toString())) continue;
                boolean seen = witnesses.stream().anyMatch(w -> HumanFactions.id(w).equals(faction.id().toString())
                        && HumanPerception.recentlySaw(w, human.getUUID()));
                rewards.add(new Reward(faction.id().toString(), seen ? 5 : 3));
            }
        }
        DEATHS.put(human, new PendingDeath(human.level().getGameTime(), actor, HumanFactions.id(human), List.copyOf(rewards),HumanFactions.isBoss(human),HumanRelations.effectiveOwner(human)!=null,blocked));
    }
    /** Only a real KILLED removal completes the political transaction; retain the original action timestamp. */
    public static void confirmDeath(Human human) {
        PendingDeath pending = DEATHS.remove(human);
        long now = human.level().getGameTime();
        if (pending == null || human.getHealth() > 0 || now < pending.tick() || now - pending.tick() > 200) return;
        Attribution actor = pending.actor();
        int penalty = memory(human).death(actor.id(), pending.tick(), actor.weapon(), null);
        change(human.getServer(), actor.id(), pending.faction(), -penalty);
        for (Reward reward : pending.rewards()) change(human.getServer(), actor.id(), reward.faction(), reward.amount());
        dev.felix.hostilehumans.economy.EconomyTasks.killed(human.getServer(),actor.id(),pending.faction(),pending.boss(),pending.owned(),pending.rewardBlocked());
    }
    private static void change(net.minecraft.server.MinecraftServer server, UUID player, String faction, int amount) {
        if (!UnifiedConfig.get().featureEnabled("standing", true) || amount == 0 || FactionDefinitions.find(faction) == null) return;
        if (server != null) FactionStandingData.get(server).change(player, faction, amount, server.overworld().getGameTime());
    }
    private static StandingIncidents memory(Human human) {
        StandingIncidents result = INCIDENTS.computeIfAbsent(human, ignored -> new StandingIncidents());
        result.configure(UnifiedConfig.get().factionRules().incidents());
        return result;
    }
    public static void clear(Human human) { INCIDENTS.remove(human); DEATHS.remove(human); }
}
