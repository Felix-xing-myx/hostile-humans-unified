package dev.felix.hostilehumans.mixin;

import dev.felix.hostilehumans.settlement.SettlementGeneration;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(StructureTemplate.class)
public abstract class SettlementTemplatePlacementMixin {
    @Inject(method = "processEntityInfos", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static void hhu$transformResidentAnchors(StructureTemplate template,
            net.minecraft.world.level.LevelAccessor level, BlockPos origin, StructurePlaceSettings settings,
            List<StructureTemplate.StructureEntityInfo> original,
            CallbackInfoReturnable<List<StructureTemplate.StructureEntityInfo>> cir) {
        if (!(level instanceof ServerLevelAccessor server)) return;
        var processed = cir.getReturnValue();
        var residents = dev.felix.hostilehumans.settlement.SettlementHumanTemplates.place(
                processed, origin, settings, server.getLevel().dimension().location());
        if (residents != processed) cir.setReturnValue(residents);
    }

    @Inject(method = "placeInWorld", at = @At("HEAD"), require = 1)
    private void hhu$beginPlacement(
            ServerLevelAccessor level,
            BlockPos origin,
            BlockPos reference,
            StructurePlaceSettings settings,
            RandomSource random,
            int flags,
            CallbackInfoReturnable<Boolean> cir) {
        SettlementGeneration.beginTemplate(
                (StructureTemplate) (Object) this, level, origin, settings);
    }

    @Inject(method = "placeInWorld", at = @At("RETURN"), require = 1)
    private void hhu$endPlacement(
            ServerLevelAccessor level,
            BlockPos origin,
            BlockPos reference,
            StructurePlaceSettings settings,
            RandomSource random,
            int flags,
            CallbackInfoReturnable<Boolean> cir) {
        SettlementGeneration.endTemplate(cir.getReturnValue());
    }

    @Inject(
            method =
                    "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            at = @At("RETURN"),
            remap = false,
            require = 1)
    private static void hhu$actualProcessedCarriers(
            ServerLevelAccessor level,
            BlockPos origin,
            BlockPos reference,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> original,
            StructureTemplate template,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir) {
        SettlementGeneration.processed(template, cir.getReturnValue());
    }
}
