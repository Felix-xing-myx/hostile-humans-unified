package dev.felix.hostilehumans.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.UUID;

/**
 * Shared typed UI contract. Prices/rewards come from these server rows, never from arbitrary
 * metadata.
 */
public final class EconomyView {
    public static final UUID NONE = new UUID(0, 0);

    public enum Page {
        WALLET,
        BROKER,
        TRADER,
        SUPPLY,
        TASKS,
        TEST
    }

    public enum Action {
        REFRESH,
        DEPOSIT,
        WITHDRAW,
        AUTO_PICKUP,
        BUY,
        SELL,
        CONTRIBUTE,
        EXCHANGE,
        ISSUE_PERMIT,
        RETURN_PERMIT,
        ACCEPT_TASK,
        DELIVER_TASK,
        CLAIM_TASK,
        WHITE_FLAG,
        CLOSE
    }

    public record Command(
            UUID wallet,
            String entry,
            Currency currency,
            Currency other,
            Currency.Denomination denomination,
            int count,
            int slot,
            long amount,
            boolean enabled) {
        public Command {
            if (wallet == null
                    || entry == null
                    || entry.length() > 128
                    || currency == null
                    || other == null
                    || denomination == null
                    || count < 0
                    || count > 4096
                    || slot < -1
                    || slot > 255
                    || amount < 0
                    || amount > 1_000_000_000L)
                throw new IllegalArgumentException("economy command bounds");
        }

        public static Command empty() {
            return new Command(
                    NONE,
                    "",
                    Currency.AZURE,
                    Currency.AZURE,
                    Currency.Denomination.COPPER,
                    0,
                    -1,
                    0,
                    false);
        }

        public static Command wallet(
                UUID id, Currency currency, Currency.Denomination face, int count) {
            return new Command(id, "", currency, currency, face, count, -1, 0, false);
        }

        public static Command offer(String id, int count, int slot) {
            return new Command(
                    NONE,
                    id,
                    Currency.AZURE,
                    Currency.AZURE,
                    Currency.Denomination.COPPER,
                    count,
                    slot,
                    0,
                    false);
        }

        public static Command exchange(Currency from, Currency to, long amount) {
            return new Command(
                    NONE, "", from, to, Currency.Denomination.COPPER, 0, -1, amount, false);
        }
    }

    public record WalletRow(
            UUID id,
            String location,
            int revision,
            boolean known,
            boolean autoPickup,
            List<Long> copper) {
        public WalletRow {
            copper = List.copyOf(copper);
            if (id == null
                    || location == null
                    || location.length() > 96
                    || copper.size() != 4
                    || copper.stream().anyMatch(v -> v < 0 || v > WalletBalance.CAPACITY))
                throw new IllegalArgumentException("wallet row");
        }

        public long amount(Currency currency) {
            return copper.get(currency.ordinal());
        }
    }

    public record FundsRow(Currency currency, long wallets, long loose, long total) {}

    public record OfferRow(
            String id,
            String item,
            int count,
            long buyCopper,
            long sellCopper,
            boolean canBuy,
            boolean canSell,
            String reason,
            int nominalContribution,
            int availableContribution,
            boolean canContribute) {
        public OfferRow(
                String id,
                String item,
                int count,
                long buyCopper,
                long sellCopper,
                boolean canBuy,
                boolean canSell,
                String reason) {
            this(id, item, count, buyCopper, sellCopper, canBuy, canSell, reason, 0, 0, false);
        }
    }

    public record ExchangeRow(
            Currency from,
            Currency to,
            long inputCopper,
            long outputCopper,
            int feePercent,
            boolean enabled,
            String reason) {}

    public record TaskRow(
            String id,
            String title,
            String kind,
            String status,
            int progress,
            int target,
            int nominalStanding,
            int availableStanding,
            long rewardCopper,
            boolean canAccept,
            boolean canDeliver,
            boolean canClaim,
            String reason) {}

    public record PermitRow(
            boolean known,
            boolean active,
            long expiresAt,
            long banUntil,
            long depositCopper,
            boolean initiated,
            boolean refundPending,
            long whiteFlagUntil,
            boolean canIssue,
            boolean canReturn,
            String reason) {}

    public record Snapshot(
            UUID token,
            int revision,
            Page page,
            BlockPos anchor,
            String faction,
            boolean valid,
            boolean creativeTest,
            String reason,
            List<WalletRow> wallets,
            List<FundsRow> funds,
            List<OfferRow> offers,
            List<TaskRow> tasks,
            PermitRow permit,
            List<ExchangeRow> exchanges,
            CompoundTag metadata,
            UUID acknowledgedRequest) {
        public Snapshot {
            wallets = List.copyOf(wallets);
            funds = List.copyOf(funds);
            offers = List.copyOf(offers);
            tasks = List.copyOf(tasks);
            exchanges = List.copyOf(exchanges);
            metadata = metadata.copy();
            anchor = anchor.immutable();
            if (token == null
                    || page == null
                    || faction == null
                    || faction.length() > 128
                    || reason == null
                    || reason.length() > 256
                    || wallets.size() > 64
                    || funds.size() > 4
                    || offers.size() > 64
                    || tasks.size() > 64
                    || exchanges.size() > 12
                    || permit == null
                    || acknowledgedRequest == null)
                throw new IllegalArgumentException("economy snapshot bounds");
        }

        public Snapshot(
                UUID token,
                int revision,
                Page page,
                BlockPos anchor,
                String faction,
                boolean valid,
                boolean creativeTest,
                String reason,
                List<WalletRow> wallets,
                List<FundsRow> funds,
                List<OfferRow> offers,
                List<TaskRow> tasks,
                PermitRow permit,
                CompoundTag metadata,
                UUID acknowledgedRequest) {
            this(
                    token,
                    revision,
                    page,
                    anchor,
                    faction,
                    valid,
                    creativeTest,
                    reason,
                    wallets,
                    funds,
                    offers,
                    tasks,
                    permit,
                    List.of(),
                    metadata,
                    acknowledgedRequest);
        }
    }

    private EconomyView() {}

    public static void command(Command p, FriendlyByteBuf b) {
        b.writeUUID(p.wallet());
        b.writeUtf(p.entry(), 128);
        b.writeEnum(p.currency());
        b.writeEnum(p.other());
        b.writeEnum(p.denomination());
        b.writeVarInt(p.count());
        b.writeVarInt(p.slot());
        b.writeLong(p.amount());
        b.writeBoolean(p.enabled());
    }

    public static Command command(FriendlyByteBuf b) {
        return new Command(
                b.readUUID(),
                b.readUtf(128),
                b.readEnum(Currency.class),
                b.readEnum(Currency.class),
                b.readEnum(Currency.Denomination.class),
                b.readVarInt(),
                b.readVarInt(),
                b.readLong(),
                b.readBoolean());
    }

    private static int size(FriendlyByteBuf b, int maximum) {
        int count = b.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("economy row count");
        return count;
    }

    public static void snapshot(Snapshot p, FriendlyByteBuf b) {
        b.writeUUID(p.token());
        b.writeVarInt(p.revision());
        b.writeEnum(p.page());
        b.writeBlockPos(p.anchor());
        b.writeUtf(p.faction(), 128);
        b.writeBoolean(p.valid());
        b.writeBoolean(p.creativeTest());
        b.writeUtf(p.reason(), 256);
        b.writeUUID(p.acknowledgedRequest());
        b.writeVarInt(p.wallets().size());
        for (var row : p.wallets()) {
            b.writeUUID(row.id());
            b.writeUtf(row.location(), 96);
            b.writeVarInt(row.revision());
            b.writeBoolean(row.known());
            b.writeBoolean(row.autoPickup());
            for (long amount : row.copper()) b.writeLong(amount);
        }
        b.writeVarInt(p.funds().size());
        for (var row : p.funds()) {
            b.writeEnum(row.currency());
            b.writeLong(row.wallets());
            b.writeLong(row.loose());
            b.writeLong(row.total());
        }
        b.writeVarInt(p.offers().size());
        for (var row : p.offers()) {
            b.writeUtf(row.id(), 128);
            b.writeUtf(row.item(), 128);
            b.writeVarInt(row.count());
            b.writeLong(row.buyCopper());
            b.writeLong(row.sellCopper());
            b.writeBoolean(row.canBuy());
            b.writeBoolean(row.canSell());
            b.writeUtf(row.reason(), 256);
            b.writeVarInt(row.nominalContribution());
            b.writeVarInt(row.availableContribution());
            b.writeBoolean(row.canContribute());
        }
        b.writeVarInt(p.tasks().size());
        for (var row : p.tasks()) {
            b.writeUtf(row.id(), 128);
            b.writeUtf(row.title(), 128);
            b.writeUtf(row.kind(), 32);
            b.writeUtf(row.status(), 32);
            b.writeVarInt(row.progress());
            b.writeVarInt(row.target());
            b.writeVarInt(row.nominalStanding());
            b.writeVarInt(row.availableStanding());
            b.writeLong(row.rewardCopper());
            b.writeBoolean(row.canAccept());
            b.writeBoolean(row.canDeliver());
            b.writeBoolean(row.canClaim());
            b.writeUtf(row.reason(), 256);
        }
        var r = p.permit();
        b.writeBoolean(r.known());
        b.writeBoolean(r.active());
        b.writeLong(r.expiresAt());
        b.writeLong(r.banUntil());
        b.writeLong(r.depositCopper());
        b.writeBoolean(r.initiated());
        b.writeBoolean(r.refundPending());
        b.writeLong(r.whiteFlagUntil());
        b.writeBoolean(r.canIssue());
        b.writeBoolean(r.canReturn());
        b.writeUtf(r.reason(), 256);
        b.writeVarInt(p.exchanges().size());
        for (var row : p.exchanges()) {
            b.writeEnum(row.from());
            b.writeEnum(row.to());
            b.writeLong(row.inputCopper());
            b.writeLong(row.outputCopper());
            b.writeVarInt(row.feePercent());
            b.writeBoolean(row.enabled());
            b.writeUtf(row.reason(), 256);
        }
        b.writeNbt(p.metadata());
    }

    public static Snapshot snapshot(FriendlyByteBuf b) {
        UUID token = b.readUUID();
        int revision = b.readVarInt();
        Page page = b.readEnum(Page.class);
        BlockPos anchor = b.readBlockPos();
        String faction = b.readUtf(128);
        boolean valid = b.readBoolean(), test = b.readBoolean();
        String reason = b.readUtf(256);
        UUID ack = b.readUUID();
        var wallets = new java.util.ArrayList<WalletRow>();
        for (int i = 0, n = size(b, 64); i < n; i++)
            wallets.add(
                    new WalletRow(
                            b.readUUID(),
                            b.readUtf(96),
                            b.readVarInt(),
                            b.readBoolean(),
                            b.readBoolean(),
                            List.of(b.readLong(), b.readLong(), b.readLong(), b.readLong())));
        var funds = new java.util.ArrayList<FundsRow>();
        for (int i = 0, n = size(b, 4); i < n; i++)
            funds.add(
                    new FundsRow(
                            b.readEnum(Currency.class), b.readLong(), b.readLong(), b.readLong()));
        var offers = new java.util.ArrayList<OfferRow>();
        for (int i = 0, n = size(b, 64); i < n; i++)
            offers.add(
                    new OfferRow(
                            b.readUtf(128),
                            b.readUtf(128),
                            b.readVarInt(),
                            b.readLong(),
                            b.readLong(),
                            b.readBoolean(),
                            b.readBoolean(),
                            b.readUtf(256),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readBoolean()));
        var tasks = new java.util.ArrayList<TaskRow>();
        for (int i = 0, n = size(b, 64); i < n; i++)
            tasks.add(
                    new TaskRow(
                            b.readUtf(128),
                            b.readUtf(128),
                            b.readUtf(32),
                            b.readUtf(32),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readVarInt(),
                            b.readLong(),
                            b.readBoolean(),
                            b.readBoolean(),
                            b.readBoolean(),
                            b.readUtf(256)));
        var permit =
                new PermitRow(
                        b.readBoolean(),
                        b.readBoolean(),
                        b.readLong(),
                        b.readLong(),
                        b.readLong(),
                        b.readBoolean(),
                        b.readBoolean(),
                        b.readLong(),
                        b.readBoolean(),
                        b.readBoolean(),
                        b.readUtf(256));
        var exchanges = new java.util.ArrayList<ExchangeRow>();
        for (int i = 0, n = size(b, 12); i < n; i++)
            exchanges.add(
                    new ExchangeRow(
                            b.readEnum(Currency.class),
                            b.readEnum(Currency.class),
                            b.readLong(),
                            b.readLong(),
                            b.readVarInt(),
                            b.readBoolean(),
                            b.readUtf(256)));
        CompoundTag metadata = b.readNbt(new NbtAccounter(65536));
        if (metadata == null) metadata = new CompoundTag();
        if (b.readableBytes() != 0) throw new IllegalArgumentException("trailing economy packet");
        return new Snapshot(
                token, revision, page, anchor, faction, valid, test, reason, wallets, funds, offers,
                tasks, permit, exchanges, metadata, ack);
    }
}
