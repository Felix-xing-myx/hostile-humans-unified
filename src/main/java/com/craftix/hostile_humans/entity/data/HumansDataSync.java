package com.craftix.hostile_humans.entity.data;

import com.craftix.hostile_humans.entity.HumanEntity;
import com.craftix.hostile_humans.entity.data.HumanData;
import com.craftix.hostile_humans.entity.data.HumanServerData;
import java.util.UUID;

public interface HumansDataSync {
    public HumanEntity getSyncReference();

    public boolean getDataSyncNeeded();

    public void setDataSyncNeeded(boolean var1);

    default public boolean hasSyncReference() {
        return this.getSyncReference() != null;
    }

    default public void syncData() {
        if (this.hasSyncReference()) {
            this.syncData(this.getSyncReference());
            this.setDataSyncNeeded(false);
        }
    }

    default public void registerData() {
        if (this.hasSyncReference()) {
            this.registerData(this.getSyncReference());
            this.setDataSyncNeeded(false);
        }
    }

    default public boolean syncDataIfNeeded() {
        if (this.getDataSyncNeeded()) {
            this.syncData();
            return true;
        }
        return false;
    }

    default public void syncData(HumanEntity humanEntity) {
        HumanManagerEventHandler.queue(humanEntity);
    }

    default public void registerData(HumanEntity humanEntity) {
        HumanServerData serverData = this.getServerData();
        if (serverData != null) {
            serverData.registerHumanMob(humanEntity);
        }
    }

    default public HumanData getData(UUID uuid) {
        HumanServerData serverData = this.getServerData();
        if (serverData == null || uuid == null) {
            return null;
        }
        return serverData.getHumanMob(uuid);
    }

    default public HumanServerData getServerData() {
        return HumanServerData.get();
    }
}

