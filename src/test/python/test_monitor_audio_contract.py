import json
from source_paths import SourcePath as Path
import unittest
ROOT = Path(__file__).resolve().parents[3]
JAVA = ROOT / "src/main/java/dev/felix/hostilehumans"
def source(name): return (JAVA / (name + ".java")).read_text(encoding="utf-8")
class MonitorAudioContract(unittest.TestCase):
    def test_audio_requires_valid_server_lease(self):
        service = source("HumanMonitorService")
        audience = service.split("static void forEachTarget", 1)[1].split("static boolean active", 1)[0]
        self.assertLess(audience.index("!filter.test(player)"), audience.index("Human human = target(player)"))
        self.assertIn("if (human != null)", audience)
        audio = source("HumanMonitorAudio")
        for guard in ("player.server == server", "player != excluded", "player.level().dimension() == dimension", "Double.isFinite(radius)"):
            self.assertIn(guard, audio)
    def test_native_and_monitor_spheres_do_not_duplicate(self):
        audio = source("HumanMonitorAudio")
        self.assertIn("player.distanceToSqr(x, y, z) < radius * radius", audio)
        self.assertLess(audio.index("human.getEyePosition().distanceToSqr(x, y, z) < squared"), audio.index("alreadyReceived.test(player)"))
        self.assertNotIn("playSound(", audio)
        self.assertNotIn(".broadcast(", audio)
    def test_mixin_only_supplements_supported_sound_packets(self):
        audio = source("HumanMonitorAudio")
        for packet in ("ClientboundSoundPacket", "ClientboundSoundEntityPacket", "ClientboundLevelEventPacket"):
            self.assertIn("packet instanceof " + packet, audio)
        mixin = source("mixin/HumanMonitorSoundBroadcastMixin")
        self.assertIn('at = @At("TAIL")', mixin)
        self.assertIn("m_11241_", mixin)
        self.assertNotIn("cancellable = true", mixin)
        config = json.loads((ROOT/"src/main/resources/hostile_humans_unified.mixins.json").read_text(encoding="utf-8"))
        self.assertIn("HumanMonitorSoundBroadcastMixin", config["mixins"])
    def test_untracked_sources_retain_sound_and_seed(self):
        audio = source("HumanMonitorAudio")
        self.assertIn("bound.getVolume(), bound.getPitch(), bound.getSeed()", audio)
        self.assertIn("player.connection.send(audible)", audio)
    def test_optional_native_sound_adapters_use_shared_audience(self):
        for name in ("ScgunsSounds", "JegSounds"):
            body = source(name)
            self.assertIn("HumanMonitorAudio.supplement", body)
            self.assertIn("sendToNearbyPlayers", body)
            self.assertIn("sendToPlayer(() -> player, message)", body)

    def test_tacz_reuses_real_native_message_and_exact_audience(self):
        body = source("TaczMonitorAudio")
        self.assertIn("HumanMonitorAudio.supplementNative", body)
        self.assertIn("nativeAudience.contains(player)", body)
        self.assertIn("server.isSameThread()", body)
        self.assertIn("level.getEntity(message.getEntityId())", body)
        self.assertIn("NetworkHandler.sendToClientPlayer(message, player)", body)
        self.assertNotIn("new ServerMessageSound", body)
        self.assertNotIn("GunShootEvent", body)
        mixin = source("mixin/TaczMonitorSoundBroadcastMixin")
        self.assertIn('at = @At("RETURN")', mixin)
        self.assertIn('com.tacz.guns.network.message.ServerMessageSound', mixin)
        self.assertNotIn("cancellable = true", mixin)
if __name__ == "__main__": unittest.main()
