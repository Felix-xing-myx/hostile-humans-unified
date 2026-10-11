package club.someoneice.humangunner.compat;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.IClassBytecodeProvider;
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
    public static final String SCGUNS_NPC_FLASH =
            "onRenderLivingPost(Lnet/minecraftforge/client/event/RenderLivingEvent$Post;)V";

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String contract;
        if (mixinClassName.endsWith(".TouhouMaidGunHurtMixin")) contract = MAID_EXPLOSION;
        else if (mixinClassName.endsWith(".SpartanWeaponryItemRandomizerMixin")) contract = SPARTAN_RANDOMIZER;
        else if (mixinClassName.endsWith(".ScgunsNpcMuzzleFlashMixin")) contract = SCGUNS_NPC_FLASH;
        else return true;
        try {
            ClassNode target = readTarget(MixinService.getService().getBytecodeProvider(), targetClassName);
            if (hasMethod(target, contract, contract.equals(SPARTAN_RANDOMIZER))) return true;
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

    /** The one-argument service API selects the loader-supported transformation
     * mode. Forge's ModLauncher explicitly rejects getClassNode(name, false).
     * Reading a ClassNode does not define or initialize the target Java class.
     */
    public static ClassNode readTarget(IClassBytecodeProvider provider, String targetClassName)
            throws ClassNotFoundException, IOException {
        return provider.getClassNode(targetClassName);
    }

    public static boolean hasMethod(ClassNode target, String contract, boolean isStatic) {
        return target.methods.stream().anyMatch(method -> (method.name + method.desc).equals(contract)
                && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic
                && (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0);
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
