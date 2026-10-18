package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import dev.felix.hostilehumans.core.StandingIncidents;
import dev.felix.hostilehumans.core.StandingPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Political standing and short-term hostility. Never assigns a combat target or grants visual confirmation. */
final class StandingEffects {
    private static final Map<Human, StandingIncidents> INCIDENTS = new WeakHashMap<>();
    private StandingEffects() {}
    static int trueStanding(Human human, Player player) {
        if (human.getTier() == HumanTier.ROAMER) return -500;
        if (!UnifiedConfig.get().featureEnabled("standing", true)) return 100;
        if (player.getServer() == null || FactionDefinitions.find(HumanFactions.id(human)) == null) return 100;
        return FactionStandingData.get(player.getServer()).standing(player.getUUID(), HumanFactions.id(human));
    }
    static int effectiveStanding(Human human, Player player) {
        int standing = trueStanding(human, player);
        if (!UnifiedConfig.get().featureEnabled("standing", true) || human.getTier() == HumanTier.ROAMER) return standing;
        IdentityBands.Selection band = IdentityBands.selected(player);
        int modifier = UnifiedConfig.get().factionRules().bandModifier(HumanFactions.id(human).equals(band.faction()),
                FactionDefinitions.enemies(HumanFactions.id(human), band.faction()), band.grade());
        return StandingPolicy.effective(standing, modifier);
    }
    static boolean isNeutralTo(Human human, Player player) { return !StandingPolicy.hostile(effectiveStanding(human, player)); }
    static boolean isFriendlyTo(Human human, Player player) { return effectiveStanding(human, player) >= 250; }
    static boolean blocksInitiatedHostility(Human human, Player player) {
        return isNeutralTo(human, player) && !mayRetaliateAgainst(human, player);
    }
    static boolean mayRetaliateAgainst(Human human, Player player) {
        StandingIncidents memory = INCIDENTS.get(human);
        return memory != null && memory.reason(player.getUUID(), human.level().getGameTime()) != StandingIncidents.Reason.NONE;
    }
    static StandingIncidents.Reason angerReason(Human human, Player player) {
        StandingIncidents memory = INCIDENTS.get(human);
        return memory == null ? StandingIncidents.Reason.NONE : memory.reason(player.getUUID(), human.level().getGameTime());
    }
    static int humanRank(Human human) {
        if (TierThreeHuman.isTierThree(human)) return 3;
        return human.getTier() == HumanTier.LEVEL2 ? 2 : human.getTier() == HumanTier.LEVEL1 ? 1 : 0;
    }
    static int bandGrade(Human human) { return HumanFactions.isBoss(human) ? 4 : humanRank(human); }
    static boolean canHire(Human human, Player player) {
        return StandingPolicy.mayHire(trueStanding(human, player), humanRank(human))
                && !mayRetaliateAgainst(human, player) && human.getTarget() != player;
    }
    /** Attribution uses native source ownership or the public tameable owner; unknown mod owners are not guessed. */
    static ServerPlayer responsiblePlayer(Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof TamableAnimal pet && pet.getOwnerUUID() != null && pet.getServer() != null)
            return pet.getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
        return null;
    }
    private static String weapon(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }
    static void damage(Human human, DamageSource source, float amount) {
        ServerPlayer player = responsiblePlayer(source.getEntity());
        if (player == null || human.level().isClientSide || amount <= 0 || !Float.isFinite(amount)
                || human.getTier() == HumanTier.ROAMER) return;
        long now = human.level().getGameTime();
        StandingIncidents memory = memory(human);
        // No backend currently guarantees a common native shot identifier here; use the documented 2-tick fallback.
        var outcome = memory.damage(player.getUUID(), now, weapon(player), null,
                Math.min(amount, human.getHealth()), human.getMaxHealth(), effectiveStanding(human, player),
                IdentityBands.selected(player).grade(), bandGrade(human));
        change(player, HumanFactions.id(human), -outcome.standingPenalty());
        if (outcome.notifyGroup() && HumanRelations.effectiveOwner(human) == null) {
            for (Human nearby : human.level().getEntitiesOfClass(Human.class, human.getBoundingBox().inflate(UnifiedConfig.get().factionRules().groupRadius()),
                    e -> e.isAlive() && HumanRelations.effectiveOwner(e) == null && HumanFactions.same(human, e))) {
                memory(nearby).groupAlert(player.getUUID(), now);
                HumanPerception.notice(nearby, "aid", human.position(), player.getUUID(), UnifiedConfig.get().factionRules().incidents().groupTicks());
            }
        }
    }
    static void death(Human human, DamageSource source) {
        ServerPlayer player = responsiblePlayer(source.getEntity());
        if (player == null || human.getTier() == HumanTier.ROAMER) return;
        long now = human.level().getGameTime();
        int penalty = memory(human).death(player.getUUID(), now, weapon(player), null);
        change(player, HumanFactions.id(human), -penalty);
        if (player.isCreative() || player.isSpectator()) return;
        // One reward per beneficiary faction. The witness bonus shares the same daily allowance.
        List<Human> witnesses = human.level().getEntitiesOfClass(Human.class, human.getBoundingBox().inflate(48),
                e -> e != human && e.isAlive() && HumanRelations.effectiveOwner(e) == null);
        for (var faction : FactionDefinitions.current().definitions().values()) {
            if (!FactionDefinitions.enemies(HumanFactions.id(human), faction.id().toString())) continue;
            boolean seen = witnesses.stream().anyMatch(w -> HumanFactions.id(w).equals(faction.id().toString())
                    && HumanPerception.recentlySaw(w, human.getUUID()));
            change(player, faction.id().toString(), seen ? 5 : 3);
        }
    }
    private static void change(ServerPlayer player, String faction, int amount) {
        if (!UnifiedConfig.get().featureEnabled("standing", true) || amount == 0 || FactionDefinitions.find(faction) == null) return;
        FactionStandingData.get(player.server).change(player.getUUID(), faction, amount, player.server.overworld().getGameTime());
    }
    private static StandingIncidents memory(Human human) {
        StandingIncidents result = INCIDENTS.computeIfAbsent(human, ignored -> new StandingIncidents());
        result.configure(UnifiedConfig.get().factionRules().incidents());
        return result;
    }
    static void clear(Human human) { INCIDENTS.remove(human); }
}
