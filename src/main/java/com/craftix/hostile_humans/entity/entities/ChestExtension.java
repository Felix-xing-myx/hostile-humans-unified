package com.craftix.hostile_humans.entity.entities;

import net.minecraft.world.level.block.entity.ContainerOpenersCounter;

public interface ChestExtension {
    public ContainerOpenersCounter openersCounter();

    public void hostileHumans$setForcedOpen(boolean var1);
}

