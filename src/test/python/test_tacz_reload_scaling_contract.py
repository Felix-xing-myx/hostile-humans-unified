"""Server timing and optional client playback boundaries; does not launch Minecraft."""
from pathlib import Path
import os
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
CORE = ROOT / "src/main/java/club/someoneice/humangunner"
CLIENT = ROOT / "src/main/java/com/craftix/hostile_humans/client/renderer"


class TaczReloadScalingContract(unittest.TestCase):
    def test_server_deadline_is_phase_aware_and_throttled(self):
        source = (CORE / "TaczReloadVisualTimeline.java").read_text(encoding="utf-8")
        self.assertIn("human.level().isClientSide", source)
        self.assertIn('finishing ? phaseEnd : Math.max(estimatedEnd, phaseEnd + tag.getInt("Tail"))', source)
        self.assertIn('boolean firstFeed = !finishing && !tag.getBoolean("SeenFeed")', source)
        self.assertIn("Math.abs(end - previousEnd) > 2", source)
        self.assertNotIn("com.tacz", source)
        self.assertNotIn("setCurrentAmmoCount", source)

    def test_timeline_is_bound_to_actual_reload_lifecycle(self):
        source = (CORE / "TaczHumanReloadTransactions.java").read_text(encoding="utf-8")
        self.assertIn("TaczReloadVisualTimeline.begin(human, gun.getGunId(weapon).toString(), visualTicks, visualTail, visualFeedTicks)", source)
        self.assertIn("state.getCountDown(), state.getStateType().isReloadFinishing()", source)
        self.assertIn("VISUAL_OWNERS.remove(data)", source)
        self.assertIn("new java.lang.ref.WeakReference<>(human)", source)
        self.assertIn("clearVisual(data);", source)

    def test_only_reload_action_layer_has_speed_modifier(self):
        source = (CLIENT / "TaczNpcAnimator.java").read_text(encoding="utf-8")
        self.assertIn("timing.addModifier.invoke(state.oneShotLayer, state.reloadSpeed)", source)
        self.assertNotIn("timing.addModifier.invoke(state.lowerLayer", source)
        self.assertIn("timing.playerAtTick.newInstance(animation, seek)", source)
        self.assertIn("TaczReloadPlayback.speed(stop, current, snapshot.getLong(\"End\") - now)", source)
        self.assertIn("state.reloadWaitingForTiming", source)
        self.assertIn('state.missingOneShotAnimation = "reload_upper"', source)
        self.assertIn("human.tickCount < state.reloadFadeUntil", source)

    def test_animation_apis_are_public_and_optional(self):
        source = (CLIENT / "ClientAnimationCompatibility.java").read_text(encoding="utf-8")
        block = source.split("resolveTaczReload()", 1)[1].split("static PlayerAnimatorModelBridge", 1)[0]
        self.assertIn('getMethod("addModifierLast", modifier)', block)
        self.assertIn('getField("speed")', block)
        self.assertNotIn("setAccessible", block)
        self.assertNotIn("getDeclared", block)
        jar = os.environ.get("PLAYER_ANIMATOR_API_JAR")
        if jar:
            with zipfile.ZipFile(jar) as z:
                speed = z.read("dev/kosmx/playerAnim/api/layered/modifier/SpeedModifier.class")
                self.assertIn(b"speed", speed)
                player = z.read("dev/kosmx/playerAnim/api/layered/KeyframeAnimationPlayer.class")
                for method in (b"getStopTick", b"getCurrentTick"):
                    self.assertIn(method, player)

    def test_synced_snapshot_is_transient_and_client_read_only(self):
        source = (ROOT / "src/main/java/com/craftix/hostile_humans/entity/HumanMobEntityData.java").read_text(encoding="utf-8")
        self.assertIn("EntityDataSerializers.COMPOUND_TAG", source)
        self.assertIn("if (!level().isClientSide) this.entityData.set(DATA_TACZ_RELOAD_VISUAL", source)
        self.assertEqual(source.count("entityData.define(DATA_TACZ_RELOAD_VISUAL"), 1)
        self.assertIn('PROTOCOL_VERSION = "6"', (ROOT / "src/main/java/com/craftix/hostile_humans/network/NetworkHandler.java").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
