package com.craftix.hostile_humans.mixin;

import com.craftix.hostile_humans.HumanUtil;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ChunkGenerator.class})
public abstract class ChunkGeneratorMix {
    @Inject(method={"tryGenerateStructure"}, at={@At(value="HEAD")}, cancellable=true)
    private void injected(StructureSet.StructureSelectionEntry structureSelectionEntry, StructureManager structureManager, RegistryAccess registryAccess, RandomState randomState, StructureTemplateManager structureTemplateManager, long seed, ChunkAccess chunkAccess, ChunkPos chunkPos, SectionPos sectionPos, CallbackInfoReturnable<Boolean> cir) {
        Optional key = structureSelectionEntry.structure().unwrapKey();
        if (key.isPresent() && ((ResourceKey)key.get()).location().toString().contains("hostile_humans")) {
            ChunkGenerator chunkGenerator;
            int z;
            int x = chunkPos.getMiddleBlockX();
            if (!(this.isLegal(x, z = chunkPos.getMiddleBlockZ(), chunkAccess, randomState, chunkGenerator = (ChunkGenerator)(Object)this) && this.isLegal(x - 16, z, chunkAccess, randomState, chunkGenerator) && this.isLegal(x + 16, z, chunkAccess, randomState, chunkGenerator) && this.isLegal(x, z - 16, chunkAccess, randomState, chunkGenerator) && this.isLegal(x, z + 16, chunkAccess, randomState, chunkGenerator))) {
                cir.setReturnValue(false);
                return;
            }
            if (HumanUtil.isStructureDisabled(((ResourceKey)key.get()).location().getPath())) {
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Unique
    private boolean isLegal(int x, int z, ChunkAccess chunkAccess, RandomState randomState, ChunkGenerator chunkGenerator) {
        int k = chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, (LevelHeightAccessor)chunkAccess, randomState);
        return k <= 78 && k >= 55;
    }
}

