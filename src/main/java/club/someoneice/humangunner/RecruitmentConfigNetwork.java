package club.someoneice.humangunner;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/** The same server snapshot drives both deductions and the client's contract tooltip. */
final class RecruitmentConfigNetwork {
    private static final class ChannelHolder {
        static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(HumanGunner.MOD_ID, "recruitment_terms"), () -> "1", "1"::equals, "1"::equals);
    }
    private static volatile Snapshot serverTerms;

    record Price(UnifiedConfig.RecruitmentCost payment, int limit, Component name) {}
    record Snapshot(List<Price> prices) {
        Snapshot {
            prices = List.copyOf(prices);
            if (prices.size() != 4) throw new IllegalArgumentException("Exactly four recruitment tiers required");
        }
        Price tier(int tier) { return prices.get(tier); }
    }

    static void register() {
        ChannelHolder.CHANNEL.registerMessage(0, Snapshot.class, RecruitmentConfigNetwork::encode,
                RecruitmentConfigNetwork::decode, RecruitmentConfigNetwork::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }

    static Snapshot snapshot(UnifiedConfig config) {
        List<Price> prices = new ArrayList<>(4);
        for (int tier = 0; tier < 4; tier++) {
            var payment = config.recruitmentCost(tier);
            Item currency = currency(payment);
            prices.add(new Price(currency == null ? null : payment, config.recruitmentLimit(tier),
                    currency == null ? Component.translatable("message.humangunner.hire.invalid_payment")
                            : currency.getDescription()));
        }
        return new Snapshot(prices);
    }

    static Item currency(UnifiedConfig.RecruitmentCost payment) {
        if (payment == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(payment.itemId());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }

    static Price serverPrice(int tier) {
        Snapshot terms = serverTerms;
        if (terms == null) throw new IllegalStateException("Recruitment server terms not initialized");
        return terms.tier(tier);
    }

    static Price serverTooltipPrice(int tier) {
        Snapshot terms = serverTerms;
        // Tooltips can be queried by integrations before a server starts.
        // This preview must never be used for the actual payment transaction.
        return (terms == null ? snapshot(UnifiedConfig.get()) : terms).tier(tier);
    }

    static boolean pay(Price terms, List<net.minecraft.world.item.ItemStack> inventory,
                       net.minecraft.world.item.ItemStack contract) {
        Item currency = currency(terms.payment());
        if (currency == null) return false;
        return RecruitmentPayment.pay(false, terms.payment().count(), inventory.size(),
                slot -> inventory.get(slot).is(currency)
                        ? Math.max(0, inventory.get(slot).getCount() - (inventory.get(slot) == contract ? 1 : 0)) : 0,
                (slot, count) -> inventory.get(slot).shrink(count));
    }

    static void onServerStarting(ServerAboutToStartEvent event) {
        // Do not reuse a client tooltip's early-load cache or a previous
        // integrated server's settings. This also upgrades missing schema keys.
        serverTerms = snapshot(UnifiedConfig.reloadForServer());
        com.mojang.logging.LogUtils.getLogger().info("Loaded authoritative recruitment terms from {}",
                FMLPaths.CONFIGDIR.get().resolve(SplitConfigFiles.DIRECTORY).resolve("recruitment.json"));
        for (int tier = 0; tier < 4; tier++) {
            Price price = serverTerms.tier(tier);
            com.mojang.logging.LogUtils.getLogger().info("Recruitment tier {}: payment={}, limit={}",
                    tier, price.payment(), price.limit());
        }
    }

    static void onServerStopped(ServerStoppedEvent event) { serverTerms = null; }

    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            ChannelHolder.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), serverTerms);
    }

    static void encode(Snapshot snapshot, FriendlyByteBuf buffer) {
        for (Price price : snapshot.prices()) {
            buffer.writeBoolean(price.payment() != null);
            if (price.payment() != null) {
                buffer.writeUtf(price.payment().itemId(), 32767);
                buffer.writeVarInt(price.payment().count());
            }
            buffer.writeVarInt(price.limit());
            buffer.writeComponent(price.name());
        }
    }

    static Snapshot decode(FriendlyByteBuf buffer) {
        List<Price> prices = new ArrayList<>(4);
        for (int tier = 0; tier < 4; tier++) {
            UnifiedConfig.RecruitmentCost payment = null;
            if (buffer.readBoolean()) {
                String id = buffer.readUtf(32767);
                int count = buffer.readVarInt();
                if (ResourceLocation.tryParse(id) == null || !id.contains(":") || count < 0 || count > 1_000_000)
                    throw new IllegalArgumentException("Invalid synchronized recruitment payment");
                payment = new UnifiedConfig.RecruitmentCost(id, count);
            }
            int limit = buffer.readVarInt();
            if (limit < 0 || limit > 10000) throw new IllegalArgumentException("Invalid synchronized hiring limit");
            prices.add(new Price(payment, limit, buffer.readComponent()));
        }
        return new Snapshot(prices);
    }

    private static void handle(Snapshot packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RecruitmentConfigClient.accept(context.getNetworkManager(), packet)));
        context.setPacketHandled(true);
    }
}
