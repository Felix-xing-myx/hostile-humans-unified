package dev.felix.hostilehumans.compat;

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
    public static final String TACZ_NPC_FLASH =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/item/ItemDisplayContext;II)V";
    public static final String TACZ_SOUND_PACKET =
            "<init>(ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;FFI)V";
    public static final String SCGUNS_SULFURHEAD_DAMAGE =
            "m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    public static final String SCGUNS_HUMAN_AIM =
            "getLeadingDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)Lnet/minecraft/world/phys/Vec3;";
    public static final String SCGUNS_HUMAN_SHIELD =
            "onHitEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Z)V";

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("ScgunsMonitorShotMixin") || mixinClassName.endsWith("JegMonitorShotMixin"))
            return monitorShotMixin(targetClassName, mixinClassName.endsWith("ScgunsMonitorShotMixin"));
        if (mixinClassName.contains(".TaczScope")) return opticMixin(targetClassName, mixinClassName);
        String contract;
        if (mixinClassName.endsWith(".TaczMonitorSoundBroadcastMixin")) contract = TACZ_SOUND_PACKET;
        else if (mixinClassName.endsWith(".TaczMonitorShotSoundMixin")) contract = "spectator-shot-playback";
        else if (mixinClassName.endsWith(".SpectatorGunSoundDistanceMixin")) contract = "spectator-gun-distance";
        else if (mixinClassName.endsWith(".SpectatorTaczAnimationSoundDistanceMixin")) contract = "spectator-animation-distance";
        else if (mixinClassName.endsWith(".TouhouMaidGunHurtMixin")) contract = MAID_EXPLOSION;
        else if (mixinClassName.endsWith(".SpartanWeaponryItemRandomizerMixin")) contract = SPARTAN_RANDOMIZER;
        else if (mixinClassName.endsWith(".ScgunsNpcMuzzleFlashMixin")) contract = SCGUNS_NPC_FLASH;
        else if (mixinClassName.endsWith(".TaczNpcMuzzleFlashMixin")) contract = TACZ_NPC_FLASH;
        else if (mixinClassName.endsWith(".ScgunsSulfurheadDamageMixin")) contract = SCGUNS_SULFURHEAD_DAMAGE;
        else if (mixinClassName.endsWith(".ScgunsHumanAimMixin")) contract = SCGUNS_HUMAN_AIM;
        else if (mixinClassName.endsWith(".ScgunsHumanShieldMixin")) contract = SCGUNS_HUMAN_SHIELD;
        else if (mixinClassName.endsWith(".JegNpcMuzzleFlashMixin")) contract =
                "renderWeapon(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)Z";
        else return true;
        if (contract.equals(SCGUNS_HUMAN_AIM)) NativeAimCompatibility.scgunsSharedPrediction(false);
        try {
            ClassNode target = readTarget(MixinService.getService().getBytecodeProvider(), targetClassName);
            if (contract.equals("spectator-shot-playback")) {
                boolean supported = SpectatorSoundContract.supportsShotPlayback(target);
                if (!supported) LOGGER.warn("Retaining native gun sound playback: controlled first-person sound seam changed in {}", targetClassName);
                return supported;
            }
            if (contract.startsWith("spectator-")) {
                boolean supported = SpectatorSoundContract.supports(target, contract.equals("spectator-animation-distance"));
                if (!supported) LOGGER.warn("Skipping spectator sound compatibility {}: upstream distance calculation changed; native sound retained", targetClassName);
                return supported;
            }
            if (contract.equals(SCGUNS_HUMAN_SHIELD) && !supportsScgunsShield(target)) {
                LOGGER.warn("Retaining native Scguns shield interception: its call site changed");
                return false;
            }
            if (contract.equals(SCGUNS_SULFURHEAD_DAMAGE)
                    && !"net/minecraft/world/entity/monster/Monster".equals(target.superName)) {
                LOGGER.warn("Skipping Sulfurhead damage compatibility: superclass changed to {}", target.superName);
                return false;
            }
            if (hasMethod(target, contract, contract.equals(SPARTAN_RANDOMIZER) || contract.equals(SCGUNS_HUMAN_AIM))) {
                if (contract.equals(SCGUNS_HUMAN_AIM)) NativeAimCompatibility.scgunsSharedPrediction(true);
                return true;
            }
            LOGGER.warn("Skipping optional upstream fix {}: {} no longer exposes {}. "
                    + "The upstream behavior is retained; this fix needs a compatibility review.",
                    mixinClassName, targetClassName, contract);
            return false;
        } catch (ClassNotFoundException absentMod) {
            return false;
        } catch (IOException error) {
            return skipUnreadableTarget(targetClassName, error);
        }
    }
    private static boolean monitorShotMixin(String targetClassName, boolean scguns) {
        boolean supported = false;
        try {
            supported = supportsMonitorShot(readTarget(MixinService.getService().getBytecodeProvider(), targetClassName), scguns);
            if (!supported) LOGGER.warn("Disabling optional manual gun fire: native NPC seam changed in {}", targetClassName);
            return supported;
        } catch (ClassNotFoundException absent) { return false; }
        catch (IOException error) { return skipUnreadableTarget(targetClassName, error); }
        finally {
            if (scguns) NativeMonitorGunCompatibility.scguns(supported);
            else NativeMonitorGunCompatibility.jeg(supported);
        }
    }

    public static boolean supportsMonitorShot(ClassNode target, boolean scguns) {
        String gun = scguns ? "top/ribs/scguns/common/Gun" : "ttv/migami/jeg/common/Gun";
        String descriptor = "(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;L"
                + gun + ";F" + (scguns ? "" : "Z") + ")V";
        var attack = target.methods.stream().filter(m -> (m.name + m.desc).equals("performGunAttack" + descriptor)
                && (m.access & Opcodes.ACC_STATIC) != 0).findFirst().orElse(null);
        if (attack == null || nativeCalls(attack, "m_21023_", "(Lnet/minecraft/world/effect/MobEffect;)Z") != 1) return false;
        if (!scguns) return true;
        var direction = target.methods.stream().filter(m -> (m.name + m.desc).equals("getDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Ltop/ribs/scguns/item/GunItem;Ltop/ribs/scguns/common/Gun;F)Lnet/minecraft/world/phys/Vec3;")
                && (m.access & Opcodes.ACC_STATIC) != 0).findFirst().orElse(null);
        return direction != null && nativeCalls(direction, "m_20184_", "()Lnet/minecraft/world/phys/Vec3;") == 1
                && hasMethod(target, SCGUNS_HUMAN_AIM, true);
    }

    private static long nativeCalls(org.objectweb.asm.tree.MethodNode method, String name, String descriptor) {
        return java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false)
                .filter(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("net/minecraft/world/entity/LivingEntity")
                        && call.name.equals(name) && call.desc.equals(descriptor)).count();
    }

    private static boolean opticMixin(String targetClassName, String mixin) {
        try {
            ClassNode target = readTarget(MixinService.getService().getBytecodeProvider(), targetClassName);
            boolean supported;
            if (mixin.endsWith("TaczScopeAttachmentIndexMixin"))
                supported = hasMethod(target, "getAttachmentModel()Lcom/tacz/guns/client/model/BedrockAttachmentModel;", false);
            else if (mixin.endsWith("TaczScopeRenderCompatibilityMixin")) supported = hasMethod(target, "shouldAccelerate()Z", true);
            else if (mixin.endsWith("TaczScopeCameraMixin")) supported = hasMethod(target, "applyScopeMagnification(Lnet/minecraftforge/client/event/ViewportEvent$ComputeFov;)V", true)
                    && hasMethod(target, "applyGunModelFovModifying(Lnet/minecraftforge/client/event/ViewportEvent$ComputeFov;)V", true);
            else supported = target.methods.stream().filter(m -> m.name.equals("renderOcularAndDivision"))
                    .flatMap(m -> java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false))
                    .filter(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("com/tacz/guns/api/client/gameplay/IClientPlayerGunOperator")
                            && call.name.equals("getClientAimingProgress") && call.desc.equals("(F)F")).count() == 1;
            if (!supported) LOGGER.warn("Skipping optional NPC optic bridge {}: upstream seam changed", mixin);
            return supported;
        } catch (ClassNotFoundException absent) { return false; }
        catch (IOException error) { return skipUnreadableTarget(targetClassName, error); }
    }

    private static boolean skipUnreadableTarget(String targetClassName, IOException error) {
        LOGGER.warn("Skipping optional compatibility for {}: target bytecode could not be inspected", targetClassName, error);
        return false;
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
    private static boolean supportsScgunsShield(ClassNode target) {
        return target.methods.stream()
                .filter(method -> (method.name + method.desc).equals(SCGUNS_HUMAN_SHIELD))
                .flatMap(method -> java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false))
                .filter(instruction -> instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals("top/ribs/scguns/entity/projectile/ProjectileEntity$ProjectileHelper")
                        && call.name.equals("handleShieldHit")
                        && call.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;F)Z"))
                .count() == 1;
    }
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
