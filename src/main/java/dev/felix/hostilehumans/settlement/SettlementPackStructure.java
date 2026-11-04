package dev.felix.hostilehumans.settlement;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Native structure start and persisted template piece for an approved building pack. */
public final class SettlementPackStructure extends Structure {
    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, HumanRuntime.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, HumanRuntime.MOD_ID);

    private static final Gson JSON = new Gson();
    private static final ThreadLocal<ServerLevel> START_LEVEL = new ThreadLocal<>();
    private static final Codec<SettlementGenerationRules> RULES = Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
        try {
            var rules = JSON.fromJson(dynamic.convert(JsonOps.INSTANCE).getValue(), SettlementGenerationRules.class);
            return rules == null ? DataResult.error(() -> "missing building generation rules") : DataResult.success(rules);
        } catch (RuntimeException invalid) {
            return DataResult.error(() -> "invalid building generation rules: " + invalid.getMessage());
        }
    }, rules -> new Dynamic<>(JsonOps.INSTANCE, JSON.toJsonTree(rules)));

    public static final Codec<SettlementPackStructure> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            settingsCodec(builder),
            ResourceLocation.CODEC.fieldOf("template").forGetter(value -> value.template),
            RULES.fieldOf("rules").forGetter(value -> value.rules),
            RegistryOps.retrieveRegistryLookup(Registries.BIOME).forGetter(value -> value.biomes)
    ).apply(builder, SettlementPackStructure::new));
    private static final RegistryObject<StructureType<?>> TYPE = TYPES.register("building_pack", () -> (StructureType<SettlementPackStructure>) () -> CODEC);
    private static final RegistryObject<StructurePieceType> PIECE = PIECES.register("building_pack",
            () -> (StructurePieceType.StructureTemplateType) Piece::new);

    private final ResourceLocation template;
    private final SettlementGenerationRules rules;
    private final HolderLookup.RegistryLookup<Biome> biomes;

    private SettlementPackStructure(StructureSettings settings, ResourceLocation template, SettlementGenerationRules rules,
            HolderLookup.RegistryLookup<Biome> biomes) {
        super(new StructureSettings(HolderSet.direct(biomes.listElements().toList()),
                settings.spawnOverrides(), settings.step(), settings.terrainAdaptation()));
        this.template = template;
        this.rules = rules;
        this.biomes = biomes;
    }

    public static <T> T withStartLevel(ServerLevel level, Supplier<T> action) {
        ServerLevel previous = START_LEVEL.get();
        START_LEVEL.set(level);
        try {
            return action.get();
        } finally {
            if (previous == null) START_LEVEL.remove();
            else START_LEVEL.set(previous);
        }
    }

    @Override public StructureType<?> type() {
        return TYPE.get();
    }

    @Override public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        return findGenerationPoint(context);
    }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        var level = START_LEVEL.get();
        if (!rules.enabled() || level == null || !level.dimension().location().toString().equals(rules.dimension())
                || rules.minimumDay() > 0 && level.getDayTime() / 24000L < rules.minimumDay())
            return Optional.empty();

        var source = context.structureTemplateManager().get(template).orElse(null);
        if (source == null) return Optional.empty();
        var size = source.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
                || size.getX() > SettlementWorldgenResources.MAX_HORIZONTAL_SIDE
                || size.getZ() > SettlementWorldgenResources.MAX_HORIZONTAL_SIDE) return Optional.empty();

        Rotation rotation = Rotation.getRandom(context.random());
        var zero = source.getBoundingBox(Piece.settings(rotation), BlockPos.ZERO);
        int x = context.chunkPos().getMiddleBlockX();
        int z = context.chunkPos().getMiddleBlockZ();
        int minX = x - zero.getXSpan() / 2;
        int minZ = z - zero.getZSpan() / 2;
        int maxX = minX + zero.getXSpan() - 1;
        int maxZ = minZ + zero.getZSpan() - 1;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int sampleX : new int[] {minX, x, maxX}) {
            for (int sampleZ : new int[] {minZ, z, maxZ}) {
                int height = height(context, sampleX, sampleZ, Heightmap.Types.WORLD_SURFACE_WG);
                low = Math.min(low, height);
                high = Math.max(high, height);
                boolean wet = height > height(context, sampleX, sampleZ, Heightmap.Types.OCEAN_FLOOR_WG);
                if (rules.water() == SettlementGenerationRules.Water.DRY && wet
                        || rules.water() == SettlementGenerationRules.Water.WATER && !wet) return Optional.empty();
            }
        }
        if (high - low > rules.maxSlope()) return Optional.empty();
        int minY = Math.max(rules.minY(), context.heightAccessor().getMinBuildHeight());
        int maxY = Math.min(rules.maxY(), context.heightAccessor().getMaxBuildHeight() - size.getY());
        int y = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        if (rules.placement() == SettlementGenerationRules.Placement.UNDERGROUND) {
            maxY = Math.min(maxY, low - size.getY() - 3);
            if (minY > maxY) return Optional.empty();
            y = minY + context.random().nextInt(maxY - minY + 1);
        }
        if (y < minY || y > maxY) return Optional.empty();
        Holder<Biome> biome = context.biomeSource().getNoiseBiome(
                x >> 2, y >> 2, z >> 2, context.randomState().sampler());
        if (!acceptsBiome(biome)) return Optional.empty();
        var anchor = new BlockPos(x, y, z);
        var origin = new BlockPos(minX - zero.minX(), y, minZ - zero.minZ());
        return Optional.of(new GenerationStub(anchor, builder ->
                builder.addPiece(new Piece(context.structureTemplateManager(), template, rotation, origin))));
    }

    public boolean acceptsBiome(Holder<Biome> biome) {
        return (rules.allowedBiomes().isEmpty() || matches(rules.allowedBiomes(), biome))
                && !matches(rules.excludedBiomes(), biome);
    }

    private static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
        return context.chunkGenerator().getBaseHeight(x, z, type, context.heightAccessor(), context.randomState());
    }

    private static boolean matches(List<String> selectors, Holder<Biome> biome) {
        for (String selector : selectors) {
            if (selector.startsWith("#")) {
                if (biome.is(TagKey.create(Registries.BIOME, new ResourceLocation(selector.substring(1))))) return true;
            } else if (biome.is(ResourceKey.create(Registries.BIOME, new ResourceLocation(selector)))) return true;
        }
        return false;
    }

    private static final class Piece extends TemplateStructurePiece {
        Piece(StructureTemplateManager manager, ResourceLocation template, Rotation rotation, BlockPos position) {
            super(PIECE.get(), 0, manager, template, template.toString(), settings(rotation), position);
        }

        Piece(StructureTemplateManager manager, CompoundTag tag) {
            super(PIECE.get(), tag, manager, ignored -> settings(Rotation.valueOf(tag.getString("Rotation"))));
        }

        static StructurePlaceSettings settings(Rotation rotation) {
            return new StructurePlaceSettings().setRotation(rotation).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rotation", getRotation().name());
        }

        @Override protected void handleDataMarker(String marker, BlockPos position,
                ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {}
    }
}
