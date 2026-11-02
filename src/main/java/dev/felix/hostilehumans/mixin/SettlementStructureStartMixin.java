package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.settlement.SettlementGeneration;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureStart.class)
public abstract class SettlementStructureStartMixin {
    @Inject(method = "placeInChunk", at = @At("HEAD"), require = 1)
    private void hhu$beginSettlementRoot(
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunk,
            CallbackInfo ci) {
        SettlementGeneration.beginRoot((StructureStart) (Object) this, level);
    }

    @Inject(method = "placeInChunk", at = @At("RETURN"), require = 1)
    private void hhu$endSettlementRoot(
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox bounds,
            ChunkPos chunk,
            CallbackInfo ci) {
        SettlementGeneration.endRoot();
    }
}
