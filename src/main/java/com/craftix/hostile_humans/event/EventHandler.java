package com.craftix.hostile_humans.event;

import com.craftix.hostile_humans.HostileHumans;
import com.craftix.hostile_humans.compat.CollectiveVillagerNames;
import com.craftix.hostile_humans.compat.FarmersDelight;
import com.craftix.hostile_humans.entity.entities.Human;
import com.craftix.hostile_humans.entity.entities.HumanInventoryGenerator;
import java.util.List;
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
            for (Human human : event.getEntity().level().getEntitiesOfClass(Human.class, event.getEntity().getBoundingBox().inflate(16.0))) {
                human.setInvestigateSound(event.getEntity().blockPosition());
            }
        }
    }

    @SubscribeEvent
    public void serverStart(ServerStartedEvent event) {
        HostileHumans.patreonNames.forEach(name -> CollectiveVillagerNames.addCustomName(name));
        if (!addedFarmerItems && ModList.get().isLoaded("farmersdelight")) {
            FarmersDelight.addFoodItems();
            addedFarmerItems = true;
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (placer != null && !placer.level().isClientSide) {
            List<Entity> humans = placer.level().getEntities(placer, placer.getBoundingBox().inflate(10.0), entity -> {
                boolean bl;
                if (entity instanceof Human) {
                    Human otherHuman = (Human)entity;
                    bl = true;
                } else {
                    bl = false;
                }
                return bl;
            });
            for (Entity otherHuman : humans) {
                ((Human)otherHuman).isAlert = true;
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void onPlayerDamage(LivingDamageEvent event) {
        LivingEntity damaged = event.getEntity();
        if (damaged instanceof Player && !damaged.level().isClientSide) {
            List<Entity> humans = damaged.level().getEntities((Entity)damaged, damaged.getBoundingBox().inflate(10.0), entity -> {
                boolean bl;
                if (entity instanceof Human) {
                    Human otherHuman = (Human)entity;
                    bl = true;
                } else {
                    bl = false;
                }
                return bl;
            });
            for (Entity otherHuman : humans) {
                ((Human)otherHuman).isAlert = true;
            }
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
                for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
                    human.setItemSlot(equipmentslot, ItemStack.EMPTY);
                }
                HumanInventoryGenerator.generateInventory(human, tag.contains("ranged"));
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
            if (ModList.get().isLoaded("villagernames")) {
                CollectiveVillagerNames.nameEntity((Entity)human);
            }
        }
    }
}

