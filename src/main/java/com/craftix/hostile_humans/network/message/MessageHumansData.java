package com.craftix.hostile_humans.network.message;

import com.craftix.hostile_humans.entity.data.HumansClientData;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record MessageHumansData(CompoundTag data) {
    public static void handle(MessageHumansData message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> MessageHumansData.handlePacket(message)));
        context.setPacketHandled(true);
    }

    public static void handlePacket(MessageHumansData message) {
        HumansClientData.load(message.data());
    }
}

