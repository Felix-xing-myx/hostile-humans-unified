package dev.felix.hostilehumans.bootstrap;

import dev.felix.hostilehumans.entity.HumanServerData;
import net.minecraftforge.event.server.ServerStartingEvent;

public class ServerSetup {
    protected ServerSetup() {
    }

    public static void handleServerStartingEvent(ServerStartingEvent event) {
        HumanServerData.prepare(event.getServer());
    }
}

