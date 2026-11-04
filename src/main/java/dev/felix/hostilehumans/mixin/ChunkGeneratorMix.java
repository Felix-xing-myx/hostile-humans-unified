package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.entity.HumanUtil;
import dev.felix.hostilehumans.settlement.SettlementPackStructure;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.injection.Redirect;
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
        if (structureSelectionEntry.structure().value() instanceof SettlementPackStructure) return;
        Optional key = structureSelectionEntry.structure().unwrapKey();
        if (key.isPresent() && ((ResourceKey)key.get()).location().toString().contains("hostile_humans_unified")) {
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

    @Redirect(method = "tryGenerateStructure", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/Structure;generate(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;"))
    private StructureStart hhu$generateBuilding(Structure structure, RegistryAccess registries, ChunkGenerator generator,
            BiomeSource biomes, RandomState random, StructureTemplateManager templates, long seed, ChunkPos chunk,
            int references, LevelHeightAccessor height, Predicate<Holder<Biome>> allowed,
            StructureSet.StructureSelectionEntry entry, StructureManager manager, RegistryAccess contextRegistries,
            RandomState contextRandom, StructureTemplateManager contextTemplates, long contextSeed,
            ChunkAccess contextChunk, ChunkPos contextPosition, SectionPos contextSection) {
        if (structure instanceof SettlementPackStructure
                && ((StructureManagerLevelAccessor) manager).hhu$getLevel() instanceof ServerLevel level) {
            return SettlementPackStructure.withStartLevel(level, () ->
                    structure.generate(registries, generator, biomes, random, templates, seed, chunk, references, height, allowed));
        }
        return structure.generate(registries, generator, biomes, random, templates, seed, chunk, references, height, allowed);
    }

    @Unique
    private boolean isLegal(int x, int z, ChunkAccess chunkAccess, RandomState randomState, ChunkGenerator chunkGenerator) {
        int k = chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, (LevelHeightAccessor)chunkAccess, randomState);
        return k <= 78 && k >= 55;
    }
}

