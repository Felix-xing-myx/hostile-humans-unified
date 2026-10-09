package club.someoneice.humangunner.compat;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Capability checks for nonessential upstream implementation fixes only.
 * Reads bytecode without initializing optional mods during early Mixin setup.
 * Ammo accounting and projectile protection must never be silently skipped here.
 */
public final class OptionalMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("HostileHumansCompatibility");
    public static final String MAID_EXPLOSION =
            "onExplosionDetonateEvent(Lnet/minecraftforge/event/level/ExplosionEvent$Detonate;)V";
    public static final String SPARTAN_RANDOMIZER =
            "generate(Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/item/ItemStack;";

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String contract;
        if (mixinClassName.endsWith(".TouhouMaidGunHurtMixin")) contract = MAID_EXPLOSION;
        else if (mixinClassName.endsWith(".SpartanWeaponryItemRandomizerMixin")) contract = SPARTAN_RANDOMIZER;
        else return true;
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName, false);
            if (hasMethod(target, contract)) return true;
            LOGGER.warn("Skipping optional upstream fix {}: {} no longer exposes {}. "
                    + "The upstream behavior is retained; this fix needs a compatibility review.",
                    mixinClassName, targetClassName, contract);
            return false;
        } catch (ClassNotFoundException absentMod) {
            return false;
        } catch (IOException error) {
            // Do not conceal unreadable bytecode as an absent/compatible dependency.
            throw new IllegalStateException("Cannot inspect optional compatibility target " + targetClassName, error);
        }
    }

    public static boolean hasMethod(ClassNode target, String contract) {
        return target.methods.stream().anyMatch(method -> (method.name + method.desc).equals(contract));
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
