package dev.felix.hostilehumans.economy.client;

import dev.felix.hostilehumans.client.ui.framework.InputValue;
import dev.felix.hostilehumans.client.ui.framework.PanelButton;
import dev.felix.hostilehumans.client.ui.framework.PanelGeometry;
import dev.felix.hostilehumans.client.ui.framework.PanelListRow;
import dev.felix.hostilehumans.client.ui.framework.PanelMeter;
import dev.felix.hostilehumans.client.ui.framework.PanelScreen;
import dev.felix.hostilehumans.client.ui.framework.PendingAction;
import dev.felix.hostilehumans.client.ui.framework.UiLayout;
import dev.felix.hostilehumans.economy.Currency;
import dev.felix.hostilehumans.economy.EconomyNetwork;
import dev.felix.hostilehumans.economy.EconomyView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Server-priced wallet, broker, trader, contribution and task views in the shared panel shell. */
public final class EconomyScreen extends PanelScreen {
    private static final LinkedHashSet<UUID> RETIRED = new LinkedHashSet<>();
    private final PendingAction<EconomyView.Action> pending = new PendingAction<>();
    private final Map<String, AbstractWidget> controls = new HashMap<>();
    private EconomyView.Snapshot snapshot;
    private UUID wallet = EconomyView.NONE;
    private String tab, offerId = "", taskId = "", taskFilter = "all";
    private Currency currency = Currency.AZURE, other = Currency.CRIMSON;
    private Currency.Denomination denomination = Currency.Denomination.COPPER;
    private final InputValue<Integer> quantity = InputValue.integer(1, 1, 4096),
            slot = InputValue.integer(-1, -1, 35),
            exchangeAmount = InputValue.integer(1, 1, 1_000_000_000);
    private boolean serverClosed;

    private EconomyScreen(EconomyView.Snapshot snapshot) {
        super(
                text("page." + snapshot.page().name().toLowerCase(Locale.ROOT)),
                snapshot.page() == EconomyView.Page.SUPPLY
                        ? PanelGeometry.Size.L
                        : PanelGeometry.Size.M);
        this.snapshot = snapshot;
        tab =
                switch (snapshot.page()) {
                    case TRADER, SUPPLY -> "offers";
                    case TASKS -> "tasks";
                    case BROKER -> "permit";
                    default -> "wallets";
                };
        int quote = snapshot.metadata().getInt("quote_count");
        if (quote > 0 && quote <= 4096) quantity.set(Integer.toString(quote));
        if (!snapshot.wallets().isEmpty()) wallet = snapshot.wallets().get(0).id();
        if (!snapshot.offers().isEmpty()) offerId = snapshot.offers().get(0).id();
        if (!snapshot.tasks().isEmpty()) taskId = taskKey(snapshot.tasks().get(0));
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.hostile_humans_unified.economy." + key, args);
    }

    private static Component currency(Currency value) {
        return text("currency." + value.key());
    }

    private static Component itemName(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        var item =
                key == null || !ForgeRegistries.ITEMS.containsKey(key)
                        ? null
                        : ForgeRegistries.ITEMS.getValue(key);
        return item == null ? Component.literal(id) : item.getDescription();
    }

    private Component priceCurrency() {
        return Currency.faction(snapshot.faction())
                .map(EconomyScreen::currency)
                .orElseGet(() -> Component.literal(snapshot.faction()));
    }

    private static Component named(String group, String value) {
        String key =
                "gui.hostile_humans_unified.economy."
                        + group
                        + "."
                        + value.toUpperCase(Locale.ROOT);
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(value);
    }

    public static void show(EconomyView.Snapshot value) {
        var mc = Minecraft.getInstance();
        if (RETIRED.contains(value.token())) return;
        if (!value.valid()
                && !(mc.screen instanceof EconomyScreen)
                && value.acknowledgedRequest().equals(EconomyView.NONE)) {
            if (mc.player != null)
                mc.player.displayClientMessage(text("invalid", dev.felix.hostilehumans.settlement.SettlementFacilityBindingTool.localize(value.reason())), false);
            return;
        }
        if (mc.screen instanceof EconomyScreen s && s.snapshot.token().equals(value.token())) {
            if (!value.valid()) {
                s.pending.acknowledge(value.acknowledgedRequest());
                s.serverClosed = true;
                if (mc.player != null)
                    mc.player.displayClientMessage(text("invalid", dev.felix.hostilehumans.settlement.SettlementFacilityBindingTool.localize(value.reason())), false);
                s.onClose();
                return;
            }
            s.pending.acknowledge(value.acknowledgedRequest());
            if (value.revision() >= s.snapshot.revision()) s.snapshot = value;
            s.refresh();
        } else if (value.valid()
                && value.acknowledgedRequest().equals(EconomyView.NONE)
                && !(value.page() == EconomyView.Page.TEST && !value.creativeTest()))
            mc.setScreen(new EconomyScreen(value));
    }

    @Override
    protected String faction() {
        return snapshot.faction();
    }

    @Override
    protected boolean fieldsEnabled() {
        return !pending.busy();
    }

    private void refresh() {
        String key =
                controls.entrySet().stream()
                        .filter(e -> e.getValue() == getFocused())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("");
        rebuildWidgets();
        AbstractWidget b = controls.get(key);
        if (b != null) {
            setFocused(b);
            b.setFocused(true);
            revealFocus();
        }
    }

    private UiLayout.Node muted(Component message) {
        return text(message, () -> theme().muted());
    }

    private UiLayout.Node action(
            String key, Component label, boolean enabled, boolean chosen, Runnable run) {
        PanelButton button = body(new PanelButton(label, b -> run.run(), this::theme));
        button.active = enabled;
        button.selected(chosen);
        controls.put(key, button);
        return node(button);
    }

    private void send(EconomyView.Action action, EconomyView.Command command) {
        if (pending.busy()) return;
        UUID id = EconomyNetwork.request(snapshot, action, command);
        pending.begin(id, action);
        refresh();
    }

    private EconomyView.OfferRow offer() {
        return snapshot.offers().stream()
                .filter(row -> row.id().equals(offerId))
                .findFirst()
                .orElse(null);
    }

    private boolean quoteMatches(EconomyView.OfferRow row) {
        return row != null && quantity.valid() && quantity.value().orElse(0) == row.count();
    }

    private EconomyView.ExchangeRow exchangeQuote() {
        return snapshot.exchanges().stream()
                .filter(
                        row ->
                                row.from() == currency
                                        && row.to() == other
                                        && exchangeAmount.valid()
                                        && row.inputCopper() == exchangeAmount.value().orElse(0))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected void formChanged() {
        var offer = offer();
        boolean valid = !pending.busy() && quoteMatches(offer) && slot.valid();
        for (String key : List.of("buy", "sell", "contribute")) {
            var button = controls.get(key);
            if (button != null)
                button.active =
                        valid
                                && (key.equals("buy")
                                        ? offer.canBuy()
                                        : key.equals("sell")
                                                ? offer.canSell()
                                                : offer.canContribute());
        }
        var selected =
                snapshot.wallets().stream()
                        .filter(row -> row.id().equals(wallet))
                        .findFirst()
                        .orElse(null);
        for (String key : List.of("deposit", "withdraw")) {
            var button = controls.get(key);
            if (button != null)
                button.active =
                        !pending.busy() && quantity.valid() && selected != null && selected.known();
        }
        var quoteButton = controls.get("quoteRefresh");
        if (quoteButton != null)
            quoteButton.active = !pending.busy() && quantity.valid() && slot.valid();
        var exchangeButton = controls.get("exchangeRefresh");
        if (exchangeButton != null)
            exchangeButton.active = !pending.busy() && exchangeAmount.valid() && currency != other;
        var exchange = controls.get("exchange");
        if (exchange != null) {
            var quote = exchangeQuote();
            exchange.active = !pending.busy() && quote != null && quote.enabled();
        }
        relayout();
    }

    @Override
    protected UiLayout.Node buildContent() {
        controls.clear();
        List<UiLayout.Node> content = new ArrayList<>();
        if (snapshot.creativeTest())
            content.add(text(text("creative_test"), () -> theme().danger()));
        content.add(muted(Component.literal(snapshot.reason())));
        if (pending.busy()) content.add(text(text("pending"), () -> theme().accent()));
        var metadata = snapshot.metadata();
        if (snapshot.page() != EconomyView.Page.WALLET
                && metadata.contains("true_standing") && metadata.contains("remaining_standing"))
            content.add(
                    muted(
                            text(
                                    "standing",
                                    metadata.getInt("true_standing"),
                                    metadata.getInt("remaining_standing"))));
        List<UiLayout.Node> funds = new ArrayList<>();
        for (var row : snapshot.funds())
            funds.add(
                    muted(
                            text(
                                    "funds",
                                    currency(row.currency()),
                                    row.wallets(),
                                    row.loose(),
                                    row.total())));
        if (!funds.isEmpty() && snapshot.page() != EconomyView.Page.TASKS)
            content.add(UiLayout.grid(2, 6, funds));
        List<String> tabs = tabIds();
        if (!tabs.contains(tab)) tab = tabs.get(0);
        content.add(
                switch (tab) {
                    case "offers" -> offers();
                    case "tasks" -> UiLayout.column(0, List.of());
                    case "standing" -> standings();
                    case "permit" -> broker();
                    default -> wallets();
                });
        return UiLayout.column(6, content);
    }

    /** Visible tabs for the current snapshot; the band renders this and the body switches on it. */
    private List<String> tabIds() {
        if (snapshot.page() == EconomyView.Page.WALLET) return List.of("wallets", "standing", "tasks");
        if (snapshot.page() == EconomyView.Page.TASKS) return List.of("tasks");
        if (snapshot.page() == EconomyView.Page.BROKER) return List.of("permit");
        List<String> tabs = new ArrayList<>();
        if (snapshot.page() == EconomyView.Page.WALLET || snapshot.page() == EconomyView.Page.TEST)
            tabs.add("wallets");
        if (!snapshot.offers().isEmpty()
                || snapshot.page() == EconomyView.Page.TRADER
                || snapshot.page() == EconomyView.Page.SUPPLY) tabs.add("offers");
        if (!snapshot.tasks().isEmpty() || snapshot.page() == EconomyView.Page.TASKS)
            tabs.add("tasks");
        if (snapshot.permit().known()
                || snapshot.page() == EconomyView.Page.BROKER
                || snapshot.page() == EconomyView.Page.TEST) tabs.add("permit");
        return tabs;
    }

    @Override
    protected void buildBand() {
        for (String page : tabIds()) {
            PanelButton button =
                    band(
                            new PanelButton(
                                    text(page.equals("tasks") && snapshot.page() == EconomyView.Page.WALLET ? "tab.my_tasks" : "tab." + page),
                                    b -> {
                                        tab = page;
                                        refresh();
                                    },
                                    this::theme));
            button.selected(tab.equals(page));
            button.active = !pending.busy();
            controls.put("tab." + page, button);
        }
        if (!"tasks".equals(tab)) return;
        var factions = snapshot.metadata().getList("task_factions", net.minecraft.nbt.Tag.TAG_STRING);
        if (factions.size() > 1)
            for (var value : factions)
                Currency.faction(value.getAsString())
                        .ifPresent(
                                selected ->
                                        chip(
                                                "task_faction." + selected.key(),
                                                currency(selected),
                                                selected.faction().equals(snapshot.faction()),
                                                () ->
                                                        send(
                                                                EconomyView.Action.SELECT_TASK_FACTION,
                                                                new EconomyView.Command(
                                                                        EconomyView.NONE,
                                                                        "",
                                                                        selected,
                                                                        selected,
                                                                        Currency.Denomination.COPPER,
                                                                        0,
                                                                        -1,
                                                                        0,
                                                                        false))));
        if (!overviewTasks())
            for (String filter : List.of("all", "available", "deliver", "claim"))
                chip(
                        "taskFilter." + filter,
                        text("filter." + filter),
                        taskFilter.equals(filter),
                        () -> {
                            taskFilter = filter;
                            refresh();
                        });
    }

    private void chip(String id, Component label, boolean selected, Runnable run) {
        PanelButton button = band(new PanelButton(label, b -> run.run(), this::theme));
        button.selected(selected);
        button.active = !pending.busy();
        controls.put(id, button);
    }

    private UiLayout.Node wallets() {
        List<UiLayout.Node> list = new ArrayList<>();
        for (var row : snapshot.wallets())
            list.add(
                    action(
                            "wallet." + row.id(),
                            text(
                                    "wallet",
                                    row.location(),
                                    row.known() ? text("known") : text("unknown")),
                            !pending.busy(),
                            wallet.equals(row.id()),
                            () -> {
                                wallet = row.id();
                                refresh();
                            }));
        List<UiLayout.Node> detail = new ArrayList<>();
        var selected =
                snapshot.wallets().stream()
                        .filter(row -> row.id().equals(wallet))
                        .findFirst()
                        .orElse(null);
        if (selected == null) {
            detail.add(muted(text("no_wallet")));
            return UiLayout.column(6, detail);
        }
        for (Currency value : Currency.values())
            detail.add(
                    muted(
                            text(
                                    "wallet_balance",
                                    currency(value),
                                    selected.known()
                                            ? Long.toString(selected.amount(value))
                                            : text("unknown").getString())));
        detail.add(currencyChoice(false));
        List<UiLayout.Node> faces = new ArrayList<>();
        for (var face : Currency.Denomination.values())
            faces.add(
                    action(
                            "face." + face,
                            text("face." + face.key()),
                            !pending.busy(),
                            face == denomination,
                            () -> {
                                denomination = face;
                                refresh();
                            }));
        detail.add(UiLayout.grid(3, 4, 70, faces));
        detail.add(field(text("coin_count"), quantity));
        detail.add(
                UiLayout.grid(
                        2,
                        6,
                        List.of(
                                action(
                                        "deposit",
                                        text("deposit"),
                                        !pending.busy() && selected.known() && quantity.valid(),
                                        false,
                                        () -> {
                                            if (quantity.valid())
                                                send(
                                                        EconomyView.Action.DEPOSIT,
                                                        EconomyView.Command.wallet(
                                                                selected.id(),
                                                                currency,
                                                                denomination,
                                                                quantity.value().orElseThrow()));
                                        }),
                                action(
                                        "withdraw",
                                        text("withdraw"),
                                        !pending.busy() && selected.known() && quantity.valid(),
                                        false,
                                        () -> {
                                            if (quantity.valid())
                                                send(
                                                        EconomyView.Action.WITHDRAW,
                                                        EconomyView.Command.wallet(
                                                                selected.id(),
                                                                currency,
                                                                denomination,
                                                                quantity.value().orElseThrow()));
                                        }))));
        detail.add(
                action(
                        "pickup",
                        text(selected.autoPickup() ? "pickup_on" : "pickup_off"),
                        !pending.busy() && selected.known(),
                        selected.autoPickup(),
                        () ->
                                send(
                                        EconomyView.Action.AUTO_PICKUP,
                                        new EconomyView.Command(
                                                selected.id(),
                                                "",
                                                currency,
                                                currency,
                                                denomination,
                                                0,
                                                -1,
                                                0,
                                                !selected.autoPickup()))));
        return UiLayout.row(12, List.of(UiLayout.column(6, list), UiLayout.column(6, detail)));
    }

    private UiLayout.Node currencyChoice(boolean destination) {
        List<UiLayout.Node> choices = new ArrayList<>();
        for (Currency value : Currency.values())
            choices.add(
                    action(
                            (destination ? "to." : "from.") + value,
                            currency(value),
                            !pending.busy(),
                            value == (destination ? other : currency),
                            () -> {
                                if (destination) other = value;
                                else currency = value;
                                refresh();
                            }));
        return UiLayout.grid(4, 4, 65, choices);
    }

    private void offerAction(EconomyView.Action action) {
        var row = offer();
        if (!quoteMatches(row) || !slot.valid()) return;
        boolean allowed =
                action == EconomyView.Action.BUY
                        ? row.canBuy()
                        : action == EconomyView.Action.SELL ? row.canSell() : row.canContribute();
        if (allowed)
            send(
                    action,
                    EconomyView.Command.offer(row.id(), row.count(), slot.value().orElseThrow()));
    }

    private UiLayout.Node offers() {
        List<UiLayout.Node> list = new ArrayList<>();
        for (var row : snapshot.offers())
            list.add(
                    action(
                            "offer." + row.id(),
                            itemName(row.item()),
                            !pending.busy(),
                            row.id().equals(offerId),
                            () -> {
                                offerId = row.id();
                                refresh();
                            }));
        List<UiLayout.Node> detail = new ArrayList<>();
        var row = offer();
        if (row == null) {
            detail.add(muted(text("no_offers")));
            return UiLayout.column(6, detail);
        }
        detail.add(text(itemName(row.item()), () -> theme().accent()));
        if (snapshot.page() == EconomyView.Page.SUPPLY)
            detail.add(muted(text("supply_batch", row.count())));
        else {
            detail.add(
                    muted(
                            text(
                                    "quote",
                                    row.count(),
                                    row.buyCopper() < 0 ? text("unknown") : row.buyCopper(),
                                    row.sellCopper() < 0 ? text("unknown") : row.sellCopper())));
            detail.add(muted(text("price_currency", priceCurrency())));
        }
        detail.add(muted(Component.literal(row.reason())));
        detail.add(field(text("batch_count"), quantity));
        detail.add(field(text("slot"), slot));
        detail.add(muted(text("slot_hint")));
        detail.add(muted(text("quote_hint")));
        detail.add(
                action(
                        "quoteRefresh",
                        text("quote_refresh"),
                        !pending.busy() && quantity.valid() && slot.valid(),
                        false,
                        () -> {
                            if (quantity.valid() && slot.valid())
                                send(
                                        EconomyView.Action.REFRESH,
                                        EconomyView.Command.offer(
                                                row.id(),
                                                quantity.value().orElseThrow(),
                                                slot.value().orElseThrow()));
                        }));
        boolean ready = !pending.busy() && quoteMatches(row) && slot.valid();
        if (snapshot.page() == EconomyView.Page.SUPPLY) {
            detail.add(
                    muted(
                            text(
                                    "contribution",
                                    row.nominalContribution(),
                                    row.availableContribution())));
            detail.add(
                    action(
                            "contribute",
                            text("contribute"),
                            ready && row.canContribute(),
                            false,
                            () -> offerAction(EconomyView.Action.CONTRIBUTE)));
        } else
            detail.add(
                    UiLayout.grid(
                            2,
                            6,
                            List.of(
                                    action(
                                            "buy",
                                            text("buy"),
                                            ready && row.canBuy(),
                                            false,
                                            () -> offerAction(EconomyView.Action.BUY)),
                                    action(
                                            "sell",
                                            text("sell"),
                                            ready && row.canSell(),
                                            false,
                                            () -> offerAction(EconomyView.Action.SELL)))));
        return UiLayout.row(12, List.of(UiLayout.column(6, list), UiLayout.column(6, detail)));
    }

    private boolean matches(EconomyView.TaskRow row) {
        return switch (taskFilter) {
            case "available" -> row.canAccept();
            case "deliver" -> row.canDeliver();
            case "claim" -> row.canClaim();
            default -> true;
        };
    }

    private EconomyView.Command taskCommand(String id) {
        return new EconomyView.Command(
                EconomyView.NONE,
                id,
                Currency.AZURE,
                Currency.AZURE,
                Currency.Denomination.COPPER,
                0,
                -1,
                0,
                false);
    }

    private UiLayout.Node standings() {
        List<UiLayout.Node> rows = new ArrayList<>();
        for (var tag : snapshot.metadata().getList("standing_overview", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var row = (net.minecraft.nbt.CompoundTag) tag;
            var label = Currency.faction(row.getString("faction")).map(EconomyScreen::currency)
                    .orElseGet(() -> Component.literal(row.getString("name").isBlank()
                            ? row.getString("faction") : row.getString("name")));
            rows.add(muted(row.getBoolean("known") ? row.getBoolean("enabled")
                    ? text("standing_overview", label, row.getInt("true"), row.getInt("effective"))
                    : text("standing_overview_disabled", label, row.getInt("true"))
                    : text("standing_overview_unknown", label)));
        }
        return UiLayout.column(6, rows);
    }

    private static String taskKey(EconomyView.TaskRow row) {
        return row.issuer() + "|" + row.id();
    }

    /** The wallet page shows the player's own commissions; a station board shows its offers. */
    private boolean overviewTasks() {
        return snapshot.page() == EconomyView.Page.WALLET;
    }

    private EconomyView.TaskRow selectedTask() {
        return snapshot.tasks().stream()
                .filter(task -> taskKey(task).equals(taskId))
                .findFirst()
                .orElse(null);
    }

    @Override
    protected int splitWidth() {
        return "tasks".equals(tab) ? 280 : 0;
    }

    private UiLayout.Node taskRow(EconomyView.TaskRow row) {
        String key = taskKey(row);
        Component metadata =
                named("status", row.status())
                        .copy()
                        .append(" · ")
                        .append(named("kind", row.kind()));
        if (overviewTasks())
            metadata =
                    metadata.copy()
                            .append(" · ")
                            .append(
                                    Currency.faction(row.issuer())
                                            .map(EconomyScreen::currency)
                                            .orElseGet(() -> Component.literal(row.issuer())));
        String value = row.target() > 0 ? row.progress() + "/" + row.target() : "";
        PanelListRow button =
                body(
                        new PanelListRow(
                                row.title(),
                                metadata.getString(),
                                value,
                                row.progress(),
                                row.target(),
                                row.canClaim() ? 0xFF8FC7A6 : row.canAccept() ? theme().accent() : 0xFF4A5560,
                                Component.literal(row.title()).append(". ").append(metadata),
                                b -> {
                                    if (pending.busy()) return;
                                    taskId = key;
                                    refresh();
                                },
                                this::theme));
        button.selected(key.equals(taskId));
        controls.put("task." + key, button);
        return node(button);
    }

    @Override
    protected void buildLeft(List<UiLayout.Node> out) {
        // Split layouts skip buildContent(), so the control map is cleared here instead.
        controls.clear();
        boolean any = false;
        for (var row : snapshot.tasks())
            if (matches(row)) {
                out.add(taskRow(row));
                any = true;
            }
        if (!any) out.add(muted(text("no_tasks")));
    }

    @Override
    protected void buildRight(List<UiLayout.Node> out) {
        var row = selectedTask();
        if (row == null) {
            out.add(muted(text("no_tasks")));
            return;
        }
        out.add(text(Component.literal(row.title()), () -> theme().accent()));
        out.add(muted(text("task_state", named("kind", row.kind()), named("status", row.status()))));
        if (!row.objective().isEmpty()) {
            out.add(muted(text("task_objective", named("objective", row.objective()))));
            if (row.objective().equals("DELIVERY")) {
                var id = net.minecraft.resources.ResourceLocation.tryParse(row.item());
                var item =
                        id == null
                                ? null
                                : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
                out.add(
                        muted(
                                text(
                                        "task_delivery_target",
                                        item == null
                                                ? Component.literal(row.item())
                                                : item.getDescription(),
                                        row.target())));
            } else if (!row.targetFaction().isEmpty()) {
                var target =
                        Currency.faction(row.targetFaction())
                                .map(EconomyScreen::currency)
                                .orElseGet(() -> Component.literal(row.targetFaction()));
                out.add(muted(text("task_target_faction", target)));
            }
        }
        if (row.deadline() > 0) out.add(muted(text("task_deadline", remaining(row.deadline()))));
        else if (row.timeLimit() > 0)
            out.add(muted(text("task_deadline", text("seconds", (row.timeLimit() + 19) / 20))));
        if (!row.siteName().isEmpty()) out.add(muted(text("task_origin", row.siteName())));
        out.add(muted(text("task_progress", row.progress(), row.target())));
        out.add(
                node(
                        body(
                                new PanelMeter(
                                        () -> row.progress(),
                                        () -> row.target(),
                                        () -> theme().accent()))));
        var issuer = Currency.faction(row.issuer()).or(() -> Currency.faction(snapshot.faction()));
        if (row.nominalStanding() != 0 || row.availableStanding() != 0)
            out.add(
                    rewardIcon(
                            net.minecraft.world.item.Items.BOOK,
                            text(
                                    "task_standing_reward",
                                    row.nominalStanding(),
                                    row.availableStanding())));
        if (row.rewardCopper() > 0 && issuer.isPresent())
            out.add(
                    rewardIcon(
                            dev.felix.hostilehumans.economy.EconomyContent.coin(
                                    issuer.get(), Currency.Denomination.COPPER),
                            text("task_coin_reward", row.rewardCopper())));
        if (row.kind().equals("INITIATION"))
            out.add(rewardIcon(net.minecraft.world.item.Items.PAPER, text("task_permit_reward")));
        out.add(
                muted(
                        text(
                                "price_currency",
                                issuer.map(EconomyScreen::currency).orElseGet(this::priceCurrency))));
        if (overviewTasks()) out.add(muted(text("task_process_at_board")));
        else out.add(muted(Component.literal(row.reason())));
    }

    @Override
    protected Component bandStatus() {
        return pending.busy() ? text("pending") : Component.empty();
    }

    private UiLayout.Node rewardIcon(net.minecraft.world.item.Item item, Component label) {
        return node(body(new dev.felix.hostilehumans.client.ui.framework.PanelText(label, () -> theme().muted()) {
            @Override public int measureHeight(int width) {
                return Math.max(18, super.measureHeight(width - 20));
            }

            @Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX,
                    int mouseY, float partialTick) {
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
                graphics.renderItem(new net.minecraft.world.item.ItemStack(item), getX() + getWidth() - 16, getY());
            }
        }));
    }

    private Component remaining(long deadline) {
        if (!snapshot.metadata().contains("server_game_time")) return text("unknown");
        long ticks = Math.max(0, deadline - snapshot.metadata().getLong("server_game_time"));
        return text("seconds", (ticks + 19) / 20);
    }

    private UiLayout.Node broker() {
        var permit = snapshot.permit();
        List<UiLayout.Node> nodes = new ArrayList<>();
        nodes.add(
                muted(
                        text(
                                "permit_state",
                                permit.known()
                                        ? text(permit.active() ? "active" : "inactive")
                                        : text("unknown"),
                                remaining(permit.expiresAt()),
                                permit.depositCopper())));
        nodes.add(
                muted(
                        text(
                                "permit_history",
                                text(permit.initiated() ? "yes" : "no"),
                                text(permit.refundPending() ? "yes" : "no"),
                                remaining(permit.banUntil()),
                                remaining(permit.whiteFlagUntil()))));
        nodes.add(muted(Component.literal(permit.reason())));
        nodes.add(
                UiLayout.grid(
                        2,
                        6,
                        List.of(
                                action(
                                        "issue",
                                        text("issue"),
                                        !pending.busy() && permit.known() && permit.canIssue(),
                                        false,
                                        () ->
                                                send(
                                                        EconomyView.Action.ISSUE_PERMIT,
                                                        EconomyView.Command.empty())),
                                action(
                                        "return",
                                        text("return"),
                                        !pending.busy() && permit.known() && permit.canReturn(),
                                        false,
                                        () ->
                                                send(
                                                        EconomyView.Action.RETURN_PERMIT,
                                                        EconomyView.Command.empty())))));
        nodes.add(
                action(
                        "whiteFlag",
                        text("white_flag"),
                        !pending.busy() && permit.known(),
                        false,
                        () -> send(EconomyView.Action.WHITE_FLAG, EconomyView.Command.empty())));
        nodes.add(muted(text("white_flag_hint")));
        nodes.add(muted(text("exchange_from")));
        nodes.add(currencyChoice(false));
        nodes.add(muted(text("exchange_to")));
        nodes.add(currencyChoice(true));
        nodes.add(field(text("exchange_amount"), exchangeAmount));
        if (snapshot.metadata().contains("exchange_remaining_text"))
            nodes.add(
                    muted(
                            Component.literal(
                                    snapshot.metadata().getString("exchange_remaining_text"))));
        var quote = exchangeQuote();
        if (quote != null) {
            nodes.add(
                    muted(
                            text(
                                    "exchange_quote",
                                    currency(quote.from()),
                                    quote.inputCopper(),
                                    currency(quote.to()),
                                    quote.outputCopper(),
                                    quote.feePercent())));
            nodes.add(muted(Component.literal(quote.reason())));
        } else nodes.add(muted(text("exchange_no_quote")));
        nodes.add(
                action(
                        "exchangeRefresh",
                        text("quote_refresh"),
                        !pending.busy() && exchangeAmount.valid() && currency != other,
                        false,
                        () -> {
                            if (exchangeAmount.valid() && currency != other)
                                send(
                                        EconomyView.Action.REFRESH,
                                        EconomyView.Command.exchange(
                                                currency,
                                                other,
                                                exchangeAmount.value().orElseThrow()));
                        }));
        nodes.add(
                action(
                        "exchange",
                        text("exchange"),
                        !pending.busy() && quote != null && quote.enabled(),
                        false,
                        () -> {
                            var current = exchangeQuote();
                            if (current != null && current.enabled())
                                send(
                                        EconomyView.Action.EXCHANGE,
                                        EconomyView.Command.exchange(
                                                current.from(),
                                                current.to(),
                                                current.inputCopper()));
                        }));
        return UiLayout.column(6, nodes);
    }

    @Override
    protected void buildFooter() {
        if ("tasks".equals(tab) && !overviewTasks()) {
            var row = selectedTask();
            if (row != null) {
                footer(text("accept"), b -> send(EconomyView.Action.ACCEPT_TASK, taskCommand(row.id())))
                        .active = !pending.busy() && row.canAccept();
                footer(text("deliver"), b -> send(EconomyView.Action.DELIVER_TASK, taskCommand(row.id())))
                        .active = !pending.busy() && row.canDeliver();
                footer(text("claim"), b -> send(EconomyView.Action.CLAIM_TASK, taskCommand(row.id())))
                        .active = !pending.busy() && row.canClaim();
                footerSpacer();
            }
        }
        footer(Component.translatable("gui.done"), b -> onClose());
        footer(text("refresh"), b -> send(EconomyView.Action.REFRESH, EconomyView.Command.empty()))
                        .active =
                !pending.busy();
    }

    @Override
    public void removed() {
        RETIRED.add(snapshot.token());
        while (RETIRED.size() > 32) RETIRED.remove(RETIRED.iterator().next());
        if (!serverClosed && Minecraft.getInstance().getConnection() != null)
            EconomyNetwork.request(snapshot, EconomyView.Action.CLOSE, EconomyView.Command.empty());
        super.removed();
    }
}
