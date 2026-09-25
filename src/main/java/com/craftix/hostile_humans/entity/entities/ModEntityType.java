package com.craftix.hostile_humans.entity.entities;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanTier;
import com.craftix.hostile_humans.entity.entities.SpawnerEntity;
import club.someoneice.humangunner.TierThreeHuman;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid="hostile_humans", bus=Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityType {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ENTITY_TYPES, (String)"hostile_humans");
    public static final RegistryObject<EntityType<Human>> HUMAN1 = ENTITIES.register("human_tier1", () -> EntityType.Builder.<Human>of((entityEntityType, level) -> new Human((EntityType<? extends HumanEntity>)entityEntityType, level, HumanTier.LEVEL1), (MobCategory)HumanEntity.CATEGORY).sized(0.6f, 1.8f).clientTrackingRange(16).build("human_tier1"));
    public static final RegistryObject<EntityType<Human>> HUMAN2 = ENTITIES.register("human_tier2", () -> EntityType.Builder.<Human>of((entityEntityType, level) -> new Human((EntityType<? extends HumanEntity>)entityEntityType, level, HumanTier.LEVEL2), (MobCategory)HumanEntity.CATEGORY).sized(0.6f, 1.8f).clientTrackingRange(16).build("human_tier2"));
    public static final RegistryObject<EntityType<TierThreeHuman>> HUMAN3 = ENTITIES.register("human_tier3", () -> EntityType.Builder.of(TierThreeHuman::new, (MobCategory)HumanEntity.CATEGORY).sized(0.6f, 1.8f).clientTrackingRange(16).build("human_tier3"));
    public static final RegistryObject<EntityType<Human>> ROAMER = ENTITIES.register("human_roamer", () -> EntityType.Builder.<Human>of((entityEntityType, level) -> new Human((EntityType<? extends HumanEntity>)entityEntityType, level, HumanTier.ROAMER), (MobCategory)HumanEntity.CATEGORY).sized(0.6f, 1.8f).clientTrackingRange(16).build("human_roamer"));
    public static final RegistryObject<EntityType<SpawnerEntity>> SPAWNER_ENTITY = ENTITIES.register("human_group", () -> EntityType.Builder.of(SpawnerEntity::new, (MobCategory)MobCategory.MONSTER).sized(0.6f, 1.8f).clientTrackingRange(16).build("human_group"));

    protected ModEntityType() {
    }

    @SubscribeEvent
    public static void entityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put((EntityType)HUMAN1.get(), Human.createAttributes().build());
        event.put((EntityType)HUMAN2.get(), Human.createAttributes().build());
        event.put((EntityType)HUMAN3.get(), Human.createAttributes().add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 50.0D).add(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE, 48.0D).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 0.15D).build());
        event.put((EntityType)ROAMER.get(), Human.createAttributes().build());
        event.put((EntityType)SPAWNER_ENTITY.get(), Human.createAttributes().build());
    }
}

