package com.craftix.hostile_humans.entity.type.human;

import com.craftix.hostile_humans.entity.HumanAbility;
import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.data.HumanData;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="hostile_humans")
public class PickUpLoot
extends HumanAbility {
    private static final short TICK_RATE = 60;
    private static int radius = 2;

    public PickUpLoot(HumanEntity humanEntity, Level level) {
        super(humanEntity, level);
    }

    @SubscribeEvent
    public static void handleServerAboutToStartEvent(ServerAboutToStartEvent event) {
        radius = 2;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level.isClientSide && radius > 0) {
            short s = this.ticker;
            this.ticker = (short)(s + 1);
            if (s >= 60) {
                HumanData humanMobData;
                List<ItemEntity> itemEntities = this.level.getEntities((EntityTypeTest)EntityType.ITEM, new AABB(this.humanEntity.blockPosition()).inflate((double)radius), entity -> true);
                if (!itemEntities.isEmpty() && (humanMobData = this.humanEntity.getData()) != null) {
                    for (ItemEntity itemEntity : itemEntities) {
                        if (itemEntity.isRemoved() || !itemEntity.isAlive() || !this.humanEntity.isAlive() || this.humanEntity.isDeadOrDying() || !humanMobData.storeInventoryItem(itemEntity.getItem())) continue;
                        ItemStack itemstack = itemEntity.getItem();
                        Item item = itemstack.getItem();
                        this.humanEntity.take((Entity)itemEntity, itemEntity.getItem().getCount());
                        itemEntity.discard();
                    }
                }
                this.ticker = 0;
            }
        }
    }
}

