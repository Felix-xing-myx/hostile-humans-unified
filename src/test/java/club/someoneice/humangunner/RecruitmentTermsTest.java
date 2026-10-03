package club.someoneice.humangunner;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;

/** Real item transactions and packet codecs, without starting a game or server. */
public final class RecruitmentTermsTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        var flag = Bootstrap.class.getDeclaredField("isBootstrapped");
        flag.setAccessible(true);
        flag.setBoolean(null, true);
        Item modCurrency = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation("test", "currency"),
                new Item(new Item.Properties()));
        BuiltInRegistries.bootStrap();
        var server = RecruitmentConfigNetwork.snapshot(new UnifiedConfig(JsonParser.parseString("""
                {"recruitment":{"max_hired_by_tier":{"tier1":7},"payment_by_tier":{
                 "roamer":{"item":"minecraft:diamond","count":5},
                 "tier1":{"item":"test:currency","count":11},
                 "tier2":{"item":"test:missing_item","count":1},
                 "tier3":{"item":"minecraft:gold_ingot","count":0}}}}
                """).getAsJsonObject()));
        var client = RecruitmentConfigNetwork.snapshot(new UnifiedConfig(new com.google.gson.JsonObject()));
        check(client.tier(1).payment().itemId().equals("minecraft:emerald"), "client local default differs");
        List<ItemStack> inventory = new ArrayList<>(List.of(new ItemStack(Items.EMERALD, 64),
                new ItemStack(modCurrency, 4), new ItemStack(modCurrency, 10)));
        inventory.get(1).setHoverName(Component.literal("Custom NBT does not change currency identity"));
        check(RecruitmentConfigNetwork.pay(server.tier(1), inventory, ItemStack.EMPTY), "mod currency payment succeeds");
        check(inventory.get(0).getCount() == 64 && inventory.get(1).isEmpty()
                && inventory.get(2).getCount() == 3, "only configured currency removed across stacks, not emeralds");
        check(!RecruitmentConfigNetwork.pay(server.tier(1), inventory, ItemStack.EMPTY), "insufficient custom currency rejected");
        check(inventory.get(0).getCount() == 64 && inventory.get(2).getCount() == 3,
                "failed payment does not alter any stack");
        check(server.tier(2).payment() == null
                && !RecruitmentConfigNetwork.pay(server.tier(2), inventory, ItemStack.EMPTY),
                "unregistered currency cannot silently charge emeralds");
        check(RecruitmentConfigNetwork.pay(server.tier(3), inventory, ItemStack.EMPTY)
                && inventory.get(0).getCount() == 64, "zero additional cost preserves inventory");
        ItemStack contract = new ItemStack(Items.DIAMOND, 5);
        check(!RecruitmentConfigNetwork.pay(server.tier(0), List.of(contract), contract)
                && contract.getCount() == 5, "one held contract reserved when it is also currency");
        contract.setCount(6);
        check(RecruitmentConfigNetwork.pay(server.tier(0), List.of(contract), contract)
                && contract.getCount() == 1, "exact currency payment leaves one contract to consume separately");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecruitmentConfigNetwork.encode(server, buffer);
            var wire = RecruitmentConfigNetwork.decode(buffer);
            for (int tier = 0; tier < 4; tier++) {
                check(java.util.Objects.equals(wire.tier(tier).payment(), server.tier(tier).payment()),
                        "wire retains server item and amount for tier " + tier);
                check(wire.tier(tier).limit() == server.tier(tier).limit()
                        && wire.tier(tier).name().equals(server.tier(tier).name()),
                        "wire retains server limit and localized item name for tier " + tier);
            }
            check(buffer.readableBytes() == 0, "packet consumed exactly");
            RecruitmentTermsCache cache = new RecruitmentTermsCache();
            Object first = new Object();
            Object second = new Object();
            check(cache.price(first, 1) == null, "pending sync does not claim a client-local price");
            cache.accept(first, first, wire);
            check(cache.price(first, 1).payment().itemId().equals("test:currency")
                    && cache.price(first, 1).payment().count() == 11
                    && cache.price(first, 1).limit() == 7, "tooltip view uses server currency and limit");
            check(cache.price(second, 1) == null, "old server prices never leak to a new connection");
            cache.accept(first, second, wire);
            check(cache.price(second, 1) == null, "late packet from old connection ignored");
            cache.accept(second, second, client);
            check(cache.price(second, 1).payment().itemId().equals("minecraft:emerald"),
                    "new server replaces the previous server's terms");
            cache.clear();
            check(cache.price(second, 1) == null && cache.price(null, 1) == null, "logout clears terms");
        } finally { buffer.release(); }
        for (int badCount : new int[]{-1, 1_000_001}) {
            FriendlyByteBuf bad = new FriendlyByteBuf(Unpooled.buffer());
            try {
                bad.writeBoolean(true);
                bad.writeUtf("minecraft:diamond");
                bad.writeVarInt(badCount);
                try { RecruitmentConfigNetwork.decode(bad); throw new AssertionError("Malformed amount accepted"); }
                catch (IllegalArgumentException expected) { assertions++; }
            } finally { bad.release(); }
        }
        System.out.println("RecruitmentTermsTest: " + assertions + " real payment, codec and connection-cache checks passed");
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
