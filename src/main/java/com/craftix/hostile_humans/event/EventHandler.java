package com.craftix.hostile_humans.event;

import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.compat.FarmersDelight;
import com.craftix.hostile_humans.entity.entities.Human;
import club.someoneice.humangunner.HumanAwareness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

public class EventHandler {
    static boolean addedFarmerItems;
    private static final String randomTag = "hostile_humans:structure_gear_initialized";

    @SubscribeEvent
    public void explode(ExplosionEvent.Detonate event) {
        if (!event.getLevel().isClientSide()) {
            BlockPos soundPos = BlockPos.containing((Position)event.getExplosion().getPosition());
            AABB soundRange = new AABB(soundPos).inflate(16.0D);
            for (Human human : event.getLevel().getEntitiesOfClass(Human.class, soundRange)) {
                human.setInvestigateSound(soundPos);
            }
        }
    }

    @SubscribeEvent
    public void damage(LivingDamageEvent event) {
        if (!event.getEntity().level().isClientSide) {
            LivingEntity source = event.getEntity();
            HumanAwareness.investigate(source, source.blockPosition());
        }
    }

    @SubscribeEvent
    public void serverStart(ServerStartedEvent event) {
        if (!addedFarmerItems && ModList.get().isLoaded("farmersdelight")) {
            FarmersDelight.addFoodItems();
            addedFarmerItems = true;
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (placer != null && !placer.level().isClientSide) {
            HumanAwareness.alert(placer, true);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onPlayerDamage(LivingDamageEvent event) {
        LivingEntity damaged = event.getEntity();
        if (damaged instanceof Player && !damaged.level().isClientSide) {
            HumanAwareness.alert(damaged, false);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void joinWorld(EntityJoinLevelEvent event) {
        String tag;
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity.hasCustomName() && (tag = entity.getCustomName().getString()).contains("give_random_gear") && !entity.getTags().contains(this.randomTag)) {
            if (entity instanceof Human) {
                Human human = (Human)entity;
                human.setHomePos(human.blockPosition());
                club.someoneice.humangunner.HumanSpawnEquipment.generateRequested(human, tag.contains("ranged"));
                entity.setCustomName(null);
            }
            if (entity instanceof ArmorStand) {
                ArmorStand stand = (ArmorStand)entity;
                if (((ArmorStand)entity).getRandom().nextFloat() < 0.2f) {
                    int[] armorstandArmor = new int[]{0, 1, 2, 0, 1, 2, 3, 3, 3};
                    int staticPick = armorstandArmor[stand.getRandom().nextInt(armorstandArmor.length)];
                    for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
                        Item item = Mob.getEquipmentForSlot((EquipmentSlot)equipmentslot, (int)staticPick);
                        if (item == null) continue;
                        ItemStack is = item.getDefaultInstance();
                        stand.setItemSlot(equipmentslot, is);
                    }
                    entity.setCustomName(null);
                }
            }
            entity.addTag(this.randomTag);
        }
        if (entity instanceof Human) {
            Human human = (Human)entity;
            if (ModList.get().isLoaded("villagernames") && !human.hasCustomName()
                    && !HostileHumans.patreonNames.isEmpty()) {
                var names = HostileHumans.patreonNames;
                human.setCustomName(net.minecraft.network.chat.Component.literal(
                        names.get(human.getRandom().nextInt(names.size()))));
            }
        }
    }
}

