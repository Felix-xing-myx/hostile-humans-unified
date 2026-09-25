package club.someoneice.humangunner.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Backports Polymorph's later thread-safe registry storage to its 1.20.1 build.
 *
 * <p>VS Addition constructs block entities while bulk-scanning block states at
 * server start. Polymorph 0.49.x can concurrently append to and iterate these
 * LinkedLists, corrupting a ListItr so that {@code next()} dereferences a null
 * node. Copy-on-write is appropriate here: registrations are rare and reads
 * occur for every capability attachment.</p>
 */
@Pseudo
@Mixin(
        targets = "com.illusivesoulworks.polymorph.common.impl.PolymorphCommon",
        remap = false
)
public abstract class PolymorphConcurrencyMixin {
    @Shadow
    @Final
    @Mutable
    private List<Object> blockEntity2RecipeData;

    @Shadow
    @Final
    @Mutable
    private List<Object> container2BlockEntities;

    @Shadow
    @Final
    @Mutable
    private List<Object> container2ItemStacks;

    @Shadow
    @Final
    @Mutable
    private List<Object> itemStack2RecipeData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void humanGunner$useThreadSafeRecipeRegistries(CallbackInfo ci) {
        blockEntity2RecipeData = copyOnWrite(blockEntity2RecipeData);
        container2BlockEntities = copyOnWrite(container2BlockEntities);
        container2ItemStacks = copyOnWrite(container2ItemStacks);
        itemStack2RecipeData = copyOnWrite(itemStack2RecipeData);
    }

    private static <T> CopyOnWriteArrayList<T> copyOnWrite(List<T> source) {
        return source == null
                ? new CopyOnWriteArrayList<>()
                : new CopyOnWriteArrayList<>(source);
    }
}
