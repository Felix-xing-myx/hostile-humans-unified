package dev.felix.hostilehumans.economy;

import dev.felix.hostilehumans.faction.FactionDefinitions;
import dev.felix.hostilehumans.faction.FactionEconomyLedger;
import dev.felix.hostilehumans.faction.FactionPassage;
import dev.felix.hostilehumans.faction.StandingAccess;
import dev.felix.hostilehumans.faction.StandingReward;
import dev.felix.hostilehumans.settlement.SettlementAccess;
import dev.felix.hostilehumans.settlement.SettlementFacility;
import dev.felix.hostilehumans.settlement.SettlementIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** All economic decisions and contextual handles are authoritative on the server. */
public final class EconomyService implements EconomyNetwork.Backend {
    private record Context(
            EconomyView.Page page,
            BlockPos anchor,
            String dimension,
            Currency currency,
            UUID instance,
            String facility,
            boolean test, boolean station) {
        Context withCurrency(Currency selected) {
            return new Context(page, anchor, dimension, selected, instance, facility, test, station);
        }
    }

    private static final class Session {
        final UUID token = UUID.randomUUID();
        Context context;
        final long expires;
        int revision;
        String stamp = "";
        long last = -100;
        EconomyView.Command quote = EconomyView.Command.empty();
        EconomyView.Snapshot view;
        final LinkedHashSet<UUID> seen = new LinkedHashSet<>();

        Session(Context context, long expires) {
            this.context = context;
            this.expires = expires;
        }
    }

    private final Map<ServerPlayer, Session> sessions = new WeakHashMap<>();
    private final Map<ServerPlayer, Long> lastOpen = new WeakHashMap<>();

    private static long now(ServerPlayer player) {
        return player.getServer().overworld().getGameTime();
    }

    private static EconomyPolicy policy(Context context) {
        return context.test() ? EconomyTables.test() : EconomyTables.current();
    }

    private static void validate(ServerPlayer player, Context c) {
        if (!player.isAlive() || player.isSpectator())
            throw new IllegalArgumentException("living player required");
        if (c.test()) {
            if (!player.isCreative())
                throw new IllegalArgumentException("explicit creative test ended");
            return;
        }
        if (c.page() == EconomyView.Page.WALLET) return;
        if (!player.level().dimension().location().toString().equals(c.dimension())
                || player.distanceToSqr(
                                c.anchor().getX() + .5,
                                c.anchor().getY() + .5,
                                c.anchor().getZ() + .5)
                        > 64
                || !player.serverLevel().hasChunkAt(c.anchor()))
            throw new IllegalArgumentException("service out of range or unloaded");
        if (c.page() == EconomyView.Page.BROKER) {
            if (!BrokerIndex.get(player.serverLevel())
                    .available(player.serverLevel(), c.anchor(), c.currency()))
                throw new IllegalArgumentException(
                        "broker invalid, inside protected region or index unknown");
            return;
        }
        if (c.page() == EconomyView.Page.TASKS) {
            if (!player.serverLevel().getBlockState(c.anchor()).is(EconomyContent.TASK_BOARD.get()))
                throw new IllegalArgumentException("task board missing");
            var site = SettlementIndex.get(player.serverLevel()).taskBoardSite(c.anchor());
            if (!site.id().equals(c.instance()))
                throw new IllegalArgumentException("task board settlement changed");
            var served = site.brokerFactions();
            if (c.station() != !served.isEmpty()
                    || !(c.station() ? served.contains(c.currency().faction()) : site.faction().equals(c.currency().faction())))
                throw new IllegalArgumentException("task board faction access changed");
            var score = StandingAccess.snapshot(player, c.currency().faction());
            if (c.station()) {
                if (!score.known() || !score.enabled())
                    throw new IllegalArgumentException("faction standing unavailable");
                // Political task eligibility is evaluated per task, including negative standing.
                return;
            }
            if (!score.known()
                    || !score.enabled()
                    || !(score.atLeast(50)
                            || score.value() >= -99
                                    && FactionPassage.permitted(player, site.faction(), true)))
                throw new IllegalArgumentException(
                        "tasks require true standing 50 or a valid cautious permit");
            var access =
                    SettlementAccess.at(
                            player,
                            c.anchor().getX() + .5,
                            c.anchor().getY(),
                            c.anchor().getZ() + .5);
            if (!access.allowed()) throw new IllegalArgumentException(access.reason());
            return;
        }
        var operation =
                c.page() == EconomyView.Page.SUPPLY
                        ? SettlementAccess.Operation.SUPPLY
                        : SettlementAccess.Operation.TRADE;
        var access = SettlementAccess.queryFacility(player, c.instance(), c.facility(), operation);
        if (!access.allowed()) throw new IllegalArgumentException(access.reason());
        var p = access.located().facility().position();
        if (p.x() != c.anchor().getX() || p.y() != c.anchor().getY() || p.z() != c.anchor().getZ())
            throw new IllegalArgumentException("facility moved; reopen service");
    }

    private static Context context(
            ServerPlayer player, EconomyView.Page page, BlockPos anchor, String faction) {
        if (page == EconomyView.Page.WALLET)
            return new Context(
                    page,
                    anchor.immutable(),
                    player.level().dimension().location().toString(),
                    Currency.AZURE,
                    EconomyView.NONE,
                    "",
                    false, false);
        if (page == EconomyView.Page.TEST || page == EconomyView.Page.BROKER) {
            var currency =
                    Currency.faction(faction)
                            .orElseThrow(
                                    () -> new IllegalArgumentException("unknown currency faction"));
            return new Context(
                    page,
                    anchor.immutable(),
                    player.level().dimension().location().toString(),
                    currency,
                    EconomyView.NONE,
                    "",
                    page == EconomyView.Page.TEST, false);
        }
        var index = SettlementIndex.get(player.serverLevel());
        if (page == EconomyView.Page.TASKS) {
            var site = index.taskBoardSite(anchor);
            var served = site.brokerFactions();
            var factionId = served.isEmpty() ? site.faction() : served.get(0);
            return new Context(page, anchor.immutable(), player.level().dimension().location().toString(),
                    Currency.faction(factionId).orElseThrow(), site.id(), "", false, !served.isEmpty());
        }
        var query = index.query(anchor.getX() + .5, anchor.getY(), anchor.getZ() + .5);
        if (query.unknown()) throw new IllegalArgumentException("unknown region");
        var type =
                page == EconomyView.Page.SUPPLY
                        ? SettlementFacility.Type.SUPPLY
                        : SettlementFacility.Type.TRADER;
        Context found = null;
        for (UUID id :
                query.hits().stream().map(SettlementIndex.Hit::instance).distinct().toList()) {
            var site = index.find(id);
            if (site == null) continue;
            for (var module : site.modules())
                for (var facility : module.facilities()) {
                    var p = facility.position();
                    if (facility.type() == type
                            && p.x() == anchor.getX()
                            && p.y() == anchor.getY()
                            && p.z() == anchor.getZ()) {
                        if (found != null)
                            throw new IllegalArgumentException(
                                    "overlapping facility definitions; author must resolve");
                        found =
                                new Context(
                                        page,
                                        anchor.immutable(),
                                        player.level().dimension().location().toString(),
                                        Currency.faction(site.faction()).orElseThrow(),
                                        site.id(),
                                        module.key() + "/" + facility.id(),
                                        false, false);
                    }
                }
        }
        if (found == null) throw new IllegalArgumentException("no typed facility at this block");
        return found;
    }

    @Override
    public EconomyView.Snapshot open(
            ServerPlayer player, EconomyView.Page page, BlockPos anchor, String faction) {
        if (now(player) - lastOpen.getOrDefault(player, -100L) < 10)
            throw new IllegalArgumentException("service opening throttled");
        lastOpen.put(player, now(player));
        var context = context(player, page, anchor, faction);
        validate(player, context);
        if (!FactionEconomyLedger.recover(player))
            throw new IllegalArgumentException("economic recovery requires management repair");
        WalletInventory.initializeEmptyWallets(player);
        var session = new Session(context, now(player) + 1200);
        sessions.put(player, session);
        return snapshot(player, session, "service opened", EconomyView.NONE);
    }

    private static String stamp(ServerPlayer player, Context context) {
        return new InventoryTransaction(player).beforeFingerprint()
                + ":"
                + FactionEconomyLedger.revision(player)
                + ":"
                + policy(context).revision()
                + ":"
                + StandingAccess.snapshot(player, context.currency().faction());
    }

    @Override
    public EconomyView.Snapshot handle(ServerPlayer player, EconomyNetwork.Request request) {
        var s = sessions.get(player);
        if (s == null || !s.token.equals(request.token()))
            return EconomyNetwork.invalid(
                    request.token(), request.requestId(), "invalid handle; reopen service");
        if (request.action() == EconomyView.Action.CLOSE) {
            sessions.remove(player);
            return null;
        }
        try {
            validate(player, s.context);
            if (now(player) >= s.expires)
                throw new IllegalArgumentException("service handle expired");
        } catch (RuntimeException invalid) {
            sessions.remove(player);
            return EconomyNetwork.invalid(
                    request.token(), request.requestId(), invalid.getMessage());
        }
        if (s.seen.contains(request.requestId()))
            return snapshot(
                    player,
                    s,
                    "request already acknowledged; no repeated transaction",
                    request.requestId());
        s.seen.add(request.requestId());
        while (s.seen.size() > 256) s.seen.remove(s.seen.iterator().next());
        if (now(player) - s.last < 2)
            return snapshot(player, s, "request throttled; no transaction", request.requestId());
        s.last = now(player);
        if (request.action() == EconomyView.Action.REFRESH) {
            s.quote = request.command();
            return snapshot(player, s, "quote refreshed", request.requestId());
        }
        if (request.revision() != s.revision || !s.stamp.equals(stamp(player, s.context)))
            return snapshot(
                    player,
                    s,
                    "stale snapshot; refreshed without transaction",
                    request.requestId());
        String reason;
        try {
            if (request.action() == EconomyView.Action.SELECT_TASK_FACTION) {
                if (s.context.page() != EconomyView.Page.TASKS || !s.context.station())
                    throw new IllegalArgumentException("faction selection requires a broker station task board");
                var selected = s.context.withCurrency(request.command().currency());
                validate(player, selected);
                s.context = selected;
                s.quote = EconomyView.Command.empty();
                reason = "task board faction selected";
            } else reason = execute(player, s, request);
        } catch (RuntimeException rejected) {
            reason = rejected.getMessage();
        }
        return snapshot(player, s, reason, request.requestId());
    }

    private static int source(
            InventoryTransaction inventory,
            net.minecraft.world.item.Item item,
            int count,
            int requested) {
        if (requested >= 0) {
            if (requested >= 36) throw new IllegalArgumentException("main inventory slot required");
            var stack = inventory.item(requested);
            if (stack.getItem() != item || stack.getCount() < count)
                throw new IllegalArgumentException("source item/count changed");
            return requested;
        }
        for (int i = 0; i < 36; i++) {
            var stack = inventory.item(i);
            if (stack.getItem() == item && stack.getCount() >= count) return i;
        }
        throw new IllegalArgumentException(
                "one matching source stack with the entire quantity is required");
    }

    private static boolean economic(ServerPlayer player, Context c, EconomyPolicy p) {
        var score = StandingAccess.snapshot(player, c.currency().faction());
        return p.enabled() && score.enabled() && score.known();
    }

    private static boolean trading(Context c) {
        return c.test() || c.page() == EconomyView.Page.TRADER;
    }

    private static boolean brokerage(Context c) {
        return c.test() || c.page() == EconomyView.Page.BROKER;
    }

    private static boolean taskLane(Context c, String kind) {
        return taskLane(c.page(), c.station(), c.test(), kind);
    }

    static boolean taskLane(EconomyView.Page page, boolean station, boolean test, String kind) {
        if (test) return page == EconomyView.Page.TEST;
        if (page != EconomyView.Page.TASKS) return false;
        return switch (kind) {
            case "INITIATION", "ATONEMENT_ONE", "ATONEMENT_TWO" -> station;
            case "BASIC", "DAILY", "BOUNTY" -> true;
            default -> false;
        };
    }

    private static boolean tasking(Context c) {
        return c.test() || c.page() == EconomyView.Page.TASKS;
    }

    private static List<EconomyView.OfferRow> offers(
            ServerPlayer player,
            Session s,
            FactionEconomyLedger.Draft draft,
            InventoryTransaction inventory,
            EconomyPolicy policy) {
        if (!trading(s.context) && s.context.page() != EconomyView.Page.SUPPLY) return List.of();
        int count = s.quote.count() > 0 ? s.quote.count() : 1;
        var result = new ArrayList<EconomyView.OfferRow>();
        int standing = draft.standing(s.context.currency().faction());
        for (var offer : policy.offers()) {
            long
                    buy =
                            MoneyMath.quote(
                                    offer.baseAzure(),
                                    count,
                                    policy.coefficients().get(s.context.currency()),
                                    MoneyMath.buyFactor(standing),
                                    10,
                                    true),
                    sell =
                            MoneyMath.quote(
                                    offer.baseAzure(),
                                    count,
                                    policy.coefficients().get(s.context.currency()),
                                    MoneyMath.sellFactor(standing),
                                    10,
                                    false);
            var item = ForgeRegistries.ITEMS.getValue(offer.item());
            boolean source;
            try {
                source(inventory, item, count, s.quote.slot());
                source = true;
            } catch (RuntimeException absent) {
                source = false;
            }
            int nominal =
                    (int)
                            Math.min(
                                    Integer.MAX_VALUE,
                                    Math.multiplyExact(offer.baseAzure(), count)
                                            / policy.contributionValuePerStanding());
            boolean enabled = economic(player, s.context, policy);
            result.add(
                    new EconomyView.OfferRow(
                            offer.id(),
                            offer.item().toString(),
                            count,
                            buy,
                            sell,
                            enabled
                                    && trading(s.context)
                                    && offer.buy()
                                    && inventory.total(s.context.currency()) >= buy,
                            enabled && trading(s.context) && offer.sell() && source && sell > 0,
                            enabled
                                    ? "full batch quote; source is one real main inventory stack"
                                    : policy.reason(),
                            nominal,
                            StandingReward.positiveDelta(
                                    standing,
                                    nominal,
                                    draft.remaining(s.context.currency().faction())),
                            enabled
                                    && s.context.page() == EconomyView.Page.SUPPLY
                                    && source
                                    && nominal > 0));
        }
        return List.copyOf(result);
    }

    private static List<EconomyView.ExchangeRow> exchanges(
            ServerPlayer player,
            Session s,
            FactionEconomyLedger.Draft draft,
            InventoryTransaction inventory,
            EconomyPolicy p) {
        if (!brokerage(s.context)
                || s.quote.amount() < 1
                || s.quote.currency() == s.quote.other()
                || !p.enabled()) return List.of();
        var from = s.quote.currency();
        var to = s.quote.other();
        long output =
                MoneyMath.exchange(
                        s.quote.amount(), p.coefficients().get(from), p.coefficients().get(to));
        boolean enabled =
                economic(player, s.context, p)
                        && inventory.total(from) >= s.quote.amount()
                        && output > 0;
        String reason = "full exact quote; 10% fee";
        try {
            MoneyMath.exchangeAllowance(
                    draft.account().copy(),
                    draft.now(),
                    s.quote.amount(),
                    p.coefficients().get(from),
                    p.dailyExchangeAzure());
        } catch (RuntimeException cap) {
            enabled = false;
            reason = cap.getMessage();
        }
        return List.of(
                new EconomyView.ExchangeRow(
                        from,
                        to,
                        s.quote.amount(),
                        output,
                        10,
                        enabled,
                        EconomyNetwork.shortReason(reason)));
    }

    private String execute(ServerPlayer player, Session s, EconomyNetwork.Request request) {
        var action = request.action();
        var c = request.command();
        var context = s.context;
        var p = policy(context);
        var inventory = new InventoryTransaction(player);
        var draft = FactionEconomyLedger.draft(player);
        boolean political = true;
        String reason;
        if (action == EconomyView.Action.DEPOSIT
                || action == EconomyView.Action.WITHDRAW
                || action == EconomyView.Action.AUTO_PICKUP) {
            if (context.page() != EconomyView.Page.WALLET && !context.test())
                throw new IllegalArgumentException("wallet operations require the player wallet panel");
            political = false;
            if (action == EconomyView.Action.DEPOSIT)
                inventory.deposit(c.wallet(), c.currency(), c.denomination(), c.count());
            else if (action == EconomyView.Action.WITHDRAW)
                inventory.withdraw(c.wallet(), c.currency(), c.denomination(), c.count());
            else inventory.autoPickup(c.wallet(), c.enabled());
            reason = "wallet transaction completed";
        } else if (action == EconomyView.Action.WHITE_FLAG) {
            if (!brokerage(context)) throw new IllegalArgumentException("white flag requires a broker");
            FactionPassage.raiseHere(player, context.currency().faction());
            return "parley request evaluated; passage/contact only";
        } else if (action == EconomyView.Action.RETURN_PERMIT) {
            if (!brokerage(context))
                throw new IllegalArgumentException("return permit at a broker");
            reason = FactionPassage.returnPermit(draft, inventory, context.currency());
        } else {
            if (!economic(player, context, p))
                throw new IllegalArgumentException(
                        p.enabled()
                                ? "political policy or parley forbids this operation"
                                : p.reason());
            switch (action) {
                case BUY, SELL, CONTRIBUTE -> {
                    var current =
                            offers(player, s, draft, inventory, p).stream()
                                    .filter(o -> o.id().equals(c.entry()))
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "offer unavailable"));
                    var prior =
                            s.view.offers().stream()
                                    .filter(o -> o.id().equals(c.entry()))
                                    .findFirst()
                                    .orElseThrow();
                    if (c.count() != current.count() || !prior.equals(current))
                        throw new IllegalArgumentException("batch quote changed; refresh required");
                    var definition =
                            p.offers().stream()
                                    .filter(o -> o.id().equals(c.entry()))
                                    .findFirst()
                                    .orElseThrow();
                    var item = ForgeRegistries.ITEMS.getValue(definition.item());
                    if (action == EconomyView.Action.BUY) {
                        if (!current.canBuy())
                            throw new IllegalArgumentException("buy unavailable");
                        inventory.debit(context.currency(), current.buyCopper());
                        int left = c.count();
                        while (left > 0) {
                            int take = Math.min(left, item.getMaxStackSize());
                            inventory.insert(new ItemStack(item, take));
                            left -= take;
                        }
                        reason = "purchase delivered";
                    } else {
                        if (action == EconomyView.Action.SELL
                                ? !current.canSell()
                                : !current.canContribute())
                            throw new IllegalArgumentException("sale/contribution unavailable");
                        int slot = source(inventory, item, c.count(), c.slot());
                        var original = inventory.item(slot);
                        inventory.consumeSlot(slot, item, c.count());
                        if (action == EconomyView.Action.SELL) {
                            inventory.credit(context.currency(), current.sellCopper());
                            reason = "goods sold";
                        } else {
                            int actual =
                                    draft.gain(
                                            context.currency().faction(),
                                            current.nominalContribution());
                            reason = "contribution accepted: standing +" + actual;
                        }
                    }
                }
                case EXCHANGE -> {
                    if (!brokerage(context))
                        throw new IllegalArgumentException("exchange requires broker");
                    var quote =
                            exchanges(player, s, draft, inventory, p).stream()
                                    .findFirst()
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "request exchange quote first"));
                    if (!quote.enabled()
                            || quote.from() != c.currency()
                            || quote.to() != c.other()
                            || quote.inputCopper() != c.amount()
                            || !s.view.exchanges().contains(quote))
                        throw new IllegalArgumentException(
                                "exchange quote changed; refresh required");
                    MoneyMath.exchangeAllowance(
                            draft.account(),
                            draft.now(),
                            c.amount(),
                            p.coefficients().get(c.currency()),
                            p.dailyExchangeAzure());
                    inventory.debit(c.currency(), c.amount());
                    inventory.credit(c.other(), quote.outputCopper());
                    reason = "currency exchanged at exact server quote";
                }
                case ISSUE_PERMIT -> {
                    if (!brokerage(context))
                        throw new IllegalArgumentException("permit requires broker");
                    reason = FactionPassage.issue(draft, inventory, context.currency(), p);
                }
                case ACCEPT_TASK, DELIVER_TASK, CLAIM_TASK -> {
                    if (!tasking(context)
                            || c.count() != 0
                            || c.slot() != -1
                            || s.view.tasks().stream()
                                    .noneMatch(
                                            row ->
                                                    row.id().equals(c.entry())
                                                            && taskLane(context, row.kind())))
                        throw new IllegalArgumentException(
                                "invalid task publication context/arguments");
                    if (action == EconomyView.Action.ACCEPT_TASK && p.tasks().stream().noneMatch(task ->
                            task.id().equals(c.entry()) && taskLane(context, task.kind().name())))
                        throw new IllegalArgumentException("current task definition is not offered by this board");
                    reason =
                            action == EconomyView.Action.ACCEPT_TASK
                                    ? EconomyTasks.accept(draft, context.currency(), p, c.entry(),
                                            context.test() ? "" : SettlementIndex.get(player.serverLevel())
                                                    .taskBoardSite(context.anchor()).name())
                                    : action == EconomyView.Action.DELIVER_TASK
                                            ? EconomyTasks.deliver(
                                                    draft,
                                                    inventory,
                                                    context.currency(),
                                                    p,
                                                    c.entry())
                                            : EconomyTasks.claim(
                                                    draft,
                                                    inventory,
                                                    context.currency(),
                                                    p,
                                                    c.entry());
                }
                default -> throw new IllegalArgumentException("unsupported economy operation");
            }
        }
        var receipt = draft.commit(player, request.requestId(), inventory, political);
        return receipt.changed() ? reason : receipt.reason();
    }

    private EconomyView.Snapshot snapshot(ServerPlayer player, Session s, String reason, UUID ack) {
        var draft = FactionEconomyLedger.draft(player);
        var inventory = new InventoryTransaction(player);
        var p = policy(s.context);
        var currency = s.context.currency();
        var score = StandingAccess.snapshot(player, currency.faction());
        var metadata = new CompoundTag();
        metadata.putLong("server_game_time", now(player));
        if (s.context.page() == EconomyView.Page.WALLET) {
            var standings = new net.minecraft.nbt.ListTag();
            var definitions = FactionDefinitions.current().definitions();
            for (var id : definitions.keySet().stream().sorted().toList()) {
                var definition = definitions.get(id);
                var state = StandingAccess.snapshot(player, id.toString());
                var row = new CompoundTag();
                row.putString("faction", id.toString());
                row.putString("name", definition.name());
                row.putBoolean("known", state.known());
                row.putBoolean("enabled", state.enabled());
                row.putInt("true", state.value());
                if (state.known() && state.enabled())
                    row.putInt("effective", StandingAccess.effective(player, id.toString()));
                standings.add(row);
            }
            metadata.put("standing_overview", standings);
        }
        if (s.context.station()) {
            var factions = new net.minecraft.nbt.ListTag();
            var site = SettlementIndex.get(player.serverLevel()).find(s.context.instance());
            if (site != null)
                site.brokerFactions().forEach(id -> factions.add(net.minecraft.nbt.StringTag.valueOf(id)));
            metadata.put("task_factions", factions);
        }
        metadata.putInt("true_standing", score.value());
        if (score.known()) metadata.putInt("remaining_standing", draft.remaining(currency.faction()));
        metadata.putLong("table_revision", p.revision());
        metadata.putInt("quote_count", Math.max(1, s.quote.count()));
        metadata.putLong("exchange_input_copper", s.quote.amount());
        metadata.putString(
                "exchange_remaining_text",
                p.enabled()
                        ? MoneyMath.remainingAllowance(
                                draft.account(), draft.now(), p.dailyExchangeAzure())
                        : "not configured");
        var funds =
                Arrays.stream(Currency.values())
                        .map(
                                c ->
                                        new EconomyView.FundsRow(
                                                c,
                                                inventory.walletTotal(c),
                                                inventory.looseTotal(c),
                                                inventory.total(c)))
                        .toList();
        dev.felix.hostilehumans.faction.PassagePermit pass;
        boolean permitKnown = true;
        try {
            pass = draft.permit(currency.faction()).reconcile(draft.now(), score.value());
        } catch (RuntimeException unknown) {
            pass = dev.felix.hostilehumans.faction.PassagePermit.empty();
            permitKnown = false;
        }
        boolean enabled = economic(player, s.context, p);
        var permit =
                new EconomyView.PermitRow(
                        score.known() && permitKnown,
                        pass.active(draft.now(), score.value()),
                        pass.expiresAt(),
                        pass.banUntil(),
                        pass.depositCopper(),
                        pass.initiated(),
                        pass.refundPending(),
                        FactionPassage.whiteUntil(player, currency.faction()),
                        permitKnown
                                && enabled
                                && brokerage(s.context)
                                && score.value() >= -99
                                && score.value() < 50
                                && pass.initiated()
                                && !pass.banned(draft.now())
                                && !pass.refundPending(),
                        permitKnown
                                && score.enabled()
                                && brokerage(s.context)
                                && (pass.refundPending()
                                        || pass.active(draft.now(), score.value())),
                        p.enabled()
                                ? "fixed term; expiration/violation forfeits deposit"
                                : p.reason());
        s.revision = Math.addExact(s.revision, 1);
        s.stamp = stamp(player, s.context);
        s.view =
                new EconomyView.Snapshot(
                        s.token,
                        s.revision,
                        s.context.page(),
                        s.context.anchor(),
                        currency.faction(),
                        true,
                        s.context.test(),
                        EconomyNetwork.shortReason(reason),
                        s.context.page() == EconomyView.Page.WALLET || s.context.test()
                                ? inventory.wallets() : List.of(),
                        s.context.page() == EconomyView.Page.TASKS ? List.of() : funds,
                        offers(player, s, draft, inventory, p),
                        s.context.page() == EconomyView.Page.WALLET
                                ? Arrays.stream(Currency.values()).flatMap(faction ->
                                        EconomyTasks.rows(draft, faction, p, false).stream()
                                                .filter(EconomyView.TaskRow::accepted)
                                                .filter(row -> !row.status().equals("CLAIMED"))
                                                .limit(64)).toList()
                                : tasking(s.context)
                                ? EconomyTasks.rows(draft, currency, p, enabled).stream()
                                        .filter(row -> taskLane(s.context, row.kind()))
                                        .limit(64).toList()
                                : List.of(),
                        permit,
                        exchanges(player, s, draft, inventory, p),
                        metadata,
                        ack);
        return s.view;
    }
}
