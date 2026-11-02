package dev.felix.hostilehumans.faction;

import dev.felix.hostilehumans.economy.Currency;
import dev.felix.hostilehumans.economy.CurrencyItem;
import dev.felix.hostilehumans.economy.EconomyPolicy;
import dev.felix.hostilehumans.economy.InventoryTransaction;
import dev.felix.hostilehumans.economy.MoneyMath;
import dev.felix.hostilehumans.economy.ParleyFlagItem;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.entity.HumanUtil;
import dev.felix.hostilehumans.settlement.SettlementIndex;
import dev.felix.hostilehumans.settlement.SettlementRegion;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Fixed faction permits and short-lived parley. Never assigns targets or confers knowledge of
 * enemies.
 */
public final class FactionPassage {
    public static final String PARLEY_ORIGIN = "hostile_humans_unified:parley_attack_origin";
    public static final long WHITE_TICKS = 1500;

    private record Flag(String dimension, UUID site, long until) {}

    private static final Map<MinecraftServer, Map<UUID, Map<String, Flag>>> FLAGS =
            new WeakHashMap<>();

    private record Attack(UUID owner, long tick, boolean blocked) {}

    private static final Map<Human, Attack> ATTACKS = new WeakHashMap<>();

    public static boolean recordAttack(
            Human victim, net.minecraft.world.damagesource.DamageSource source, UUID owner) {
        boolean blocked = rewardBlocked(source, owner);
        ATTACKS.put(victim, new Attack(owner, victim.level().getGameTime(), blocked));
        return blocked;
    }

    public static boolean blockedDeath(
            Human victim, net.minecraft.world.damagesource.DamageSource source, UUID owner) {
        var attack = ATTACKS.get(victim);
        return rewardBlocked(source, owner)
                || attack != null
                        && Objects.equals(attack.owner(), owner)
                        && attack.blocked()
                        && victim.level().getGameTime() - attack.tick() >= 0
                        && victim.level().getGameTime() - attack.tick() <= 2;
    }

    private FactionPassage() {}

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static PassagePermit state(ServerPlayer player, String faction) {
        String id = FactionDefinitions.find(faction).id().toString();
        return FactionStandingData.get(player.getServer())
                .passage(player.getUUID(), id)
                .orElseThrow(() -> new IllegalStateException("unknown permit payload retained"));
    }

    public static boolean permitted(ServerPlayer player, String faction, boolean service) {
        return permitted(player.getServer(), player.getUUID(), faction, service);
    }

    public static boolean permitted(
            MinecraftServer server, UUID player, String faction, boolean service) {
        var standing = StandingAccess.snapshot(server, player, faction);
        if (!standing.known() || !standing.enabled()) return false;
        String id = FactionDefinitions.find(faction).id().toString();
        var pass = FactionStandingData.get(server).passage(player, id);
        if (pass.isEmpty()) return false;
        var live = server.getPlayerList().getPlayer(player);
        return pass.get().active(now(server), standing.value())
                || !service && live != null && whiteUntil(live, id) > now(server);
    }

    public static long whiteUntil(ServerPlayer player, String faction) {
        var definition = FactionDefinitions.find(faction);
        if (definition == null) return 0;
        faction = definition.id().toString();
        var byPlayer = FLAGS.getOrDefault(player.getServer(), Map.of()).get(player.getUUID());
        if (byPlayer == null) return 0;
        var flag = byPlayer.get(faction);
        if (flag == null) return 0;
        var standing = StandingAccess.snapshot(player, faction);
        boolean valid =
                standing.known()
                        && standing.enabled()
                        && flag.until() > now(player.getServer())
                        && flag.dimension()
                                .equals(player.level().dimension().location().toString());
        if (valid) {
            var query =
                    SettlementIndex.get(player.serverLevel())
                            .query(player.getX(), player.getY(), player.getZ());
            valid =
                    !query.unknown()
                            && query.hits().stream()
                                    .anyMatch(
                                            h ->
                                                    h.instance().equals(flag.site())
                                                            && h.layer()
                                                                    == SettlementRegion.Layer
                                                                            .OUTER);
        }
        if (!valid) {
            byPlayer.remove(faction);
            return 0;
        }
        return flag.until();
    }

    public static boolean parleyInitiation(
            Human human, net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer server
                && human.level() == player.level()
                && whiteUntil(server, HumanFactions.id(human)) > now(server.getServer());
    }

    public static boolean underFlag(MinecraftServer server, UUID player) {
        var p = server.getPlayerList().getPlayer(player);
        if (p == null) return false;
        for (String faction :
                List.copyOf(
                        FLAGS.getOrDefault(server, Map.of())
                                .getOrDefault(player, Map.of())
                                .keySet())) if (whiteUntil(p, faction) > now(server)) return true;
        return false;
    }

    public static void clear(ServerPlayer player) {
        var map = FLAGS.get(player.getServer());
        if (map != null) map.remove(player.getUUID());
    }

    private static boolean unarmed(ItemStack stack) {
        return !HumanUtil.isMeleeWeapon(stack) && !HumanUtil.isRangedWeapon(stack);
    }

    public static void raiseHere(ServerPlayer player, String requestedFaction) {
        if (!unarmed(player.getMainHandItem())
                || !(player.getMainHandItem().getItem() instanceof ParleyFlagItem
                        || player.getMainHandItem().getItem() instanceof CurrencyItem)) {
            player.displayClientMessage(
                    Component.literal(
                            "Hold a white flag or faction currency in an unarmed main hand."),
                    true);
            return;
        }
        var query =
                SettlementIndex.get(player.serverLevel())
                        .query(player.getX(), player.getY(), player.getZ());
        if (query.unknown()) {
            player.displayClientMessage(
                    Component.literal("Region unknown; cannot establish parley."), true);
            return;
        }
        boolean raised = false;
        for (var hit : query.hits())
            if (hit.layer() == SettlementRegion.Layer.OUTER) {
                var site = SettlementIndex.get(player.serverLevel()).find(hit.instance());
                if (site == null) continue;
                var definition = FactionDefinitions.find(site.faction());
                if (definition == null) continue;
                String canonical = definition.id().toString();
                if (requestedFaction != null
                        && !Currency.faction(canonical).equals(Currency.faction(requestedFaction)))
                    continue;
                if (player.getMainHandItem().getItem() instanceof CurrencyItem held
                        && !Currency.faction(site.faction())
                                .map(c -> c == held.currency())
                                .orElse(false)) continue;
                var standing = StandingAccess.snapshot(player, site.faction());
                if (!standing.known() || !standing.enabled()) continue;
                var pass = state(player, site.faction());
                if (pass.banned(now(player.getServer()))) continue;
                var map =
                        FLAGS.computeIfAbsent(player.getServer(), k -> new HashMap<>())
                                .computeIfAbsent(player.getUUID(), k -> new HashMap<>());
                var existing = map.get(canonical);
                if (existing != null
                        && whiteUntil(player, site.faction()) > now(player.getServer())) {
                    raised = true;
                    continue;
                }
                map.put(
                        canonical,
                        new Flag(
                                player.level().dimension().location().toString(),
                                site.id(),
                                Math.addExact(now(player.getServer()), WHITE_TICKS)));
                raised = true;
            }
        player.displayClientMessage(
                Component.literal(
                        raised
                                ? "Parley raised for 75 seconds: passage/contact only; no"
                                        + " transactions or tasks."
                                : "Enter a known outer courtyard without an active parley ban."),
                true);
    }

    public static String issue(
            FactionEconomyLedger.Draft draft,
            InventoryTransaction inventory,
            Currency currency,
            EconomyPolicy policy) {
        String faction = currency.faction();
        int standing = draft.standing(faction);
        var pass = draft.permit(faction).reconcile(draft.now(), standing);
        if (pass.active(draft.now(), standing))
            return "existing fixed-term permit retained; no additional charge";
        if (pass.refundPending())
            throw new IllegalArgumentException(
                    "collect pending deposit refund before applying again");
        long deposit =
                MoneyMath.quote(
                        policy.depositBaseAzure(),
                        1,
                        policy.coefficients().get(currency),
                        1,
                        1,
                        true);
        var issued = pass.issue(draft.now(), PassagePermit.DEFAULT_DURATION, deposit, standing);
        inventory.debit(currency, deposit);
        draft.permit(faction, issued);
        return "permit issued for 20 minutes; deposit paid once";
    }

    public static String returnPermit(
            FactionEconomyLedger.Draft draft, InventoryTransaction inventory, Currency currency) {
        String faction = currency.faction();
        var pass = draft.permit(faction).returnEarly(draft.now(), draft.standing(faction));
        if (!pass.refundPending()) {
            draft.permit(faction, pass);
            return "no refundable deposit (expired/forfeited or no permit)";
        }
        inventory.credit(currency, pass.depositCopper());
        draft.permit(faction, pass.refunded());
        return "deposit returned and permit closed once";
    }

    /**
     * Called with canonical responsibility by the combat adapter; no duplicate standing penalty is
     * applied here.
     */
    public static void violate(MinecraftServer server, UUID owner, String victimFaction) {
        if (server == null || owner == null || !server.isSameThread()) return;
        var affected = new HashSet<String>();
        var live = server.getPlayerList().getPlayer(owner);
        if (live != null)
            for (String faction :
                    List.copyOf(
                            FLAGS.getOrDefault(server, Map.of())
                                    .getOrDefault(owner, Map.of())
                                    .keySet()))
                if (whiteUntil(live, faction) > now(server)) affected.add(faction);
        FLAGS.computeIfAbsent(server, k -> new HashMap<>()).remove(owner);
        var snapshot = StandingAccess.snapshot(server, owner, victimFaction);
        if (snapshot.known() && snapshot.enabled())
            affected.add(FactionDefinitions.find(victimFaction).id().toString());
        for (String faction : affected)
            if (StandingAccess.snapshot(server, owner, faction).enabled())
                try {
                    var draft = FactionEconomyLedger.draft(server, owner);
                    draft.permit(
                            faction,
                            draft.permit(faction).violate(now(server), PassagePermit.DEFAULT_BAN));
                    draft.commitState();
                } catch (RuntimeException unknown) {
                }
    }

    public static void captureProjectile(Projectile projectile, UUID owner) {
        if (owner == null
                || projectile.getServer() == null
                || !projectile.getServer().isSameThread()) return;
        if (underFlag(projectile.getServer(), owner)) {
            projectile.getPersistentData().putBoolean(PARLEY_ORIGIN, true);
            violate(projectile.getServer(), owner, null);
        }
    }

    public static boolean rewardBlocked(
            net.minecraft.world.damagesource.DamageSource source, UUID owner) {
        return source.getDirectEntity() != null
                        && source.getDirectEntity().getPersistentData().getBoolean(PARLEY_ORIGIN)
                || source.getEntity() != null
                        && source.getEntity().getPersistentData().getBoolean(PARLEY_ORIGIN)
                || source.getEntity() != null
                        && source.getEntity().getServer() != null
                        && owner != null
                        && underFlag(source.getEntity().getServer(), owner);
    }

    public static void reconcile(ServerPlayer player) {
        if (!dev.felix.hostilehumans.config.UnifiedConfig.get().featureEnabled("standing", true)) {
            clear(player);
            return;
        }
        for (Currency currency : Currency.values())
            try {
                var draft = FactionEconomyLedger.draft(player);
                var old = draft.permit(currency.faction());
                var current = old.reconcile(draft.now(), draft.standing(currency.faction()));
                if (current.refundPending()) {
                    var inventory = new InventoryTransaction(player);
                    try {
                        inventory.credit(currency, current.depositCopper());
                        draft.permit(currency.faction(), current.refunded());
                        draft.commit(player, UUID.randomUUID(), inventory, true);
                    } catch (IllegalArgumentException full) {
                        if (!old.equals(current)) {
                            draft.permit(currency.faction(), current);
                            draft.commitState();
                        }
                    }
                } else if (!old.equals(current)) {
                    draft.permit(currency.faction(), current);
                    draft.commitState();
                }
            } catch (RuntimeException unknown) {
            }
        for (String faction :
                List.copyOf(
                        FLAGS.getOrDefault(player.getServer(), Map.of())
                                .getOrDefault(player.getUUID(), Map.of())
                                .keySet())) whiteUntil(player, faction);
    }
}
