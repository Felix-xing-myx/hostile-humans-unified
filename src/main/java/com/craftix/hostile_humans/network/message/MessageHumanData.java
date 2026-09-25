package com.craftix.hostile_humans.network.message;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public class MessageHumanData {
    protected final CompoundTag data;
    protected final String humanMobEntityUUID;

    public MessageHumanData(String humanMobEntityUUID, CompoundTag data) {
        this.humanMobEntityUUID = humanMobEntityUUID;
        this.data = data;
    }

    public static void handle(MessageHumanData message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> MessageHumanData.handlePacket(message)));
        context.setPacketHandled(true);
    }

    public static void handlePacket(MessageHumanData message) {
        com.craftix.hostile_humans.entity.data.HumansClientData.load(message.data);
    }

    public CompoundTag getData() {
        return this.data;
    }

    public String getHHFollowerUUID() {
        return this.humanMobEntityUUID;
    }
}

