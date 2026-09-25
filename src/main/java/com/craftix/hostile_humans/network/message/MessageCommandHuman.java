package com.craftix.hostile_humans.network.message;

import com.craftix.hostile_humans.entity.HumanCommand;
import com.craftix.hostile_humans.entity.HumanEntity;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

public class MessageCommandHuman {
    protected final String humanMobEntityUUID;
    protected final String command;

    public MessageCommandHuman(String humanMobEntityUUID, String command) {
        this.humanMobEntityUUID = humanMobEntityUUID;
        this.command = command;
    }

    public static void handle(MessageCommandHuman message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> MessageCommandHuman.handlePacket(message, context));
        context.setPacketHandled(true);
    }

    public static void handlePacket(MessageCommandHuman message, NetworkEvent.Context context) {
        ServerPlayer sender = context.getSender();
        if (sender == null || message == null || message.command == null
                || message.command.length() > 32 || message.humanMobEntityUUID == null
                || message.humanMobEntityUUID.length() != 36) return;
        try {
            UUID uuid = UUID.fromString(message.humanMobEntityUUID);
            HumanCommand command = HumanCommand.valueOf(message.command);
            Entity entity = sender.serverLevel().getEntity(uuid);
            if (entity instanceof HumanEntity human && human.isAlive()
                    && sender.getUUID().equals(human.getOwnerUUID())
                    && sender.distanceToSqr(human) <= 64.0 * 64.0
                    && club.someoneice.humangunner.CommandRateLimit.allow(sender)) {
                human.handleCommand(command);
            }
        } catch (IllegalArgumentException ignored) {
            // Untrusted command text is never allowed to crash the server task queue.
        }
    }

    public String getCommand() {
        return this.command;
    }

    public String getHHFollowerUUID() {
        return this.humanMobEntityUUID;
    }
}

