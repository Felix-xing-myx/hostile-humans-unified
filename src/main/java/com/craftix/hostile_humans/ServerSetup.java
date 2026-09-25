package com.craftix.hostile_humans;

import com.craftix.hostile_humans.entity.data.HumanServerData;
import net.minecraftforge.event.server.ServerStartingEvent;

public class ServerSetup {
    protected ServerSetup() {
    }

    public static void handleServerStartingEvent(ServerStartingEvent event) {
        HumanServerData.prepare(event.getServer());
    }
}

