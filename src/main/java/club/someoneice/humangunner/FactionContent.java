package club.someoneice.humangunner;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.ModEntityType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import java.util.*;

/** One catalog of built-in faction entities; no natural spawn entries or replacement AI. */
public final class FactionContent {
    public static final String SPAWNER_BIRTH = HumanGunner.MOD_ID + ":faction_spawner_birth";
    static final String AUTHORED_EQUIPMENT = HumanGunner.MOD_ID + ":faction_authored_equipment";
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HumanGunner.MOD_ID);
    public static final RegistryObject<Block> TRIBUTE_CHEST = BLOCKS.register("tribute_chest", () -> new Block(
            net.minecraft.world.level.block.state.BlockBehaviour.Properties.copy(Blocks.OAK_PLANKS).strength(2.5F).sound(SoundType.WOOD)));
    public record Boss(String faction, int tier, RegistryObject<EntityType<FactionBossHuman>> type) {}
    public static final List<Boss> BOSSES = bosses();
    private FactionContent() {}
    /** Spawners carrying EntityTag bypass vanilla finalizeSpawn. Consume only an explicit birth marker. */
    static void onJoin(Human human, boolean fromDisk) {
        var data = human.getPersistentData();
        boolean spawnerBirth = data.getBoolean(SPAWNER_BIRTH), authored = data.getBoolean(AUTHORED_EQUIPMENT);
        data.remove(SPAWNER_BIRTH); data.remove(AUTHORED_EQUIPMENT);
        if (!fromDisk && authored) HumanSpawnEquipment.preserveAuthored(human);
        if (fromDisk || !spawnerBirth || authored) return;
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values())
            if (!human.getItemBySlot(slot).isEmpty()) return;
        HumanSpawnEquipment.generate(human, false);
    }
    private static List<Boss> bosses() {
        List<Boss> values = new ArrayList<>();
        for (String faction : List.of("crimson", "azure", "verdant", "ashen")) for (int rank = 1; rank <= 3; rank++) {
            final int tier = rank;
            String name = "faction_" + faction + "_tier" + tier + "_boss";
            var type = ModEntityType.ENTITIES.register(name, () -> EntityType.Builder
                    .<FactionBossHuman>of((entityType, level) -> new FactionBossHuman(entityType, level, tier, faction), HumanEntity.CATEGORY)
                    .sized(.6F, 1.8F).clientTrackingRange(16).build(HumanGunner.MOD_ID + ":" + name));
            values.add(new Boss(faction, tier, type));
        }
        return List.copyOf(values);
    }
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        modBus.addListener(FactionContent::attributes);
    }
    static List<RegistryObject<Item>> registerOrdinaryEggs(DeferredRegister<Item> items) {
        List<RegistryObject<Item>> eggs=new ArrayList<>();
        for(String faction:List.of("crimson","azure","verdant","ashen"))for(int tier=1;tier<=3;tier++) {
            final int rank=tier;
            eggs.add(items.register("faction_"+faction+"_tier"+rank+"_spawn_egg",()->new FactionSpawnEggItem(
                    () -> ordinaryType(rank),faction,false,baseColor(rank),spotColor(rank),new Item.Properties().rarity(Rarity.EPIC))));
        }
        return List.copyOf(eggs);
    }
    static List<RegistryObject<Item>> registerBossEggs(DeferredRegister<Item> items) {
        List<RegistryObject<Item>> eggs=new ArrayList<>();
        for(Boss boss:BOSSES)eggs.add(items.register("faction_"+boss.faction()+"_tier"+boss.tier()+"_boss_spawn_egg",()->new FactionSpawnEggItem(
                boss.type(),boss.faction(),true,baseColor(boss.tier()),spotColor(boss.tier()),new Item.Properties().rarity(Rarity.EPIC))));
        return List.copyOf(eggs);
    }
    static EntityType<? extends Human> ordinaryType(int tier) {
        return switch(tier) { case 1 -> ModEntityType.HUMAN1.get();case 2 -> ModEntityType.HUMAN2.get();case 3 -> ModEntityType.HUMAN3.get();default -> throw new IllegalArgumentException("Human tier"); };
    }
    /** Optional thin Human#getPickResult hook: vanilla's type-only egg map cannot retain faction variants. */
    public static net.minecraft.world.item.ItemStack pickEgg(Human human) {
        List<RegistryObject<Item>> eggs=human instanceof FactionBossHuman?HumanGunnerRegistries.BOSS_SPAWN_EGGS:HumanGunnerRegistries.FACTION_SPAWN_EGGS;
        String faction=HumanFactions.id(human);
        for(var registered:eggs) {
            var egg=(FactionSpawnEggItem)registered.get();
            if(egg.faction().equals(faction)&&egg.getType(null)==human.getType())return new net.minecraft.world.item.ItemStack(egg);
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
    private static int baseColor(int tier) { return tier==3?0x24304A:net.minecraft.world.level.material.MapColor.COLOR_ORANGE.col; }
    private static int spotColor(int tier) { return tier==3?0xB88A2B:tier==2?net.minecraft.world.level.material.MapColor.GOLD.col:net.minecraft.world.level.material.MapColor.TERRACOTTA_WHITE.col; }
    private static void attributes(EntityAttributeCreationEvent event) {
        for (Boss boss : BOSSES) {
            var builder = Human.createAttributes();
            if (boss.tier() == 3) builder.add(Attributes.MAX_HEALTH, 50).add(Attributes.FOLLOW_RANGE, 48).add(Attributes.KNOCKBACK_RESISTANCE, .15);
            event.put(boss.type().get(), builder.build());
        }
    }
}
