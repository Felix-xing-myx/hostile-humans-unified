package com.craftix.hostile_humans.mixin;

import com.craftix.hostile_humans.entity.entities.ChestExtension;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ChestLidController;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value={ChestBlockEntity.class})
public class ChestBlockEnityMixin
implements ChestExtension {
    @Shadow(remap=false)
    @Final
    private ContainerOpenersCounter f_155324_;
    @Shadow(remap=false)
    @Final
    private ChestLidController f_155325_;

    @Override
    public ContainerOpenersCounter openersCounter() {
        return this.f_155324_;
    }

    @Override
    public void hostileHumans$setForcedOpen(boolean open) {
        this.f_155325_.shouldBeOpen(open);
    }
}

