package dev.felix.hostilehumans.monitor.client;

import dev.felix.hostilehumans.observer.client.NavigationLegendScreen;
import dev.felix.hostilehumans.entity.Human;

import dev.felix.hostilehumans.bootstrap.HumanRuntime;
import dev.felix.hostilehumans.monitor.HumanMonitorNetwork;
import dev.felix.hostilehumans.observer.NavigationDebugFrame;
import dev.felix.hostilehumans.observer.client.NavigationDebugClient;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owns the local viewing lifecycle, including nested UI and exact restoration of the previous camera. */
@Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT)
public final class HumanMonitorClient {
    private static HumanMonitorNetwork.State state;
    private static dev.felix.hostilehumans.monitor.MonitorLoadoutSnapshot loadout;
    private static java.util.UUID pendingSelection;
    static dev.felix.hostilehumans.monitor.MonitorLoadoutSnapshot loadout() { return loadout; }
    public static void acceptLoadout(dev.felix.hostilehumans.monitor.MonitorLoadoutSnapshot view) {
        if (state != null && state.takeover() && state.token().equals(view.token()) && state.generation() == view.generation()) {
            loadout = view; pendingSelection = null;
        }
    }
    static int menuPage() {
        if (loadout == null) return 0;
        var selected = pendingSelection == null ? loadout.selected() : pendingSelection;
        for (int i = 0; i < loadout.options().size(); i++) if (loadout.options().get(i).id().equals(selected)) return i / 9 * 9;
        return 0;
    }
    private static void selectOption(int index) {
        if (state == null || !state.takeover() || loadout == null || index < 0 || index >= loadout.options().size()) return;
        pendingSelection = loadout.options().get(index).id();
        HumanMonitorNetwork.select(state.token(), state.generation(), loadout.revision(), pendingSelection);
    }
    private static Entity previousCamera;
    private static CameraType previousType;
    private static net.minecraft.client.multiplayer.ClientLevel initialLevel;
    private static long receivedAt;
    private static int unresolvedTicks, heartbeat;
    private static float controlYaw, controlPitch;
    private static long nextInputSequence;
    private static boolean queuedAttack, queuedUse, queuedReload;
    private static boolean pauseInputSent;
    private static final MonitorMovementPrediction PREDICTION = new MonitorMovementPrediction();
    public static String predictionStatus() { return PREDICTION.reason(); }
    public static net.minecraft.world.phys.Vec3 predictedEye(Human human, float partialTick) {
        return takingOver(human) ? PREDICTION.eyePosition(human, partialTick) : null;
    }
    public static Boolean predictedOnGround(Human human, float partialTick) {
        return takingOver(human) ? PREDICTION.onGround() : null;
    }
    public static void acceptMotion(dev.felix.hostilehumans.monitor.MonitorMotionSnapshot motion) {
        var client = Minecraft.getInstance();
        if (state == null || !state.takeover() || !state.token().equals(motion.token())
                || state.generation() != motion.generation() || !state.target().equals(motion.target())
                || state.entityId() != motion.entityId() || client.level != initialLevel || client.level == null) return;
        if (client.level.getEntity(motion.entityId()) instanceof Human human && takingOver(human))
            PREDICTION.accept(human, motion);
    }
    private static final java.util.LinkedHashSet<java.util.UUID> CLOSED = new java.util.LinkedHashSet<>();
    private HumanMonitorClient() {}
    static HumanMonitorNetwork.State state() { return state; }
    public static boolean active() { return state != null; }
    public static boolean viewing(Human human) {
        return state != null && human.getId() == state.entityId() && human.getUUID().equals(state.target())
                && human.level() == initialLevel && initialLevel == Minecraft.getInstance().level;
    }
    public static boolean takingOver(Human human) { return state != null && state.takeover() && viewing(human); }
    public static float viewYaw() { return controlYaw; }
    public static float viewPitch() { return controlPitch; }
    public static boolean debugging() { return state != null && state.debug(); }
    public static void accept(HumanMonitorNetwork.State snapshot) {
        if (!snapshot.active()) {
            if (state != null && state.token().equals(snapshot.token())) close(false);
            return;
        }
        if (CLOSED.contains(snapshot.token())) return; // Late snapshots cannot reopen a locally closed view.
        var client = Minecraft.getInstance();
        if (state == null || !state.token().equals(snapshot.token())) {
            close(false);
            previousCamera = client.getCameraEntity(); previousType = client.options.getCameraType(); initialLevel = client.level;
            heartbeat = 0; unresolvedTicks = 0;
            if (client.screen instanceof dev.felix.hostilehumans.client.ui.SoldierRosterScreen) client.setScreen(null);
        }
        if (snapshot.takeover() && (state == null || !state.takeover())
                && client.level != null && client.level.getEntity(snapshot.entityId()) instanceof Human human) {
            controlYaw = net.minecraft.util.Mth.wrapDegrees(human.getYHeadRot());
            controlPitch = net.minecraft.util.Mth.clamp(human.getXRot(), -90, 90);
        }
        if (state == null || !state.token().equals(snapshot.token()) || state.generation() != snapshot.generation()
                || state.takeover() != snapshot.takeover()) {
            resetControlState();
        }
        state = snapshot; receivedAt = System.nanoTime(); MonitorGunHud.activate();
        dev.felix.hostilehumans.client.render.tacz.TaczSpectatorFirstPerson.activate();
        Keys.migrateLegacyTakeover();
    }
    private static void resetControlState() {
        PREDICTION.clear();
        nextInputSequence = 0;
        pauseInputSent = false;
        queuedAttack = queuedUse = queuedReload = false;
        MonitorGunHud.clear();
        dev.felix.hostilehumans.client.render.tacz.TaczSpectatorFirstPerson.clear();
        loadout = null;
        pendingSelection = null;
    }

    public static void request(int action, boolean enabled, int mode) {
        if (state != null) HumanMonitorNetwork.request(state.token(), action, enabled, mode);
    }
    static void close(boolean notifyServer) {
        if (state == null) return;
        var client = Minecraft.getInstance();
        if (notifyServer && client.getConnection() != null) request(1, false, 0);
        CLOSED.add(state.token());
        while (CLOSED.size() > 16) CLOSED.remove(CLOSED.iterator().next());
        state = null;
        resetControlState();
        NavigationDebugClient.accept(NavigationDebugFrame.EMPTY);
        if (client.player != null) {
            if (client.player.containerMenu != client.player.inventoryMenu) client.player.closeContainer();
            client.setCameraEntity(previousCamera != null && previousCamera.level() == client.level
                    && !previousCamera.isRemoved() ? previousCamera : client.player);
        }
        if (previousType != null) client.options.setCameraType(previousType);
        previousCamera = null; previousType = null; initialLevel = null;
        if (client.screen instanceof NavigationLegendScreen)
            client.setScreen(null);
    }
    /** The native hand pass always uses the real local player's inventory, even with a Human camera.
     * NPC arms/items are rendered separately by HumanSpectatorView at AFTER_ENTITIES. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hidePlayerHands(RenderHandEvent event) {
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera instanceof Human human && viewing(human)) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || state == null) return;
        var client = Minecraft.getInstance();
        boolean paused = client.isPaused();
        if (paused) receivedAt = System.nanoTime();
        else pauseInputSent = false;
        if (client.player == null || client.level == null || client.level != initialLevel || client.getConnection() == null
                || client.player.isSpectator() || System.nanoTime() - receivedAt > 5_000_000_000L) { close(true); return; }
        if (!paused && ++heartbeat >= 20) { heartbeat = 0; request(0, false, 0); }
        var entity = client.level.getEntity(state.entityId());
        if (!(entity instanceof Human human) || !viewing(human)) {
            if (++unresolvedTicks > 200) close(true);
            return;
        }
        if (!human.isAlive() || human.isRemoved()) { close(true); return; }
        unresolvedTicks = 0;
        client.setCameraEntity(human);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        // No persistent Screen: native chat, commands, pause and nested menus remain usable.
        if (state.takeover()) {
            if (paused && pauseInputSent) return;
            boolean input = !paused && client.screen == null && client.isWindowActive();
            float forward = input ? axis(client.options.keyUp.isDown(), client.options.keyDown.isDown()) : 0;
            float sideways = input ? axis(client.options.keyLeft.isDown(), client.options.keyRight.isDown()) : 0;
            var weapon = human.getMainHandItem();
            boolean attack = input && (queuedAttack || MonitorGunInput.attack(weapon));
            boolean use = input && (queuedUse || MonitorGunInput.aim(weapon));
            boolean reload = input && MonitorGunInput.nativeBindings(weapon) && (queuedReload || MonitorGunInput.reload(weapon));
            boolean ranged = dev.felix.hostilehumans.behavior.combat.gun.GunSupport.isGunItem(human.getMainHandItem())
                    || human.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem
                    || human.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem;
            boolean offhandConsumable = human.getOffhandItem().isEdible() || human.getOffhandItem().is(net.minecraft.world.item.Items.POTION);
            boolean supply = input && (Keys.SUPPLY.isDown() || !ranged && offhandConsumable && use);
            if (!paused) MonitorGunRecoil.update(human,state.token(),state.generation());
            var command = new HumanMonitorNetwork.Control(state.token(), forward, sideways,
                    controlYaw, controlPitch, input && client.options.keyJump.isDown(),
                    input && client.options.keySprint.isDown(), input && client.options.keyShift.isDown(),
                    state.generation(), ++nextInputSequence, input, attack, use, reload, supply);
            queuedAttack = queuedUse = queuedReload = false;
            HumanMonitorNetwork.control(command);
            if (paused) {
                pauseInputSent = true;
                PREDICTION.suspend();
            } else {
                PREDICTION.input(human, command, input);
            }
        }
    }
    private static float axis(boolean positive, boolean negative) { return (positive ? 1 : 0) - (negative ? 1 : 0); }
    public static boolean turn(double dx, double dy) {
        if (!active()) return false;
        if (state.takeover() && Minecraft.getInstance().screen == null) {
            var camera = Minecraft.getInstance().getCameraEntity();
            double scale = camera instanceof Human human
                    ? dev.felix.hostilehumans.client.render.tacz.TaczSpectatorFirstPerson.lookScale(human) : 1;
            controlYaw = net.minecraft.util.Mth.wrapDegrees(controlYaw + (float) (dx * scale) * .15F);
            controlPitch = net.minecraft.util.Mth.clamp(controlPitch + (float) (dy * scale) * .15F, -90, 90);
        }
        return true;
    }
    /** Native shot recoil changes both camera intent and subsequent server aim, without turning the real player. */
    public static void recoil(Human human,float yaw,float pitch) {
        if(!takingOver(human))return;
        controlYaw=net.minecraft.util.Mth.wrapDegrees(controlYaw+yaw);
        controlPitch=net.minecraft.util.Mth.clamp(controlPitch+pitch,-90,90);
    }
    /** The local view follows raw mouse intent and native recoil, never delayed entity rotation. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void cameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (event.getCamera().getEntity() instanceof Human human && takingOver(human)) {
            if (!paused) MonitorGunRecoil.update(human,state.token(),state.generation());
            event.setYaw(controlYaw); event.setPitch(controlPitch);
        }
    }
    @SubscribeEvent public static void movement(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        if (!active() || event.getEntity() != Minecraft.getInstance().player) return;
        var input = event.getInput();
        input.forwardImpulse = input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
        event.getEntity().setSprinting(false);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        if (active()) {
            if (state.takeover() && Minecraft.getInstance().screen == null
                    && !(Minecraft.getInstance().getCameraEntity() instanceof Human human
                        && MonitorGunInput.nativeBindings(human.getMainHandItem()))) {
                queuedAttack |= event.isAttack(); queuedUse |= event.isUseItem();
            }
            event.setCanceled(true); event.setSwingHand(false);
        }
    }
    /** Preserve short native/custom key taps until the next bounded control tick. */
    public static void queueWeaponInput(boolean attack, boolean use, boolean reload) {
        var client = Minecraft.getInstance();
        if (state != null && state.takeover() && client.screen == null && client.isWindowActive() && !client.isPaused()) {
            queuedAttack |= attack; queuedUse |= use; queuedReload |= reload;
        }
    }
    /** Runs before vanilla consumes inventory/hotbar keys. Does not swallow chat, slash or escape. */
    public static void handleKeys() {
        var client = Minecraft.getInstance();
        if (!active() || client.screen != null) return;
        while (Keys.EXIT.consumeClick()) { close(true); return; }
        while (Keys.TAKEOVER.consumeClick()) request(5, !state.takeover(), 0);
        while (Keys.DEBUG.consumeClick()) request(3, !state.debug(), 0);
        while (client.options.keyInventory.consumeClick()) request(2, false, 0);
        for (int i = 0; i < client.options.keyHotbarSlots.length; i++) {
            while (client.options.keyHotbarSlots[i].consumeClick()) {
                if (state.takeover()) selectOption(menuPage() + i);
                else if (i < 4) request(4, false, i);
            }
        }
        while (client.options.keyDrop.consumeClick()) { }
        while (client.options.keySwapOffhand.consumeClick()) if (state.takeover()) request(6, false, 0);
    }
    @SubscribeEvent public static void scroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        var client = Minecraft.getInstance();
        if (!active() || client.screen != null) return;
        event.setCanceled(true);
        if (!state.takeover() || loadout == null || loadout.options().isEmpty() || event.getScrollDelta() == 0) return;
        java.util.UUID selected = pendingSelection == null ? loadout.selected() : pendingSelection;
        int index = 0;
        for (int i = 0; i < loadout.options().size(); i++) if (loadout.options().get(i).id().equals(selected)) { index = i; break; }
        selectOption(Math.floorMod(index + (event.getScrollDelta() > 0 ? -1 : 1), loadout.options().size()));
    }
    @Mod.EventBusSubscriber(modid = HumanRuntime.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        private static final net.minecraftforge.client.settings.IKeyConflictContext CONTEXT = new net.minecraftforge.client.settings.IKeyConflictContext() {
            public boolean isActive() { return active() && Minecraft.getInstance().screen == null; }
            public boolean conflicts(net.minecraftforge.client.settings.IKeyConflictContext other) {
                return other == this || other == net.minecraftforge.client.settings.KeyConflictContext.IN_GAME;
            }
        };
        private static net.minecraft.client.KeyMapping key(String name, int code) {
            return new net.minecraft.client.KeyMapping("key.hostile_humans_unified.monitor_" + name, CONTEXT,
                    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, code, "key.categories.hostile_humans_unified");
        }
        static final net.minecraft.client.KeyMapping EXIT = key("exit", org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE);
        static final net.minecraft.client.KeyMapping TAKEOVER = key("takeover", org.lwjgl.glfw.GLFW.GLFW_KEY_F6);
        private static boolean migrated;
        private static void migrateLegacyTakeover() {
            if (migrated) return;
            migrated = true;
            if (TAKEOVER.getKey().equals(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_R))) {
                TAKEOVER.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F6));
                net.minecraft.client.KeyMapping.resetMapping();
                Minecraft.getInstance().options.save();
            }
        }
        static final net.minecraft.client.KeyMapping DEBUG = key("debug", org.lwjgl.glfw.GLFW.GLFW_KEY_V);
        static final net.minecraft.client.KeyMapping SUPPLY = key("supply", org.lwjgl.glfw.GLFW.GLFW_KEY_H);
        @SubscribeEvent public static void register(net.minecraftforge.client.event.RegisterKeyMappingsEvent event) {
            event.register(EXIT); event.register(TAKEOVER); event.register(DEBUG); event.register(SUPPLY);
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { close(false); }
}
