package dev.felix.hostilehumans.client.render.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.client.animation.Animations;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.AnimationListener;
import com.tacz.guns.api.client.animation.ObjectAnimationChannel.ChannelType;
import com.tacz.guns.client.resource.InternalAssetLoader;
import com.tacz.guns.client.resource.pojo.display.gun.DefaultAnimationType;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.util.math.MathUtil;
import dev.felix.hostilehumans.client.render.ClientAnimationCompatibility;
import dev.felix.hostilehumans.entity.Human;
import dev.felix.hostilehumans.client.render.TaczNpcEffects;
import dev.felix.hostilehumans.client.render.SpectatorSkinTextures;
import dev.felix.hostilehumans.client.render.HumanSpectatorMotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/** Loaded only after the mod gate. Every mutable model/channel belongs to this one observation. */
final class TaczNativePresentation {
    private static Observation current;
    private static Object failedDisplay;
    private static Object failedHuman;
    private static Object failedLevel;
    private static String failedOptic;
    private static boolean listening;
    private TaczNativePresentation() {}

    static void clear() { current = null; failedDisplay = null; failedHuman = null; failedLevel = null; failedOptic = null; }

    static boolean render(Human human, float partialTick, PoseStack poses, MultiBufferSource buffers, int light) {
        ItemStack source = human.getMainHandItem();
        if (IGun.getIGunOrNull(source) == null) { current = null; return false; }
        boolean submitted = false;
        GunDisplay attemptedDisplay = null;
        try {
            if (!listening) {
                MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TaczNativePresentation::shot);
                listening = true;
            }
            GunDisplay display = attemptedDisplay = TaczPresentationResources.display(source);
            if (display == null) { current = null; return false; }
            if (failedDisplay == display && failedHuman == human && failedLevel == human.level()
                    && java.util.Objects.equals(failedOptic, TaczNativeOptics.failureKey(source))) return false;
            if (current == null || current.human != human || current.display != display
                    || !current.gunId.equals(IGun.getIGunOrNull(source).getGunId(source))) {
                current = null;
                current = new Observation(human, source, display);
            }
            // Equipment synchronization can replace ItemStack on every shot. Geometry is keyed by display,
            // while actions are keyed by the authoritative reload session, never by mutable ammunition NBT.
            current.source = source;
            current.submitted = false;
            return current.render(partialTick, poses, buffers, light);
        } catch (RuntimeException | LinkageError failure) {
            if (current != null) submitted = current.submitted;
            failedDisplay = attemptedDisplay;
            current = null;
            failedHuman = human; failedLevel = human.level();
            // Removing a broken optic or changing its selected view must permit a fresh presentation.
            try { failedOptic = TaczNativeOptics.failureKey(source); }
            catch (RuntimeException | LinkageError ignored) { failedOptic = null; }
            ClientAnimationCompatibility.warnOnce("TaCZ spectator native model", "Native presentation unavailable; using NPC arms", failure);
            // A driver/model error after vertex submission must not overlay the old arms in this frame.
            return submitted;
        }
    }

    private static void shot(GunFireEvent event) {
        if (event.getLogicalSide().isClient() && current != null && event.getShooter() == current.human) {
            IGun fired = IGun.getIGunOrNull(event.getGunItemStack());
            IGun held = IGun.getIGunOrNull(current.human.getMainHandItem());
            if (fired != null && held != null && fired.getGunId(event.getGunItemStack()).equals(held.getGunId(current.human.getMainHandItem())))
                current.shots.shot(current.clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused()));
        }
    }

    private record ArmFrame(HumanoidArm arm, Matrix4f pose, Matrix3f normal) {}
    private record ActionFrame(String animation, double seconds) {}

    private static final class Observation {
        final Human human;
        ItemStack source;
        private ItemStack copiedSource, renderedStack;
        private net.minecraft.nbt.CompoundTag copiedTag;
        private int copiedCount;
        final GunDisplay display;
        final net.minecraft.resources.ResourceLocation gunId;
        final BedrockGunModel model;
        final PlayerModel<Human> arms;
        final Map<String, ObjectAnimation> animations = new HashMap<>();
        final Map<String, Double> lengths = new HashMap<>();
        final List<ArmFrame> handFrames = new ArrayList<>();
        long reloadStart = Long.MIN_VALUE;
        long reloadSession = Long.MIN_VALUE;
        boolean submitted;
        final NativeFrameClock clock = new NativeFrameClock();
        final NativeShotTimeline shots = new NativeShotTimeline();
        NativeReloadPlan reloadPlan;
        NativeMovementPlan movement;
        NativeCrouchPlan crouch;
        NativeBoltPlan bolt;
        final NativeJumpSway jumpSway = new NativeJumpSway();
        final TaczNativeOptics optics = new TaczNativeOptics();
        float sampleWeight = 1;

        Observation(Human human, ItemStack source, GunDisplay display) {
            this.human = human; this.source = source; this.display = display;
            gunId = IGun.getIGunOrNull(source).getGunId(source);
            var assets = ClientAssetsManager.INSTANCE;
            var pojo = assets.getBedrockModelPOJO(display.getModelLocation());
            if (pojo == null) throw new IllegalArgumentException("Missing native gun model");
            BedrockVersion version;
            if (BedrockVersion.isNewVersion(pojo)) version = BedrockVersion.NEW;
            else if (BedrockVersion.isLegacyVersion(pojo)) version = BedrockVersion.LEGACY;
            else throw new IllegalArgumentException("Unsupported native model format");
            // Unknown registered Java model types can install arbitrary functional delegates. They are
            // deliberately outside this audited adapter; ordinary unfamiliar bones remain plain geometry.
            if (!"default".equals(display.getModelType())) throw new IllegalArgumentException("Unsupported custom native model type");
            model = new BedrockGunModel(pojo, version);
            if (model.getNode("lefthand_pos") == null || model.getNode("righthand_pos") == null)
                throw new IllegalArgumentException("Native model has no first-person hand anchors");
            model.setFunctionalRenderer("lefthand_pos", part -> (poses, consumer, context, light, overlay) -> capture(HumanoidArm.LEFT, poses));
            model.setFunctionalRenderer("righthand_pos", part -> (poses, consumer, context, light, overlay) -> capture(HumanoidArm.RIGHT, poses));
            // Replace the global-player flash delegate with the existing scoped NPC capture.
            model.setFunctionalRenderer("muzzle_flash", part -> (poses, consumer, context, light, overlay) -> TaczNpcEffects.renderMuzzle(poses,context));
            model.setRenderHand(true);
            arms = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            var file = assets.getBedrockAnimations(display.getAnimationLocation());
            if (file == null) throw new IllegalArgumentException("Native spectator sampling requires Bedrock animation resources");
            for (ObjectAnimation animation : Animations.createAnimationFromBedrock(file)) {
                double authored = file.getAnimations().get(animation.name).getAnimationLength();
                register(animation, authored);
            }
            // Match native resource precedence: gun-specific > explicit default > rifle/pistol prototype.
            // Prototypes are copied before binding, so another player/model is never modified.
            var defaults = display.getDefaultAnimation() == null ? null : assets.getBedrockAnimations(display.getDefaultAnimation());
            if (defaults != null) {
                for (ObjectAnimation animation : Animations.createAnimationFromBedrock(defaults))
                    register(animation, defaults.getAnimations().get(animation.name).getAnimationLength());
            } else {
                var prototypes = display.getDefaultAnimationType() == DefaultAnimationType.PISTOL
                        ? InternalAssetLoader.getDefaultPistolAnimations() : InternalAssetLoader.getDefaultRifleAnimations();
                if (prototypes != null) for (ObjectAnimation prototype : prototypes)
                    if (!animations.containsKey(prototype.name)) register(new ObjectAnimation(prototype), 0);
            }
            if (!animations.containsKey("static_idle")) throw new IllegalArgumentException("Missing native static_idle");
            movement = new NativeMovementPlan(lengths);
            crouch = new NativeCrouchPlan(lengths);
            // The default and manual-action Lua state machines select this canonical main-track clip.
            // Arbitrary script-specific clip names are not guessed from a gun name or Bolt enum.
            bolt = new NativeBoltPlan(lengths.getOrDefault("bolt", 0D));
        }

        private void register(ObjectAnimation animation, double authored) {
            if (animations.containsKey(animation.name)) return;
            animation.applyAnimationListeners((name, type) -> {
                AnimationListener delegate = model.supplyListeners(name, type);
                if (delegate == null) return null;
                return new AnimationListener() {
                    private final float[] weighted = new float[3];
                    public ChannelType getType() { return delegate.getType(); }
                    public float[] initialValue() { return delegate.initialValue(); }
                    public void update(float[] value, boolean additive) {
                        if (!additive || sampleWeight >= 1) { delegate.update(value, additive); return; }
                        NativeAnimationWeight.apply(value, type, sampleWeight, weighted);
                        delegate.update(weighted, true);
                    }
                };
            });
            animations.put(animation.name, animation);
            lengths.put(animation.name, authored > 0 ? authored : (double) animation.getMaxEndTimeS());
        }

        private void capture(HumanoidArm arm, PoseStack poses) {
            if (!model.getRenderHand()) return;
            poses.pushPose();
            try {
                poses.mulPose(Axis.ZP.rotationDegrees(180));
                handFrames.add(new ArmFrame(arm, new Matrix4f(poses.last().pose()), new Matrix3f(poses.last().normal())));
            } finally { poses.popPose(); }
        }

        boolean render(float partialTick, PoseStack input, MultiBufferSource buffers, int light) {
            double renderSeconds=clock.sample(System.nanoTime(),Minecraft.getInstance().isPaused());
            var operator = IGunOperator.fromLivingEntity(human);
            optics.update(source);
            boolean reloading = operator.getSynReloadState().getStateType().isReloading();
            boolean bolting = operator.getSynIsBolting();
            // Preserve the existing foreground pose for actions not owned by this native action sampler.
            if(!reloading&&(operator.getSynDrawCoolDown()>0||operator.getSynMeleeCoolDown()>0)) {
                crouch.clear();
                bolt.clear();
                return false;
            }
            if (reloading) crouch.clear();
            var boltAction = bolt.sample(((double) human.level().getGameTime() + partialTick) / 20D, bolting, reloading);
            if (bolting && !bolt.supported()) { crouch.clear(); return false; }
            var timeline = human.getTaczReloadVisual();
            ActionFrame action = null;
            if (reloading) {
                IGun held = IGun.getIGunOrNull(source);
                if (timeline.isEmpty() || timeline.getBoolean("Complete") || !timeline.contains("WorkRate") || !timeline.contains("Empty")
                        || !held.getGunId(source).toString().equals(timeline.getString("Gun"))) return false;
                if (reloadPlan == null || reloadStart != timeline.getLong("Start") || reloadSession != timeline.getLong("Session")) {
                    reloadStart = timeline.getLong("Start");
                    reloadSession = timeline.getLong("Session");
                    reloadPlan = TaczPresentationResources.plan(lengths, timeline);
                }
                if (!reloadPlan.supported()) return false;
                double position = TaczPresentationResources.presentationTime(reloadPlan, human, partialTick);
                if (!Double.isFinite(position)) return false;
                var reloadAction = reloadPlan.sample(position);
                action = new ActionFrame(reloadAction.animation(), reloadAction.seconds());
            } else {
                reloadPlan = null; reloadStart = Long.MIN_VALUE;
                if (boltAction != null) action = new ActionFrame("bolt", boltAction.seconds());
            }
            boolean foreground = reloading || bolting || boltAction != null;

            // Native item rendering is already player-centered. Do not reuse the world-oriented NPC frame.
            PoseStack poses = new PoseStack();
            // TaCZ cancels vanilla hand bob for players. Authored gun gait owns movement here too;
            // the world's separate camera bob remains active without adding a second gun oscillation.
            handFrames.clear();
            model.cleanAnimationTransform();
            try {
                sample("static_idle", 0, false);
                IGun gun = IGun.getIGunOrNull(source);
                String fireMode = gun.getFireMode(source).name().toLowerCase(java.util.Locale.ROOT);
                sample("static_" + fireMode, 0, false);
                if (!gun.hasBulletInBarrel(source) && gun.getCurrentAmmoCount(source) <= 0) sample("static_bolt_caught", 0, false);
                var motion = HumanSpectatorMotion.frame(human, partialTick);
                float aim = TaczNativeOptics.aim(human, partialTick).aim();
                double sinceShot = shots.elapsed(renderSeconds);
                if (action != null) sample(action.animation(), action.seconds(), false);
                double yaw = Math.toRadians(Minecraft.getInstance().gameRenderer.getMainCamera().getYRot());
                double forward = -Math.sin(yaw) * motion.dx() + Math.cos(yaw) * motion.dz();
                double side = Math.cos(yaw) * motion.dx() + Math.sin(yaw) * motion.dz();
                boolean grounded = HumanSpectatorMotion.onGround(human, partialTick);
                for (var entry : movement.sample(motion.seconds(), motion.distance(), motion.speed() > 0.01,
                        human.isSprinting(), grounded, aim, forward, side, foreground, sinceShot < 0.18).entrySet()) {
                    sampleWeight = entry.getValue().weight();
                    sample(entry.getKey(), entry.getValue().seconds(), true);
                }
                sampleWeight = 1;
                // Native shouldSlide is crouching && GunData.canSlide. Humans synchronize sneak intent
                // without the LocalPlayer pose updater, so accept that flag alongside the actual pose.
                boolean canSlide = TimelessAPI.getCommonGunIndex(gunId)
                        .map(index -> index.getGunData() != null && index.getGunData().canSlide()).orElse(false);
                for (var entry : crouch.sample(motion.seconds(), human.isCrouching() || human.isShiftKeyDown(),
                        canSlide, foreground || human.isSprinting(), aim).entrySet()) {
                    sampleWeight = entry.getValue().weight();
                    sample(entry.getKey(), entry.getValue().seconds(), true);
                }
                sampleWeight = 1;
                // Native gun kick is an independent blending track, even over the tail of a bolt clip.
                if (!reloading) {
                    String shoot = gun.getCurrentAmmoCount(source) <= 0 && !gun.hasBulletInBarrel(source) && animations.containsKey("shoot_last") ? "shoot_last" : "shoot";
                    var shot=shots.sample(renderSeconds,lengths.getOrDefault(shoot,0D));
                    if(shot!=null) {
                        sampleWeight=shot.weight();
                        sample(shoot,shot.seconds(),true);
                        sampleWeight=1;
                    }
                }
                double jumpOffset = jumpSway.sample(motion.seconds(), HumanSpectatorMotion.eyePosition(human, partialTick).y,
                        grounded, !human.isPassenger() && !human.isInWaterOrBubble() && !human.isFallFlying() && !human.onClimbable());
                var root = model.getRootNode();
                if (root != null) root.offsetY += (float) (jumpOffset * NativeMovementPlan.movementScale(aim));
                poses.translate(0, 1.5F, 0);
                poses.mulPose(Axis.ZP.rotationDegrees(180));
                Matrix4f idle = positioningInverse(model.getIdleSightPath());
                Matrix4f positioning = new Matrix4f(idle);
                var aimPath = optics.aimPath(model, source);
                if (aim > 0 && aimPath != null)
                    MathUtil.applyMatrixLerp(idle, positioningInverse(aimPath), positioning, aim);
                poses.translate(0, 1.5F, 0); poses.mulPoseMatrix(positioning); poses.translate(0, -1.5F, 0);
                FirstPersonRenderGunEvent.applyAnimationConstraintTransform(poses, model, aim);
                // TaCZ's low-level model handles attachments and its own gun buffer; no local-player renderer is entered.
                var type = TaczPresentationResources.transparent(display) ? RenderType.entityTranslucent(display.getModelTexture())
                        : RenderType.entityCutout(display.getModelTexture());
                var nativeBuffers = Minecraft.getInstance().renderBuffers().bufferSource();
                nativeBuffers.endBatch();
                submitted = true;
                try (var opticState = new TaczOpticGraphicsState(); var opticContext = optics.enter(aim)) {
                    try {
                        TaczNpcEffects.withPresentation(human,buffers,()->model.render(poses,renderStack(),
                                ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,type,light,OverlayTexture.NO_OVERLAY));
                    } finally {
                        // Drain native delegates before closing the optic context and restoring its stencil state.
                        nativeBuffers.endBatch();
                    }
                }
                for (ArmFrame frame : handFrames) drawArm(frame, buffers, light);
                return true;
            } finally { handFrames.clear(); model.cleanAnimationTransform(); }
        }

        /** TaCZ's public bone coordinates, isolated from the renderer's optional nested-library parent.
         * Inverse path order, positive XYZ rotations and the root's 1.5 offset match 1.1.8's positioning contract. */
        private static Matrix4f positioningInverse(List<BedrockPart> path) {
            Matrix4f inverse=new Matrix4f();
            if(path!=null)for(int i=path.size()-1;i>=0;i--) {
                BedrockPart part=path.get(i);
                inverse.rotate(Axis.XP.rotation(part.xRot)).rotate(Axis.YP.rotation(part.yRot)).rotate(Axis.ZP.rotation(part.zRot));
                inverse.translate(-part.x/16F,part.getParent()==null?1.5F-part.y/16F:-part.y/16F,-part.z/16F);
            }
            return inverse;
        }

        /** Keep rendering detached, but do not construct ItemStack/capabilities on every frame. */
        private ItemStack renderStack() {
            if(renderedStack==null||copiedSource!=source||!java.util.Objects.equals(copiedTag,source.getTag())||copiedCount!=source.getCount()) {
                renderedStack=source.copy();copiedSource=source;copiedCount=source.getCount();
                copiedTag=source.getTag()==null?null:source.getTag().copy();
            }
            return renderedStack;
        }

        private void sample(String name, double seconds, boolean blend) {
            ObjectAnimation animation = animations.get(name);
            if (animation != null) animation.update(blend, (float) (seconds * 1_000_000_000D));
        }

        private void drawArm(ArmFrame frame, MultiBufferSource buffers, int light) {
            PoseStack poses = new PoseStack();
            poses.last().pose().set(frame.pose()); poses.last().normal().set(frame.normal());
            ModelPart arm = frame.arm() == HumanoidArm.LEFT ? arms.leftArm : arms.rightArm;
            ModelPart sleeve = frame.arm() == HumanoidArm.LEFT ? arms.leftSleeve : arms.rightSleeve;
            arm.resetPose(); sleeve.resetPose();
            arm.xRot = arm.yRot = arm.zRot = 0;
            sleeve.copyFrom(arm);
            var consumer = buffers.getBuffer(RenderType.entityTranslucent(SpectatorSkinTextures.resolve(human.getResourceLocation())));
            arm.render(poses, consumer, light, OverlayTexture.NO_OVERLAY);
            sleeve.render(poses, consumer, light, OverlayTexture.NO_OVERLAY);
        }
    }
}
