package com.craftix.hostile_humans.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value={Beardifier.class})
public abstract class BeardifierMix {
    @Unique
    private static final ResourceLocation HOSTILE_HUMANS$FORTRESS_BOTTOM = new ResourceLocation("hostile_humans", "fortress_bottom");

    @Redirect(method={"m_223937_(Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/levelgen/Beardifier;"}, at=@At(value="INVOKE", target="Lnet/minecraft/world/level/StructureManager;m_220477_(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;"), remap=false, require=1)
    private static List<StructureStart> hostileHumans$filterFortressBottom(StructureManager structureManager, ChunkPos chunkPos, Predicate<Structure> structurePredicate) {
        return structureManager.startsForStructure(chunkPos, structurePredicate).stream().filter(start -> !BeardifierMix.hostileHumans$hasNearbyFortressBottom(start, chunkPos)).toList();
    }

    @Unique
    private static boolean hostileHumans$hasNearbyFortressBottom(StructureStart start, ChunkPos chunkPos) {
        for (StructurePiece piece : start.getPieces()) {
            PoolElementStructurePiece poolPiece;
            StructurePoolElement structurePoolElement;
            if (!piece.isCloseToChunk(chunkPos, 12) || !(piece instanceof PoolElementStructurePiece) || !((structurePoolElement = (poolPiece = (PoolElementStructurePiece)piece).getElement()) instanceof SinglePoolElement)) continue;
            SinglePoolElement singlePoolElement = (SinglePoolElement)structurePoolElement;
            if (!singlePoolElement.template.left().filter(arg_0 -> ((ResourceLocation)HOSTILE_HUMANS$FORTRESS_BOTTOM).equals(arg_0)).isPresent()) continue;
            return true;
        }
        return false;
    }
}

