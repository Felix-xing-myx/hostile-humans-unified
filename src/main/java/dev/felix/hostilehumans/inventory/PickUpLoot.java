package dev.felix.hostilehumans.inventory;

import dev.felix.hostilehumans.entity.HumanAbility;
import dev.felix.hostilehumans.entity.HumanEntity;
import dev.felix.hostilehumans.entity.HumanData;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.perception.PerceptionBudget;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="hostile_humans_unified")
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
        if (this.humanEntity instanceof Human human
                && !dev.felix.hostilehumans.inventory.SoldierPickupPolicy.canCollectNow(human)) {
            this.ticker = 0;
            return;
        }
        if (!this.level.isClientSide && radius > 0) {
            short s = this.ticker;
            this.ticker = (short)(s + 1);
            if (s >= TICK_RATE) {
                if (this.humanEntity instanceof Human human && human.isActivelyCollectingLoot()) {
                    // The active goal owns both navigation and this pile; do
                    // not consume its target behind its back or run a second scan.
                    this.ticker = 0;
                    return;
                }
                AABB area = new AABB(this.humanEntity.blockPosition()).inflate(radius);
                if (this.humanEntity instanceof Human human) {
                    if (!PerceptionBudget.claim(human, "queries", 1, ":nearby-loot-query")) {
                        this.ticker = TICK_RATE;
                        return;
                    }
                    for (ItemEntity itemEntity : HumanLootManager.nearbyCandidates(human, area, 12,
                            item -> human.distanceToSqr(item) <= 6.25D)) {
                        if (HumanLootManager.tryCollectNearby(human, itemEntity)) break;
                    }
                    this.ticker = 0;
                    return;
                }
                HumanData humanMobData;
                List<ItemEntity> itemEntities = this.level.getEntities((EntityTypeTest)EntityType.ITEM, area, entity -> true);
                if (!itemEntities.isEmpty() && (humanMobData = this.humanEntity.getData()) != null) {
                    for (ItemEntity itemEntity : itemEntities) {
                        if (itemEntity.isRemoved() || !itemEntity.isAlive() || !this.humanEntity.isAlive() || this.humanEntity.isDeadOrDying() || !humanMobData.storeInventoryItem(itemEntity.getItem())) continue;
                        this.humanEntity.take((Entity)itemEntity, itemEntity.getItem().getCount());
                        itemEntity.discard();
                    }
                }
                this.ticker = 0;
            }
        }
    }
}

